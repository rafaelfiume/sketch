package org.fiume.sketch.datastore.http

import cats.MonadThrow
import cats.data.{EitherT, NonEmptyChain}
import cats.effect.{Concurrent, Sync}
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.datastore.algebras.DocumentsStore
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.datastore.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.datastore.http.Model.Incorrect
import org.fiume.sketch.datastore.http.Model.IncorrectOps.*
import org.fiume.sketch.domain.Document
import org.http4s.{HttpRoutes, QueryParamDecoder, _}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.QueryParamDecoderMatcher
import org.http4s.multipart.{Multipart, Part, _}
import org.http4s.server.Router
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import DocumentsRoutes.*

class DocumentsRoutes[F[_]: Async, Txn[_]](store: DocumentsStore[F, Txn]) extends Http4sDsl[F]:
  private val logger = Slf4jLogger.getLogger[F]

  private val prefix = "/"

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "documents" =>
        req.decode { (m: Multipart[F]) =>
          val payload = (m.metadata, m.bytes).parTupled
          for
            value <- payload.value
            res <- value match {
              case Left(details) =>
                logger.info(s"Received incorrect request to upload document: $details") *>
                  BadRequest(Incorrect(details))

              case Right((metadata, bytes)) =>
                logger.info(s"Received request to upload document ${metadata.name}") *>
                  store.commit { store.store(Document[F](metadata, bytes)) } >>
                  Created(metadata)
            }
          yield res
        }

      case GET -> Root / "documents" / "metadata" :? NameQParam(name) =>
        for
          _ <- logger.info(s"Received request to fetch metadata for doc $name")
          result <- store.commit { store.fetchMetadata(name) }
          res <- result match
            case None           => NotFound()
            case Some(metadata) => Ok(metadata)
        yield res

      case GET -> Root / "documents" :? NameQParam(name) =>
        for
          _ <- logger.info(s"Received request to download doc $name")
          result <- store.commit { store.fetchBytes(name) }
          res <- result match
            case None         => NotFound()
            case Some(stream) => Ok(stream)
        yield res

      case DELETE -> Root / "documents" :? NameQParam(name) =>
        for
          _ <- logger.info(s"Received request to delete doc $name")
          _ <- store.commit { store.delete(name) }
          res <- NoContent()
        yield res
    }

  val routes: HttpRoutes[F] = Router(prefix -> httpRoutes)

private [http] object DocumentsRoutes:
  given QueryParamDecoder[Document.Metadata.Name] = QueryParamDecoder.stringQueryParamDecoder.map(Document.Metadata.Name.apply)
  object NameQParam extends QueryParamDecoderMatcher[Document.Metadata.Name]("name")

  extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
    def metadata: EitherT[F, NonEmptyChain[Incorrect.Detail], Document.Metadata] = EitherT
      .fromEither {
        m.parts.find { _.name == Some("metadata") }.orMissing("metadata")
      }
      .flatMap { json =>
        json
          .as[Document.Metadata]
          .attemptT
          .leftMap { _.getMessage.malformed }
      }

    def bytes: EitherT[F, NonEmptyChain[Incorrect.Detail], Stream[F, Byte]] = EitherT
      .fromEither {
        m.parts.find { _.name == Some("document") }.orMissing("bytes").map(_.body)
      }

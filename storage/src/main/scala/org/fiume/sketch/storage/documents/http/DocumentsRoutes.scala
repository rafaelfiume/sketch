package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.{EitherT, NonEmptyChain}
import cats.effect.{Concurrent, Sync}
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.storage.documents.Model.{Document, Metadata}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.JsonCodecs.given
import org.fiume.sketch.storage.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.storage.http.Model.Incorrect
import org.fiume.sketch.storage.http.Model.IncorrectOps.*
import org.http4s.{HttpRoutes, QueryParamDecoder, *}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.QueryParamDecoderMatcher
import org.http4s.multipart.{Multipart, Part, *}
import org.http4s.server.Router
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import DocumentsRoutes.*

class DocumentsRoutes[F[_]: Async, Txn[_]](store: DocumentsStore[F, Txn]) extends Http4sDsl[F]:
  private val logger = Slf4jLogger.getLogger[F]

  private val prefix = "/"

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      /*
       * TODO Fix warning:
       *
       * > [io-compute-9] INFO org.fiume.sketch.storage.http.DocumentsRoutes - Received request to upload document Name(altamura.jpg)
       * > [WARNING] Your app's responsiveness to a new asynchronous event (such as a
       * > new connection, an upstream response, or a timer) was in excess of 100 milliseconds.
       * > Your CPU is probably starving. Consider increasing the granularity
       * > of your delays or adding more cedes. This may also be a sign that you are
       * > unintentionally running blocking I/O operations (such as File or InetAddress)
       * > without the blocking combinator.
       */
      case req @ POST -> Root / "documents" =>
        req.decode { (m: Multipart[F]) =>
          val payload = (m.metadata, m.bytes).parTupled // warning: errors won't accumulate by default: see validation tests
          for
            value <- payload.value
            res <- value match
              case Left(details) =>
                logger.info(s"Bad request to upload document: $details") *>
                  BadRequest(Incorrect(details))

              case Right((metadata, bytes)) =>
                for
                  _ <- logger.info(s"Uploading document ${metadata.name}")
                  uuid <- store.commit { store.store(metadata, bytes) }
                  created <- Created(uuid)
                yield created
          yield res
        }

      case GET -> Root / "documents" / UUIDVar(uuid) / "metadata" =>
        for
          _ <- logger.info(s"Fetching document metadata of $uuid")
          result <- store.commit { store.fetchMetadata(uuid) }
          res <- result match
            case None           => NotFound()
            case Some(metadata) => Ok(metadata)
        yield res

      case GET -> Root / "documents" / UUIDVar(uuid) / "content" =>
        for
          _ <- logger.info(s"fetching content of document $uuid")
          result <- store.commit { store.fetchBytes(uuid) }
          res <- result match
            case None         => NotFound()
            case Some(stream) => Ok(stream)
        yield res

      case DELETE -> Root / "documents" / UUIDVar(uuid) =>
        for
          _ <- logger.info(s"Deleting document $uuid")
          metadata <- store.commit { store.fetchMetadata(uuid) }
          res <- metadata match
            case None => NotFound()
            case Some(_) =>
              store.commit { store.delete(uuid) } >>
                NoContent()
        yield res
    }

  val routes: HttpRoutes[F] = Router(prefix -> httpRoutes)

private[http] object DocumentsRoutes:
  import java.util.UUID
  object UUIDVar:
    def unapply(str: String): Option[UUID] =
      try Some(UUID.fromString(str))
      catch case _: IllegalArgumentException => None

  extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
    def metadata: EitherT[F, NonEmptyChain[Incorrect.Detail], Metadata] = EitherT
      .fromEither {
        m.parts.find { _.name == Some("metadata") }.orMissing("metadata")
      }
      .flatMap { json =>
        json
          .as[Metadata]
          .attemptT
          .leftMap { _.getMessage.malformed }
      }

    def bytes: EitherT[F, NonEmptyChain[Incorrect.Detail], Stream[F, Byte]] = EitherT
      .fromEither {
        m.parts.find { _.name == Some("bytes") }.orMissing("bytes").map(_.body)
      }

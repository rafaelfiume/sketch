package org.fiume.sketch.datastore.http

import cats.MonadThrow
import cats.effect.Concurrent
import cats.implicits.*
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.domain.Document
import org.http4s.{HttpRoutes, _}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.multipart.{Multipart, Part, _}
import org.http4s.server.Router

class DocumentsRoutes[F[_]: Concurrent] extends Http4sDsl[F]:

  private val prefix = "/"

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] { case req @ POST -> Root / "documents" / "upload" =>
      req.decode { (m: Multipart[F]) =>
        // TODO Sad path
        val metadata = m.parts
          .find(_.name == Some("metadata"))
          .getOrElse(throw new RuntimeException("sad path coming soon"))
          .as[Document.Metadata]
        val document = m.parts
          .find(_.name == Some("document"))
          .fold(ifEmpty = fs2.Stream.empty)(part => part.body)
        Created(metadata) // TODO Do we want to send the created resource back to the client?
      }
    }

  val routes: HttpRoutes[F] = Router(prefix -> httpRoutes)

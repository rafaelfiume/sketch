package org.fiume.sketch.shared.app.http4s.middlewares

import cats.Invariant
import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.{ErrorDetails, ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.PayloadCodecs.ErrorInfoCodecs.given
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl

import scala.util.control.NoStackTrace

sealed abstract case class SemanticInputError(message: ErrorMessage, details: ErrorDetails) extends NoStackTrace

object SemanticInputError:
  val message = ErrorMessage("""
      |Please, check the request body conforms to the established semantic rules. Tips:
      | * Does the request conforms to the API contract?
      | * Does it include invalid data, for instance a password that is too short or a username with invalid characters?
      | * Does the entity exceed a certain size, for example a request to upload a document that is too large?
    """.stripMargin)

  def makeFrom(details: ErrorDetails) = new SemanticInputError(message, details) {}

  extension (error: SemanticInputError) def toErrorInfo = ErrorInfo.withDetails(error.message, error.details)

// TODO make this middleware more generic, so it can catch any kind of error and return ErrorInfo
object ErrorInfoMiddleware:
  def apply[F[_]: Async](routes: HttpRoutes[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    Kleisli { req =>
      routes
        .run(req)
        .semiflatMap { response =>
          // there errors are swallowed by http4s, so we need to inspect the response
          response.status match
            case Status.UnprocessableEntity =>
              response
                .as[String]
                .flatMap { body =>
                  Status.UnprocessableEntity(
                    SemanticInputError.makeFrom(ErrorDetails(Map("input.semantic.error" -> body))).toErrorInfo
                  )
                }
            case _ => response.pure[F]
        }
        .handleError { case SemanticInputError(message, details) =>
          Response[F](Status.UnprocessableEntity).withEntity(SemanticInputError.makeFrom(details).toErrorInfo)
        }
    }

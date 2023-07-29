package org.fiume.sketch.shared.app.http4s.middlewares

import cats.Invariant
import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.{ErrorCode, ErrorDetails, ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.PayloadCodecs.ErrorInfoCodecs.given
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl

import scala.util.control.NoStackTrace

case class InvalidInputError(code: ErrorCode, message: ErrorMessage, details: ErrorDetails) extends NoStackTrace

case class MalformedInputError(details: ErrorDetails) extends NoStackTrace

// TODO make this middleware more generic, so it can catch any kind of error and return ErrorInfo
object ErrorInfoMiddleware:
  def apply[F[_]: Async](service: HttpRoutes[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    Kleisli { req =>
      service
        .run(req)
        .semiflatMap { response =>
          // there errors are swallowed by http4s, so we need to inspect the response
          response.status match
            case Status.UnprocessableEntity =>
              response
                .as[String]
                .flatMap { body => Status.UnprocessableEntity(malformedInputErrorInfoFromString(body)) }
                .recoverWith { case e: Throwable =>
                  Status.UnprocessableEntity(malformedInputErrorInfoFromString(e.getMessage()))
                }
            case _ => response.pure[F]
        }
        .handleError {
          // these are raised by the routes when validating requests
          case MalformedInputError(details) =>
            Response[F](Status.UnprocessableEntity).withEntity(malformedInputErrorInfo(details))

          case InvalidInputError(code, message, details) =>
            Response[F](Status.BadRequest).withEntity(ErrorInfo.withDetails(code, message, details))
        }
    }

  private def malformedInputErrorInfoFromString(details: String) =
    malformedInputErrorInfo(ErrorDetails(Map("malformed.client.input" -> details)))

  private def malformedInputErrorInfo(details: ErrorDetails) =
    ErrorInfo.withDetails(
      ErrorCode.InvalidClientInput,
      ErrorMessage("Please, check the client request conforms to the API contract."),
      details
    )

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

case class SyntaxInputError(details: ErrorDetails) extends NoStackTrace
case class SemanticInputError(code: ErrorCode, message: ErrorMessage, details: ErrorDetails) extends NoStackTrace

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
                .flatMap { body => Status.UnprocessableEntity(semanticInputErrorInfoFromString(body)) }
                .recoverWith { case e: Throwable =>
                  Status.BadRequest(syntaxInputErrorInfoFromString(e.getMessage()))
                }
            case _ => response.pure[F]
        }
        .handleError {
          // these are raised by the routes when validating requests
          case SyntaxInputError(details) =>
            Response[F](Status.BadRequest).withEntity(syntaxInputErrorInfo(details))

          case SemanticInputError(code, message, details) =>
            Response[F](Status.UnprocessableEntity).withEntity(ErrorInfo.withDetails(code, message, details))
        }
    }

  private def semanticInputErrorInfoFromString(details: String) =
    semanticInputErrorInfo(ErrorDetails(Map("input.semantic.error" -> details)))

  private def semanticInputErrorInfo(details: ErrorDetails) =
    ErrorInfo.withDetails(
      ErrorCode.InvalidClientInput,
      ErrorMessage("????"),
      details
    )

  private def syntaxInputErrorInfoFromString(details: String) =
    syntaxInputErrorInfo(ErrorDetails(Map("input.syntax.error" -> details)))

  private def syntaxInputErrorInfo(details: ErrorDetails) =
    ErrorInfo.withDetails(
      ErrorCode.InvalidClientInput,
      ErrorMessage("Please, check the client request conforms to the API contract."),
      details
    )

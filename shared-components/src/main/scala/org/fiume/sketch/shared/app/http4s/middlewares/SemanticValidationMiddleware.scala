package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl

import scala.util.control.NoStackTrace

sealed abstract case class SemanticInputError(code: ErrorCode, message: ErrorMessage, details: ErrorDetails) extends NoStackTrace

object SemanticInputError:
  def make(code: ErrorCode, message: ErrorMessage, details: ErrorDetails) = new SemanticInputError(code, message, details) {}

  def make(code: ErrorCode, details: ErrorDetails) =
    new SemanticInputError(code, ErrorMessage("Input data doesn't meet the requirements"), details) {}

object SemanticValidationMiddleware:
  def apply[F[_]: Async](routes: HttpRoutes[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    Kleisli { req =>
      routes
        .run(req)
        .semiflatMap { response =>
          // http4s swallows errors, so we need to transform UnprocessableEntity responses
          response.status match
            case UnprocessableEntity =>
              response
                .as[String]
                .flatMap { body =>
                  UnprocessableEntity(
                    ErrorInfo.make(
                      ErrorCode("9000"),
                      ErrorMessage("The request body could not be processed"),
                      ErrorDetails("input.semantic.error" -> body)
                    )
                  )
                }
            case _ => response.pure[F]
        }
        .handleError {
          // this is raised by validation functions in the app's routes
          case SemanticInputError(code, message, details) =>
            Response[F](UnprocessableEntity)
              .withEntity(ErrorInfo.make(code, message, details))
        }
    }

package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl

// TODO make this middleware more generic, so it can catch any kind of error and return ErrorInfo
object ErrorInfoMiddleware:
  def apply[F[_]: Async](service: HttpRoutes[F]): HttpRoutes[F] =
    val dsl = Http4sDsl[F]
    import dsl.*

    Kleisli { req =>
      service.run(req).semiflatMap { response =>
        response.status match
          case Status.UnprocessableEntity =>
            response
              .as[String]
              .flatMap { body => Status.UnprocessableEntity(inputErrorInfo(body)) }
              .recoverWith { case e: Throwable => Status.UnprocessableEntity(inputErrorInfo(e.getMessage())) }
          case _ => response.pure[F]
      }
    }

  private def inputErrorInfo(error: String) =
    ErrorInfo.withDetails(
      ErrorCode.InvalidClientInput,
      ErrorMessage("Please, check the client request conforms to the API contract."),
      ErrorDetails(Map("invalid.client.input" -> error))
    )

package org.fiume.sketch.auth.http.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth.Authenticator
import org.fiume.sketch.shared.auth.domain.{Jwt, User}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.http4s.{AuthScheme, AuthedRoutes, Challenge, Request, Response, Status}
import org.http4s.Credentials.Token
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.{`WWW-Authenticate`, Authorization}
import org.http4s.server.AuthMiddleware

object Auth0Middleware:
  def apply[F[_]: Sync](authenticator: Authenticator[F]): AuthMiddleware[F, User] =

    def verify: Kleisli[F, Request[F], Either[String, User]] = Kleisli { req =>
      Sync[F].delay {
        for
          jwt <- req.headers
            .get[Authorization]
            .collect { case Authorization(Token(AuthScheme.Bearer, value)) =>
              Jwt.makeUnsafeFromString(value)
            }
            .toRight("Couldn't find an Authorization header with a Bearer token")
          user <- authenticator.verify(jwt).leftMap(_.toString)
        yield user
      }
    }

    val onFailure: AuthedRoutes[String, F] = Kleisli { cx =>
      OptionT.pure(
        Response[F](Status.Unauthorized)
          .withHeaders(`WWW-Authenticate`(Challenge("Bearer", s"${cx.req.uri.path}")))
          .withEntity(
            ErrorInfo.make(ErrorCode("1011"), ErrorMessage("Invalid credentials"), ErrorDetails("invalid.jwt" -> cx.context))
          )
      )
    }

    AuthMiddleware(verify, onFailure)

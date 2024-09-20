package org.fiume.sketch.auth0.http.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.domain.{JwtToken, User}
import org.http4s.{AuthedRoutes, Challenge, Request, Response, Status}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.{`WWW-Authenticate`, Authorization}
import org.http4s.server.AuthMiddleware
import org.http4s.syntax.header.*

object Auth0Middleware:
  def apply[F[_]: Sync](authenticator: Authenticator[F]): AuthMiddleware[F, User] =

    def verify: Kleisli[F, Request[F], Either[String, User]] = Kleisli { req =>
      Sync[F].delay {
        for
          header <- req.headers.get[Authorization].toRight("Couldn't find an Authorization header")
          token = JwtToken.makeUnsafeFromString(header.value.stripPrefix("Bearer "))
          user <- authenticator.verify(token).leftMap(_.toString)
        yield user
      }
    }

    val onFailure: AuthedRoutes[String, F] = Kleisli { cx =>
      OptionT.pure(
        Response[F](Status.Unauthorized)
          .withHeaders(`WWW-Authenticate`(Challenge("Bearer", s"${cx.req.uri.path}")))
          .withEntity(
            ErrorInfo.make(ErrorMessage("Invalid credentials"), ErrorDetails("invalid.jwt" -> cx.context))
          )
      )
    }

    AuthMiddleware(verify, onFailure)

package org.fiume.sketch.auth0.http.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth0.{Authenticator, JwtToken}
import org.fiume.sketch.shared.app.troubleshooting.{ErrorDetails, ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.json.ErrorInfoCodecs.given
import org.fiume.sketch.shared.auth0.User
import org.http4s.{AuthedRoutes, Request}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.syntax.header.*

object Auth0Middleware:
  def apply[F[_]](authenticator: Authenticator[F])(using F: Sync[F]): AuthMiddleware[F, User] =
    val dsl = Http4sDsl[F]
    import dsl.*

    val onFailure: AuthedRoutes[String, F] = Kleisli { req =>
      OptionT.liftF(
        Forbidden(
          ErrorInfo.withDetails(
            ErrorMessage("Invalid credentials"),
            ErrorDetails.single("invalid.jwt" -> req.context)
          )
        )
      )
    }

    def verify: Kleisli[F, Request[F], Either[String, User]] = Kleisli { req =>
      F.delay {
        for
          header <- req.headers.get[Authorization].toRight("Couldn't find an Authorization header")
          token = JwtToken.notValidatedFromString(header.value.stripPrefix("Bearer "))
          user <- authenticator.verify(token).leftMap(_.toString)
        yield user
      }
    }

    AuthMiddleware(verify, onFailure)

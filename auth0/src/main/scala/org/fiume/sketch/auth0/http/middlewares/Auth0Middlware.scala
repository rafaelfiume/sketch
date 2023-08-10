package org.fiume.sketch.auth0.http.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth0.{Authenticator, JwtToken}
import org.fiume.sketch.shared.auth0.User
import org.http4s.{AuthedRoutes, Request}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.syntax.header.*

object Auth0Middleware:
  def apply[F[_]](authenticator: Authenticator[F])(using F: Sync[F]): AuthMiddleware[F, User] =
    val dsl = Http4sDsl[F]
    import dsl.*

    // TODO AuthenticationError
    val onFailure: AuthedRoutes[String, F] = Kleisli { req =>
      OptionT.liftF(F.delay(println(s"Auth0Middleware onFailure: ${req.context}"))) *>
        OptionT.liftF(Forbidden(req.context))
    }

    // TODO AuthenticationError
    def verify: Kleisli[F, Request[F], Either[String, User]] = Kleisli { req =>
      F.delay {
        for
          header <- req.headers.get[Authorization].toRight("Couldn't find an Authorization header")
          token = JwtToken.notValidatedFromString(header.value.stripPrefix("Bearer "))
          user <- authenticator.verify(token).leftMap(_.toString) // for now
        yield user
      }
    }

    AuthMiddleware(verify, onFailure)

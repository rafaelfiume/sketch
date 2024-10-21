package org.fiume.sketch.shared.auth.testkit

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.shared.auth.domain.User
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.{AuthedRoutes, Challenge, Request, Response, Status}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.AuthMiddleware

trait AuthMiddlewareContext:

  def makeAuthMiddleware(): AuthMiddleware[IO, User] =
    def aUser(): User = users.sample.someOrFail
    makeAuthMiddleware(aUser())

  def makeAuthMiddleware(authenticated: User): AuthMiddleware[IO, User] =
    def verify: Kleisli[IO, Request[IO], Either[String, User]] = Kleisli.liftF(authenticated.asRight[String].pure[IO])

    val onFailure: AuthedRoutes[String, IO] = Kleisli { cx =>
      OptionT.pure(
        Response[IO](Status.Unauthorized)
          .withHeaders(`WWW-Authenticate`(Challenge("Bearer", s"${cx.req.uri.path}")))
          .withEntity(
            ErrorInfo.make(ErrorCode("1011"), ErrorMessage("Invalid credentials"), ErrorDetails("invalid.jwt" -> cx.context))
          )
      )
    }
    AuthMiddleware(verify, onFailure)

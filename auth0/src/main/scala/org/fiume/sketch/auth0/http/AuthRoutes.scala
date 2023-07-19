package org.fiume.sketch.auth0.http

import cats.effect.kernel.Async
import cats.implicits.*
import io.circe.generic.auto.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequest, LoginResponse}
import org.fiume.sketch.auth0.http.JsonCodecs.given
import org.fiume.sketch.shared.auth0.Model.Username
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.http4s.{Challenge, HttpRoutes, Response}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class AuthRoutes[F[_]: Async](authenticator: Authenticator[F]) extends Http4sDsl[F]:
  private val logger = Slf4jLogger.getLogger[F]

  val routes: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "login" =>
    req.decode { (loginRequest: LoginRequest) =>
      logger.info(s"Attempt to authenticate username ${loginRequest.username}") *>
        authenticator.authenticate(loginRequest.username, loginRequest.password).flatMap {
          case Right(token) =>
            logger.info(s"Successful login attempt for username ${loginRequest.username}") *>
              Ok(LoginResponse(token.value))
          case Left(failure) =>
            logger.info(s"Failed login attempt for username ${loginRequest.username}") *>
              Ok("Failed login attempt") // TODO Make it a json payload
        }
    }
  }

object AuthRoutes:
  object Model:
    case class LoginRequest(username: Username, password: PlainPassword)
    case class LoginResponse(token: String)

/*
To be returned by the middleware when authentication fails.
Unauthorized(`WWW-Authenticate`(Challenge(scheme = "Bearer", realm = "sketch")))
*/
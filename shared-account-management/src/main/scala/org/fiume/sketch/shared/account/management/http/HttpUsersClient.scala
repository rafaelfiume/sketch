package org.fiume.sketch.shared.account.management.http

import cats.effect.Async
import cats.implicits.*
import com.comcast.ip4s.{Host, Port}
import org.fiume.sketch.shared.auth.domain.{ActivateAccountError, Jwt, JwtError, SoftDeleteAccountError, UserId}
import org.fiume.sketch.shared.auth.domain.SoftDeleteAccountError.AccountAlreadyPendingDeletion
import org.fiume.sketch.shared.auth.http.model.Users.ScheduledForPermanentDeletionResponse
import org.fiume.sketch.shared.auth.http.model.Users.json.given
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.authorisation.AuthorisationError
import org.fiume.sketch.shared.authorisation.AuthorisationError.UnauthorisedError
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.Status.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.*
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object HttpUsersClient:
  case class Config(private val host: Host, private val port: Port):
    val baseUri: Uri = Uri.unsafeFromString(s"http://$host:$port")

  def make[F[_]: Async](config: Config, client: Client[F]): HttpUsersClient[F] =
    new HttpUsersClient(config.baseUri, client)

class HttpUsersClient[F[_]: Async] private (baseUri: Uri, client: Client[F]):
  given Logger[F] = Slf4jLogger.getLogger[F]

  def markAccountForDeletion(
    id: UserId,
    jwt: Jwt
  ): F[Either[JwtError | AuthorisationError | SoftDeleteAccountError, ScheduledAccountDeletion]] =
    for
      authHeader <- Async[F].delay { Authorization.parse(s"Bearer ${jwt.value}") }
      request = Request[F](DELETE, baseUri / "users" / id.value).withHeaders(authHeader)
      result <- client.run(request).use {
        case Ok(resp) =>
          resp
            .as[ScheduledForPermanentDeletionResponse]
            .map(p => ScheduledAccountDeletion(p.jobId, p.userId, p.permanentDeletionAt).asRight)
        case Conflict(_) => AccountAlreadyPendingDeletion.asLeft[ScheduledAccountDeletion].pure[F]
        case NotFound(_) => SoftDeleteAccountError.AccountNotFound.asLeft.pure[F]
        case Unauthorized(resp) =>
          resp.as[ErrorInfo].flatTap { error => warn"Unauthorised to mark account for deletion: $error" }.map { error =>
            error.code.value match
              case "1011" => JwtError.JwtUnknownError(error.message.value).asLeft
          }
        case Forbidden(_) => UnauthorisedError.asLeft.pure[F]
      }
    yield result

  def restoreAccount(id: UserId, jwt: Jwt): F[Either[JwtError | AuthorisationError | ActivateAccountError, Unit]] =
    for
      authHeader <- Async[F].delay { Authorization.parse(s"Bearer ${jwt.value}") }
      request = Request[F](POST, baseUri / "users" / id.value / "restore").withHeaders(authHeader)
      result <- client.run(request).use {
        case NoContent(_) => ().asRight[JwtError | AuthorisationError | ActivateAccountError].pure[F]
        case Conflict(_)  => ActivateAccountError.AccountAlreadyActive.asLeft.pure[F]
        case NotFound(_)  => ActivateAccountError.AccountNotFound.asLeft.pure[F]
        case Unauthorized(resp) =>
          resp.as[ErrorInfo].flatTap { error => warn"Unauthorised to restore account: $error" }.map { error =>
            error.code.value match
              case "1011" => JwtError.JwtUnknownError(error.message.value).asLeft
          }
        case Forbidden(_) => UnauthorisedError.asLeft.pure[F]
      }
    yield result

package org.fiume.sketch.shared.account.management.http

import cats.effect.Async
import cats.implicits.*
import com.comcast.ip4s.{Host, Port}
import org.fiume.sketch.shared.auth.{Jwt, UserId}
import org.fiume.sketch.shared.auth.accounts.{AccountDeletionEvent, ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.accounts.SoftDeleteAccountError.AccountAlreadyPendingDeletion
import org.fiume.sketch.shared.auth.http.ClientAuthorisationError
import org.fiume.sketch.shared.auth.http.model.Users.ScheduledForPermanentDeletionResponse
import org.fiume.sketch.shared.auth.http.model.Users.json.given
import org.fiume.sketch.shared.authorisation.AccessDenied
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
  ): F[Either[ClientAuthorisationError | AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
    for
      authHeader <- Async[F].delay { Authorization.parse(s"Bearer ${jwt.value}") }
      request = Request[F](DELETE, baseUri / "users" / id.value).withHeaders(authHeader)
      result <- client.run(request).use {
        case Ok(resp) =>
          resp
            .as[ScheduledForPermanentDeletionResponse]
            .map(p => AccountDeletionEvent.Scheduled(p.eventId, p.userId, p.permanentDeletionAt).asRight)
        case Conflict(_)        => AccountAlreadyPendingDeletion.asLeft[AccountDeletionEvent.Scheduled].pure[F]
        case NotFound(_)        => SoftDeleteAccountError.AccountNotFound.asLeft.pure[F]
        case Unauthorized(resp) =>
          /* There is a chance an Api gateway will take care of verifying an issued token,
           * so I'm relaxing the error handling to merely pass a String payload as param to ClientAuthenticationError.
           * That's to minimise potential changes to this logic.
           */
          resp.bodyText.compile.string
            .flatTap { error => warn"Unauthorised to restore account: $error" }
            .map(_ => ClientAuthorisationError("Invalid credentials").asLeft)
        case Forbidden(_) => AccessDenied.asLeft.pure[F]
      }
    yield result

  def restoreAccount(
    id: UserId,
    jwt: Jwt
  ): F[Either[ClientAuthorisationError | AccessDenied.type | ActivateAccountError, Unit]] =
    for
      authHeader <- Async[F].delay { Authorization.parse(s"Bearer ${jwt.value}") }
      request = Request[F](POST, baseUri / "users" / id.value / "restore").withHeaders(authHeader)
      result <- client.run(request).use {
        case NoContent(_) => ().asRight[ClientAuthorisationError | AccessDenied.type | ActivateAccountError].pure[F]
        case Conflict(_)  => ActivateAccountError.AccountAlreadyActive.asLeft.pure[F]
        case NotFound(_)  => ActivateAccountError.AccountNotFound.asLeft.pure[F]
        case Unauthorized(resp) =>
          resp.bodyText.compile.string
            .flatTap { error => warn"Unauthorised to restore account: $error" }
            .as(
              ClientAuthorisationError("Invalid credentials").asLeft
            )
        case Forbidden(_) => AccessDenied.asLeft.pure[F]
      }
    yield result

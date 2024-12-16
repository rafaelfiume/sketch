package org.fiume.sketch.auth.scripts

import cats.data.Validated
import cats.effect.{Clock, ExitCode, IO, IOApp}
import cats.effect.std.Console
import cats.implicits.*
import doobie.ConnectionIO
import org.fiume.sketch.auth.accounts.UsersManager
import org.fiume.sketch.auth.scripts.UsersScript.Args
import org.fiume.sketch.shared.auth.{User, UserId}
import org.fiume.sketch.shared.auth.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.accounts.AccountConfig
import org.fiume.sketch.shared.authorisation.GlobalRole
import org.fiume.sketch.shared.common.troubleshooting.{ErrorInfo, InvariantError}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.ErrorDetails
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*
import org.fiume.sketch.shared.common.troubleshooting.syntax.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.common.typeclasses.AsString
import org.fiume.sketch.storage.auth.postgres.PostgresUsersStore
import org.fiume.sketch.storage.authorisation.postgres.PostgresAccessControl
import org.fiume.sketch.storage.postgres.{DatabaseConfig, DbTransactor}

import scala.concurrent.duration.*
import scala.util.Try

object UsersScript extends IOApp:

  private val scriptErrorCode = ExitCode(55)

  def run(cliArgs: List[String]): IO[ExitCode] =
    Args.make(cliArgs) match
      case Right(args) =>
        makeScript().flatMap { _.createUserAccount(args).as(ExitCode.Success) }
      case Left(invalidInput) =>
        Console[IO].error(invalidInput.asString()) *>
          IO.pure(scriptErrorCode)

  def makeScript(): IO[UsersScript] =
    // TODO Load for the environment
    val accountConfig = AccountConfig(
      delayUntilPermanentDeletion = 90.days,
      permanentDeletionJobInterval = 15.seconds
    )
    DatabaseConfig.envs[IO](dbPoolThreads = 2).load[IO].map(UsersScript(_, accountConfig, Clock[IO]))

  /*
   * An experimental implementation of `AsString` for `ErrorInfo`
   * which doesn't seem to work particularly well when, for example, sent over the network via a json field.
   */
  given AsString[ErrorInfo] with // yolo
    extension (error: ErrorInfo)
      override def asString(): String =
        val basicMessage = s"""
          |code: ${error.code.value}
          |message: ${error.message.value}
          |""".stripMargin
        error.details.fold(
          ifEmpty = basicMessage
        ) { details =>
          s"""|
              |${basicMessage}:
              |${details.tips.mkString(" * ", "\n * ", "")}
              |""".stripMargin
        }

  object Args:
    def make(args: List[String]): Either[ErrorInfo, Args] =
      args match
        case username :: password :: isSuperuser :: isAdmin :: Nil =>
          (
            Username.validated(username).leftMap(_.asDetails),
            PlainPassword.validated(password).leftMap(_.asDetails),
            Args.validatedIsGlobalRole(isSuperuser, GlobalRole.Superuser).leftMap(_.asDetails),
            Args.validatedIsGlobalRole(isAdmin, GlobalRole.Admin).leftMap(_.asDetails)
          )
            .parFlatMapN((user, password, isSuperuser, isAdmin) => Args.validated(user, password, isSuperuser, isAdmin))
            .leftMap(details => ErrorInfo.make("1100".code, "Invalid parameters".message, details))
        case unknown =>
          ErrorInfo.make("1100".code, s"Invalid arguments: '$unknown'".message).asLeft[Args]

    private def validatedIsGlobalRole(boolean: String, role: GlobalRole) =
      Validated.fromTry(Try(boolean.toBoolean)).leftMap(_ => InvalidGlobalRoleArg(role)).toEither

    private def validated(
      username: Username,
      password: PlainPassword,
      isSuperuser: Boolean,
      isAdmin: Boolean
    ): Either[ErrorDetails, Args] =
      if isSuperuser && isAdmin then InvalidGlobalRoleSelectionArg.asDetails.asLeft
      else
        val globalRole =
          if isSuperuser then GlobalRole.Superuser.some
          else if isAdmin then GlobalRole.Superuser.some
          else none
        Args(username, password, globalRole).asRight

    private case object InvalidGlobalRoleSelectionArg extends InvariantError:
      override val key: String = "invalid.global.role.selection.arg"
      override val detail: String = "select at most one global role"

    private case class InvalidGlobalRoleArg(role: GlobalRole) extends InvariantError:
      override val key: String = s"invalid.${role.toString().toLowerCase()}.arg"
      override val detail: String = s"'is$role' must be either 'true' or 'false'"

  case class Args(username: Username, password: PlainPassword, globalRole: Option[GlobalRole])

class UsersScript private (dbConfig: DatabaseConfig, accountConfig: AccountConfig, clock: Clock[IO]):
  def createUserAccount(args: Args): IO[UserId] =
    DbTransactor
      .make[IO](dbConfig)
      .flatMap { transactor =>
        (PostgresUsersStore.make[IO](transactor, clock), PostgresAccessControl.make[IO](transactor)).tupled
      }
      .use { case (usersStore, accessControl) =>
        UsersManager
          .make[IO, ConnectionIO](usersStore, accessControl, accountConfig.delayUntilPermanentDeletion)
          .createAccount(args.username, args.password, args.globalRole)
      }

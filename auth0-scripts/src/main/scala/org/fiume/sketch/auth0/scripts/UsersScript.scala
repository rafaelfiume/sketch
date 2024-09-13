package org.fiume.sketch.auth0.scripts

import cats.data.{EitherNec, Validated}
import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.std.Console
import cats.implicits.*
import doobie.ConnectionIO
import org.fiume.sketch.auth0.UsersManager
import org.fiume.sketch.auth0.scripts.UsersScript.Args
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, InvariantError}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorMessage
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.storage.DatabaseConfig
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore
import org.fiume.sketch.storage.authorisation.postgres.PostgresAccessControl
import org.fiume.sketch.storage.postgres.DbTransactor

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
    DatabaseConfig.envs[IO](dbPoolThreads = 2).load[IO].map(UsersScript(_))

  object Args:
    def make(args: List[String]): Either[ErrorInfo, Args] =
      args match
        case username :: password :: isSuperuser :: Nil =>
          (
            Username.validated(username).leftMap(_.asDetails),
            PlainPassword.validated(password).leftMap(_.asDetails),
            Args.validatedIsSuperuser(isSuperuser).leftMap(_.asDetails)
          )
            .parMapN((user, password, isSuperuser) => Args(user, password, isSuperuser))
            .leftMap(details => ErrorInfo.make(ErrorMessage("Invalid parameters"), details))
        case unknown =>
          ErrorInfo.make(ErrorMessage(s"Invalid arguments: '$unknown'")).asLeft[Args]

    private case object InvalidSuperuserArg extends InvariantError:
      override val uniqueCode: String = "invalid.superuser.arg"
      override val message: String = "'isSuperuser' must be either 'true' or 'false'"

    private def validatedIsSuperuser(isSuperuser: String): EitherNec[InvalidSuperuserArg.type, Boolean] = Validated
      .condNec(isSuperuser == "true" || isSuperuser == "false", isSuperuser.toBoolean, InvalidSuperuserArg)
      .toEither

  case class Args(username: Username, password: PlainPassword, isSuperuser: Boolean)

class UsersScript private (private val config: DatabaseConfig):
  def createUserAccount(args: Args): IO[Unit] =
    DbTransactor
      .make[IO](config)
      .flatMap { transactor =>
        (PostgresUsersStore.make[IO](transactor), PostgresAccessControl.make[IO](transactor)).tupled
      }
      .use { case (usersStore, accessControl) =>
        for
          usersManager <- UsersManager.make[IO, ConnectionIO](usersStore, accessControl)
          _ <- usersManager.createAccount(args.username, args.password, args.isSuperuser)
        yield ()
      }

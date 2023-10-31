package org.fiume.sketch.auth0.scripts

import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.std.Console
import cats.implicits.*
import doobie.ConnectionIO
import org.fiume.sketch.auth0.UsersManager
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.app.typeclasses.ToSemanticStringSyntax.*
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.storage.DatabaseConfig
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore
import org.fiume.sketch.storage.postgres.DbTransactor

object UsersScript extends IOApp:

  private case class Args(username: Username, pass: PlainPassword)
  private val scriptErrorCode = ExitCode(55)

  def run(args: List[String]): IO[ExitCode] =
    extract(args) match
      case Right(Args(username, password)) =>
        makeScript().flatMap {
          _.registreUser(username, password).as(ExitCode.Success)
        }
      case Left(invalidInput) =>
        Console[IO].error(invalidInput.asSemanticString()) *>
          IO.pure(scriptErrorCode)

  def makeScript(): IO[UsersScript] =
    DatabaseConfig
      .envs[IO](dbPoolThreads = 10)
      .load[IO]
      .map(UsersScript(_))

  private def extract(args: List[String]): Either[ErrorInfo, Args] =
    args match
      case username :: pass :: Nil =>
        (
          Username.validated(username).leftMap(_.asDetails),
          PlainPassword.validated(pass).leftMap(_.asDetails)
        )
          .parMapN((user, pass) => Args(user, pass))
          .leftMap(details => ErrorInfo.withDetails(ErrorMessage("Invalid parameters"), details))
      case unknown =>
        ErrorInfo.short(ErrorMessage(s"Invalid parameters: expected `username` and `password`; got $unknown")).asLeft[Args]

class UsersScript private (private val config: DatabaseConfig):
  def registreUser(username: Username, password: PlainPassword): IO[Unit] =
    DbTransactor.make[IO](config).flatMap(PostgresUsersStore.make[IO]).use { store =>
      for
        usersManager <- UsersManager.make[IO, ConnectionIO](store)
        _ <- usersManager.registreUser(username, password)
      yield ()
    }

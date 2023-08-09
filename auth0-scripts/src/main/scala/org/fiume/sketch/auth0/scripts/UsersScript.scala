package org.fiume.sketch.auth0.scripts

import cats.effect.{ExitCode, IO, IOApp}
import ciris.Secret
import doobie.ConnectionIO
import org.fiume.sketch.auth0.UsersManager
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.storage.DatabaseConfig
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore
import org.fiume.sketch.storage.postgres.DbTransactor
import org.http4s.Uri

/*
 * Script to registre a user.
 *
 * It is currently pointing to the local (dockerised) database.
 */
object UsersScript extends IOApp:

  private val dbConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    uri = Uri.unsafeFromString("jdbc:postgresql://localhost:5432/sketch"),
    user = "sketch.dev",
    password = Secret("sketch.pw"),
    dbPoolThreads = 10
  )

  def run(args: List[String]): IO[ExitCode] =
    val username = Username.validated("abacaxinaofazxixi").getOrElse(throw new Exception("Invalid username"))
    val password = PlainPassword.validated("@b3aTeSesamo").getOrElse(throw new Exception("Invalid password"))
    doRegistreUser(username, password).as(ExitCode.Success)

  def doRegistreUser(username: Username, password: PlainPassword): IO[Unit] =
    DbTransactor.make[IO](dbConfig).flatMap(PostgresUsersStore.make[IO]).use { store =>
      for
        usersManager <- UsersManager.make[IO, ConnectionIO](store)
        _ <- usersManager.registreUser(username, password)
      yield ()
    }

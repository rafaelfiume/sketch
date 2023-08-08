package org.fiume.sketch.shared.auth0.testkit

import cats.effect.{IO, Ref}
import cats.implicits.*
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth0.algebras.UsersStore

import java.util.UUID

trait UsersStoreContext:

  // TODO temp?
  def makeUsersStore(): IO[UsersStore[IO, IO]] = makeUsersStore(Map.empty)

  def makeUsersStore(credentials: UserCredentialsWithId): IO[UsersStore[IO, IO]] =
    makeUsersStore(
      Map(
        credentials.uuid -> UserCredentials(credentials.username, credentials.hashedPassword, credentials.salt)
      )
    )

  def makeUsersStore(state: Map[UUID, UserCredentials]): IO[UsersStore[IO, IO]] =
    Ref.of[IO, Map[UUID, UserCredentials]](state).map { storage =>
      new UsersStore[IO, IO]:
        override def store(credentials: UserCredentials): IO[UUID] =
          IO.randomUUID.flatMap { uuid =>
            storage
              .update {
                _.updated(uuid, credentials)
              }
              .as(uuid)
          }

        override def fetchUser(uuid: UUID): IO[Option[User]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, storedCreds) if storedUuid == uuid =>
              User(uuid, storedCreds.username)
          })

        override def fetchCredentials(username: Username): IO[Option[UserCredentialsWithId]] =
          storage.get.map(_.collectFirst {
            case (uuid, storedCreds) if storedCreds.username == username =>
              UserCredentials.withUuid(uuid, storedCreds)
          })

        override def updatePassword(uuid: UUID, newPassword: HashedPassword): IO[Unit] =
          storage.update {
            _.updatedWith(uuid) {
              case Some(storedCreds) => UserCredentials(storedCreds.username, newPassword, storedCreds.salt).some
              case None              => none
            }
          }

        override def delete(uuid: UUID): IO[Unit] =
          storage.update(_.removed(uuid))

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }

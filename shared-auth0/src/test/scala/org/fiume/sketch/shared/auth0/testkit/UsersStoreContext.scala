package org.fiume.sketch.shared.auth0.testkit

import cats.effect.{IO, Ref}
import cats.implicits.*
import org.fiume.sketch.shared.auth0.{Account, AccountState, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.jobs.PermanentAccountDeletionJob

import java.time.Instant

trait UsersStoreContext:

  def makeUsersStore(): IO[UsersStore[IO, IO]] = makeUsersStore(Map.empty)

  def makeUsersStore(credentials: UserCredentialsWithId): IO[UsersStore[IO, IO]] =
    makeUsersStore(
      Map(
        credentials.uuid -> Account(credentials.uuid, credentials, AccountState.Active(Instant.now()))
      )
    )

  def makeUsersStore(account: Account): IO[UsersStore[IO, IO]] =
    makeUsersStore(Map(account.uuid -> account))

  private def makeUsersStore(state: Map[UserId, Account]): IO[UsersStore[IO, IO]] =
    Ref.of[IO, Map[UserId, Account]](state).map { storage =>
      new UsersStore[IO, IO]:
        override def store(credentials: UserCredentials): IO[UserId] =
          IO.randomUUID.map(UserId(_)).flatMap { uuid =>
            val account = Account(uuid, credentials, AccountState.Active(Instant.now()))
            storage.update { _.updated(uuid, account) }.as(uuid)
          }

        override def fetchAccount(username: Username): IO[Option[Account]] =
          storage.get.map(_.collectFirst {
            case (_, account) if account.credentials.username == username => account
          })

        override def fetchCredentials(username: Username): IO[Option[UserCredentialsWithId]] =
          storage.get.map(_.collectFirst {
            case (uuid, account) if account.credentials.username == username =>
              UserCredentials.make(uuid, account.credentials)
          })

        override def updatePassword(uuid: UserId, newPassword: HashedPassword): IO[Unit] = ???

        override protected def softDeleteAccount(uuid: UserId): IO[Instant] =
          val deletedAt = Instant.now()
          storage
            .update {
              _.updatedWith(uuid) {
                case Some(account) =>
                  val softDeletion = AccountState.SoftDeleted(deletedAt)
                  account.copy(state = softDeletion).some
                case None => none
              }
            }
            .as(deletedAt)

        override protected def schedulePermanentDeletion(
          uuid: UserId,
          permanentDeletionAt: Instant
        ): IO[PermanentAccountDeletionJob] = ???

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action
    }

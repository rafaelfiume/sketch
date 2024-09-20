package org.fiume.sketch.shared.auth0.testkit

import cats.effect.{IO, Ref}
import cats.effect.kernel.Clock
import cats.implicits.*
import org.fiume.sketch.shared.auth0.{Account, AccountState, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.jobs.{JobId, PermanentAccountDeletionJob}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*

import java.time.Instant
import scala.concurrent.duration.*

trait UsersStoreContext:

  def makeEmptyUsersStore(
    clock: Clock[IO] = Clock[IO],
    delayUntilPermanentDeletion: Duration = 1.second
  ): IO[UsersStore[IO, IO]] = makeUsersStore(Map.empty, clock, delayUntilPermanentDeletion)

  def makeUsersStore(
    credentials: UserCredentialsWithId,
    clock: Clock[IO] = Clock[IO],
    delayUntilPermanentDeletion: Duration = 1.second
  ): IO[UsersStore[IO, IO]] =
    makeUsersStore(
      Map(
        credentials.uuid -> Account(credentials.uuid, credentials, AccountState.Active(Instant.now()))
      ),
      clock,
      delayUntilPermanentDeletion
    )

  def makeUsersStoreForAccount(
    account: Account,
    clock: Clock[IO] = Clock[IO],
    delayUntilPermanentDeletion: Duration = 1.second
  ): IO[UsersStore[IO, IO]] =
    makeUsersStore(Map(account.uuid -> account), clock, delayUntilPermanentDeletion)

  private def makeUsersStore(
    state: Map[UserId, Account],
    clock: Clock[IO],
    delayUntilPermanentDeletion: Duration
  ): IO[UsersStore[IO, IO]] =
    Ref.of[IO, Map[UserId, Account]](state).map { storage =>
      new UsersStore[IO, IO]:
        override def store(credentials: UserCredentials): IO[UserId] =
          for
            uuid <- IO.randomUUID.map(UserId(_))
            account <- clock.realTimeInstant.map { now => Account(uuid, credentials, AccountState.Active(now)) }
            _ <- storage.update { _.updated(uuid, account) }
          yield uuid

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
          clock.realTimeInstant.flatMap { deletedAt =>
            storage
              .update {
                _.updatedWith(uuid) {
                  case Some(account) =>
                    val softDeation = AccountState.SoftDeleted(deletedAt)
                    account.copy(state = softDeation).some
                  case None => none
                }
              }
              .as(deletedAt)
          }

        override protected def schedulePermanentDeletion(
          userId: UserId,
          permanentDeletionAt: Instant
        ): IO[PermanentAccountDeletionJob] =
          for
            jobId <- IO.randomUUID.map(JobId(_))
            account <- fetchAccountByUserId(userId).map(_.someOrFail)
            permanentDeletionAt = account.state
              .asInstanceOf[AccountState.SoftDeleted]
              .deletedAt
              .plusSeconds(delayUntilPermanentDeletion.toSeconds)
          yield PermanentAccountDeletionJob(jobId, userId, permanentDeletionAt)

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action

        private def fetchAccountByUserId(userId: UserId): IO[Option[Account]] = storage.get.map(_.get(userId))
    }

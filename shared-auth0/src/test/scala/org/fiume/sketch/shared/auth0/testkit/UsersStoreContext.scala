package org.fiume.sketch.shared.auth0.testkit

import cats.effect.IO
import cats.effect.kernel.Clock
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.{Account, AccountState, User, UserId}
import org.fiume.sketch.shared.auth0.domain.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.domain.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth0.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.jobs.JobId
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*

trait UsersStoreContext:

  private case class State(
    accounts: Map[UserId, Account],
    scheduledDeletions: Map[JobId, ScheduledAccountDeletion]
  ):
    def ++(account: Account): State = copy(accounts = accounts + (account.uuid -> account))
    def getAccount(uuid: UserId): Option[Account] = accounts.get(uuid)
    def getAccount(username: Username): Option[Account] = accounts.collectFirst {
      case (_, account) if account.credentials.username == username => account
    }
    def --(uuid: UserId): State = copy(accounts = accounts - uuid)

    def +++(scheduledDeletion: ScheduledAccountDeletion): State =
      copy(scheduledDeletions = scheduledDeletions + (JobId(UUID.randomUUID()) -> scheduledDeletion))

    def ---(userId: UserId): State =
      scheduledDeletions
        .collectFirst {
          case (jobId, deletion) if deletion.userId == userId => jobId
        }
        .fold(this)(jobId => copy(scheduledDeletions = scheduledDeletions - jobId))

    def getScheduledJob(userId: UserId): Option[ScheduledAccountDeletion] =
      scheduledDeletions.collectFirst {
        case (_, schedule) if schedule.userId == userId => schedule
      }

  private object State:
    val empty = State(Map.empty, Map.empty)
    def makeWith(account: Account): State = State(Map(account.uuid -> account), Map.empty)

  def makeEmptyUsersStore(
    clock: Clock[IO] = Clock[IO],
    delayUntilPermanentDeletion: Duration = 1.second
  ): IO[UsersStore[IO, IO]] = makeUsersStore(State.empty, clock, delayUntilPermanentDeletion)

  def makeUsersStore(
    credentials: UserCredentialsWithId,
    clock: Clock[IO] = Clock[IO],
    delayUntilPermanentDeletion: Duration = 1.second
  ): IO[UsersStore[IO, IO]] =
    val account = Account(credentials.uuid, credentials, AccountState.Active(Instant.now()))
    makeUsersStoreForAccount(account, clock, delayUntilPermanentDeletion)

  def makeUsersStoreForAccount(
    account: Account,
    clock: Clock[IO] = Clock[IO],
    delayUntilPermanentDeletion: Duration = 1.second
  ): IO[UsersStore[IO, IO]] =
    makeUsersStore(State.makeWith(account), clock, delayUntilPermanentDeletion)

  private def makeUsersStore(
    state: State,
    clock: Clock[IO],
    delayUntilPermanentDeletion: Duration
  ): IO[UsersStore[IO, IO]] =
    IO.ref(state).map { storage =>
      new UsersStore[IO, IO]:
        override def createAccount(credentials: UserCredentials): IO[UserId] =
          for
            uuid <- IO.randomUUID.map(UserId(_))
            account <- clock.realTimeInstant.map { now => Account(uuid, credentials, AccountState.Active(now)) }
            _ <- storage.update { _.++(account) }
          yield uuid

        override def fetchAccount(userId: UserId): IO[Option[Account]] = storage.get.map(_.getAccount(userId))

        override def fetchAccount(username: Username): IO[Option[Account]] = storage.get.map(_.getAccount(username))

        override def updatePassword(userId: UserId, newPassword: HashedPassword): IO[Unit] = ???

        override def deleteAccount(userId: UserId): IO[Unit] = storage.update { _.--(userId) }.void

        override def claimNextJob(): IO[Option[ScheduledAccountDeletion]] = ???

        override protected def updateAccount(account: Account): IO[Unit] = storage.update { _.++(account) }.void

        override protected def schedulePermanentDeletion(
          userId: UserId,
          permanentDeletionAt: Instant
        ): IO[ScheduledAccountDeletion] =
          for
            jobId <- IO.randomUUID.map(JobId(_))
            account <- fetchAccount(userId).map(_.someOrFail)
            permanentDeletionAt = account.state
              .asInstanceOf[AccountState.SoftDeleted]
              .deletedAt
              .plusSeconds(delayUntilPermanentDeletion.toSeconds)
            job = ScheduledAccountDeletion(jobId, userId, permanentDeletionAt)
            _ <- storage.update { _.+++(job) }
          yield job

        override protected def unschedulePermanentDeletion(uuid: UserId): IO[Unit] =
          storage.update { _.---(uuid) }.void

        override protected def getNow(): IO[Instant] = clock.realTimeInstant

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action
    }

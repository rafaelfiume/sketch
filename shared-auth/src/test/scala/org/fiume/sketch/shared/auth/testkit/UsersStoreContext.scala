package org.fiume.sketch.shared.auth.testkit

import cats.effect.IO
import cats.effect.kernel.Clock
import cats.implicits.*
import org.fiume.sketch.shared.auth.Passwords.HashedPassword
import org.fiume.sketch.shared.auth.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.auth.algebras.UsersStore

import java.time.Instant

trait UsersStoreContext:

  private case class State(accounts: Map[UserId, Account]):
    def ++(account: Account): State = copy(accounts = accounts + (account.uuid -> account))
    def getAccount(uuid: UserId): Option[Account] = accounts.get(uuid)
    def getAccount(username: Username): Option[Account] = accounts.collectFirst {
      case (_, account) if account.credentials.username == username => account
    }
    def --(uuid: UserId): State = copy(accounts = accounts - uuid)
    def contains(uuid: UserId): Boolean = accounts.contains(uuid)

  private object State:
    val empty = State(Map.empty)
    def makeWith(account: Account): State = State(Map(account.uuid -> account))

  def makeEmptyUsersStore(clock: Clock[IO] = Clock[IO]): IO[UsersStore[IO, IO]] = makeUsersStore(State.empty, clock)

  def makeUsersStore(credentials: UserCredentialsWithId, clock: Clock[IO] = Clock[IO]): IO[UsersStore[IO, IO]] =
    val account = Account(credentials.uuid, credentials, AccountState.Active(Instant.now()))
    makeUsersStoreForAccount(account, clock)

  def makeUsersStoreForAccount(account: Account, clock: Clock[IO] = Clock[IO]): IO[UsersStore[IO, IO]] =
    makeUsersStore(State.makeWith(account), clock)

  private def makeUsersStore(state: State, clock: Clock[IO]): IO[UsersStore[IO, IO]] =
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

        override def deleteAccount(uuid: UserId): IO[Option[UserId]] = storage.modify { state =>
          if state.contains(uuid) then state.--(uuid) -> uuid.some
          else state -> none
        }

        override def updateAccount(account: Account): IO[Unit] = storage.update { _.++(account) }.void

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action
    }

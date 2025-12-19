package org.fiume.sketch.shared.auth.testkit

import cats.effect.{Clock, IO}
import cats.implicits.*
import org.fiume.sketch.shared.auth.Passwords.HashedPassword
import org.fiume.sketch.shared.auth.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.testkit.TxRef

import java.time.Instant

trait UsersStoreContext:

  case class UsersState(accounts: Map[UserId, Account]):
    def ++(account: Account): UsersState = copy(accounts = accounts + (account.uuid -> account))
    def getAccount(uuid: UserId): Option[Account] = accounts.get(uuid)
    def getAccount(username: Username): Option[Account] = accounts.collectFirst {
      case (_, account) if account.credentials.username == username => account
    }
    def --(uuid: UserId): UsersState = copy(accounts = accounts - uuid)
    def contains(uuid: UserId): Boolean = accounts.contains(uuid)

  private object UsersState:
    val empty = UsersState(Map.empty)
    def makeWith(account: Account): UsersState = UsersState(Map(account.uuid -> account))

  def makeEmptyUsersStore(clock: Clock[IO] = Clock[IO]): IO[(UsersStore[IO], TxRef[UsersState])] =
    makeUsersStore(UsersState.empty, clock)

  def makeUsersStore(credentials: UserCredentialsWithId, clock: Clock[IO] = Clock[IO]): IO[(UsersStore[IO], TxRef[UsersState])] =
    val account = Account(credentials.uuid, credentials, AccountState.Active(Instant.now()))
    makeUsersStoreForAccount(account, clock)

  def makeUsersStoreForAccount(account: Account, clock: Clock[IO] = Clock[IO]): IO[(UsersStore[IO], TxRef[UsersState])] =
    makeUsersStore(UsersState.makeWith(account), clock)

  private def makeUsersStore(state: UsersState, clock: Clock[IO]): IO[(UsersStore[IO], TxRef[UsersState])] =
    for tx <- TxRef.of(state)
    yield (
      new UsersStore[IO]:
        override def createAccount(credentials: UserCredentials): IO[UserId] =
          for
            uuid <- IO.randomUUID.map(UserId(_))
            account <- clock.realTimeInstant.map { now => Account(uuid, credentials, AccountState.Active(now)) }
            _ <- tx.update { _.++(account) }
          yield uuid

        override def fetchAccount(userId: UserId): IO[Option[Account]] = tx.get.map(_.getAccount(userId))

        override def fetchAccount(username: Username): IO[Option[Account]] = tx.get.map(_.getAccount(username))

        override def updatePassword(userId: UserId, newPassword: HashedPassword): IO[Unit] = ???

        override def deleteAccount(uuid: UserId): IO[Option[UserId]] = tx.modify { state =>
          if state.contains(uuid) then state.--(uuid) -> uuid.some
          else state -> none
        }

        override def updateAccount(account: Account): IO[Unit] = tx.update { _.++(account) }.void
    ) -> tx

package org.fiume.sketch.shared.auth.algebras

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.auth.{User, UserId}
import org.fiume.sketch.shared.auth.Passwords.HashedPassword
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.accounts.Account
import org.fiume.sketch.shared.common.app.Store

trait UsersStore[F[_], Txn[_]: Monad] extends Store[F, Txn]:

  def createAccount(credentials: UserCredentials): Txn[UserId]

  def fetchAccount(userId: UserId): Txn[Option[Account]]

  def fetchAccountWith[T](userId: UserId)(f: Option[Account] => T): Txn[T] =
    fetchAccount(userId).map(f)

  def fetchAccount(username: Username): Txn[Option[Account]]

  def updatePassword(userId: UserId, password: HashedPassword): Txn[Unit]

  def updateAccount(account: Account): Txn[Unit]

  def deleteAccount(userId: UserId): Txn[Option[UserId]]

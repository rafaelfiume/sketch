package org.fiume.sketch.shared.auth0.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.{Account, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*

trait UsersStore[F[_], Txn[_]] extends Store[F, Txn]:
  def store(credentials: UserCredentials): Txn[UserId]
  def fetchAccount(username: Username): Txn[Option[Account]]
  def fetchCredentials(username: Username): Txn[Option[UserCredentialsWithId]]
  def updatePassword(uuid: UserId, password: HashedPassword): Txn[Unit]
  // TODO Distinguish between soft and hard deletion
  def markForDeletion(uuid: UserId): Txn[Unit]

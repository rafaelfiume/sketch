package org.fiume.sketch.shared.auth0.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.{User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*

trait UsersStore[F[_], Txn[_]] extends Store[F, Txn]:
  def store(credentials: UserCredentials): Txn[UserId]
  // TODO Remove it from algebra since this is only being used for testing purposes
  def fetchUser(uuid: UserId): Txn[Option[User]]
  def fetchCredentials(username: Username): Txn[Option[UserCredentialsWithId]]
  def updatePassword(uuid: UserId, password: HashedPassword): Txn[Unit]
  // TODO Distinguish between soft and hard deletion
  def delete(uuid: UserId): Txn[Unit]

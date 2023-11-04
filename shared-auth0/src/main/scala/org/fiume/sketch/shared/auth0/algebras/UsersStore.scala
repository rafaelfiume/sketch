package org.fiume.sketch.shared.auth0.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.{User, UserUuid}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*

trait UsersStore[F[_], Txn[_]] extends Store[F, Txn]:
  def store(credentials: UserCredentials): Txn[UserUuid]
  def fetchUser(uuid: UserUuid): Txn[Option[User]]
  def fetchCredentials(username: Username): Txn[Option[UserCredentialsWithId]]
  def updatePassword(uuid: UserUuid, password: HashedPassword): Txn[Unit]
  def delete(uuid: UserUuid): Txn[Unit]

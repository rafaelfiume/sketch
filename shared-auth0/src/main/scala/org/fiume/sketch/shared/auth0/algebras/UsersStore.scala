package org.fiume.sketch.shared.auth0.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.*

import java.util.UUID

trait UsersStore[F[_], Txn[_]] extends Store[F, Txn]:
  def store(credentials: UserCredentials): Txn[UUID]
  def fetchUser(uuid: UUID): Txn[Option[User]]
  def fetchCredentials(username: Username): Txn[Option[UserCredentialsWithId]]
  def updatePassword(uuid: UUID, password: HashedPassword): Txn[Unit]
  def delete(uuid: UUID): Txn[Unit]

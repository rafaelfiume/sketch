package org.fiume.sketch.storage.auth0.algebras

import org.fiume.sketch.storage.auth0.Model.*
import org.fiume.sketch.storage.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.storage.postgres.Store
import java.util.UUID

trait UsersStore[F[_], Txn[_]] extends Store[F, Txn]:
  def store(user: User, password: HashedPassword, salt: Salt): Txn[UUID]
  // TODO Consider fetching and updating by id instead of username
  def fetchCredentials(uuid: UUID): Txn[Option[UserCredentials]]
  def fetchUser(uuid: UUID): Txn[Option[User]]
  def updateUser(uuid: UUID, user: User): Txn[Unit]

  /**
   * Salt must be retained and used during password update. One for the service layer.
   */
  def updatePassword(uuid: UUID, password: HashedPassword): Txn[Unit]
  def remove(uuid: UUID): Txn[Unit]

package org.fiume.sketch.storage.auth0.algebras

import org.fiume.sketch.storage.algebras.Store
import org.fiume.sketch.storage.auth0.{HashedPassword, Salt}
import org.fiume.sketch.storage.auth0.Model.*

trait UserStore[F[_], Txn[_]] extends Store[F, Txn]:
  def store(user: User, password: HashedPassword, salt: Salt): Txn[UserCredentials]
  def fetchCredentials(username: Username): Txn[Option[UserCredentials]]
  def fetchUser(username: Username): Txn[Option[User]]
  def updateUser(user: User): Txn[Unit]

  /**
   * Salt be retained and used during password update One for the service layer.
   */
  def updatePassword(username: Username, password: HashedPassword): Txn[Unit]
  def remove(username: Username): Txn[Unit]

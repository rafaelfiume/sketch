package org.fiume.sketch.auth0

import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore

trait UsersManager[F[_]]:
  def registreUser(username: Username, password: PlainPassword): F[User]

object UsersManager:
  def make[F[_], Txn[_]](store: UsersStore[F, Txn])(using F: Sync[F]): F[UsersManager[F]] = F.delay {
    new UsersManager[F]:
      override def registreUser(username: Username, password: PlainPassword): F[User] =
        for
          salt <- Salt.generate[F]()
          // TODO Make #hashPassword and #veryPassword effectful and `F.blocking` them?
          hashedPassword <- F.blocking { HashedPassword.hashPassword(password, salt) }
          credentials = UserCredentials(username, hashedPassword, salt)
          user <- store.commit { store.store(credentials) }.map { User(_, username) }
        yield user
  }

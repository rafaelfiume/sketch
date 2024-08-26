package org.fiume.sketch.auth0

import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.Store.Syntax.commit
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore

trait UsersManager[F[_]]:
  def registreUser(username: Username, password: PlainPassword): F[User]

object UsersManager:
  def make[F[_]: Sync, Txn[_]](store: UsersStore[F, Txn]): F[UsersManager[F]] = Sync[F].delay {
    given UsersStore[F, Txn] = store

    new UsersManager[F]:
      override def registreUser(username: Username, password: PlainPassword): F[User] =
        for
          salt <- Salt.generate[F]()
          hashedPassword <- Sync[F].blocking { HashedPassword.hashPassword(password, salt) }
          credentials = UserCredentials(username, hashedPassword, salt)
          user <- store.store(credentials).commit().map { User(_, username) }
        yield user
  }

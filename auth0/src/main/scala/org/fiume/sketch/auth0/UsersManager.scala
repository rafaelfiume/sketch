package org.fiume.sketch.auth0

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.authorisation.{AccessControl, GlobalRole}
import org.fiume.sketch.shared.app.algebras.Store.Syntax.commit
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.UserId

trait UsersManager[F[_]]:
  def createAccount(username: Username, password: PlainPassword, isSuperuser: Boolean = false): F[UserId]

object UsersManager:
  def make[F[_]: Sync, Txn[_]: Monad](store: UsersStore[F, Txn], accessControl: AccessControl[F, Txn]): F[UsersManager[F]] =
    Sync[F].delay {
      given UsersStore[F, Txn] = store

      new UsersManager[F]:
        override def createAccount(username: Username, password: PlainPassword, isSuperuser: Boolean): F[UserId] =
          val credentials = for
            salt <- Salt.generate()
            hashedPassword <- HashedPassword.hashPassword(password, salt)
          yield UserCredentials(username, hashedPassword, salt)

          val setUpAccount = for
            creds <- store.lift { credentials }
            userId <- store.store(creds)
            _ <- accessControl.grantGlobalAccess(userId, GlobalRole.Superuser).whenA(isSuperuser)
          yield userId

          setUpAccount.commit()
    }

package org.fiume.sketch.auth

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.Store.syntax.*
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.auth.domain.{User, UserId}
import org.fiume.sketch.shared.auth.domain.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth.domain.User.*
import org.fiume.sketch.shared.authorisation.{AccessControl, ContextualRole, GlobalRole}

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
            userId <- store.createAccount(creds)
            _ <- accessControl.grantAccess(userId, userId, ContextualRole.Owner)
            _ <- accessControl.grantGlobalAccess(userId, GlobalRole.Superuser).whenA(isSuperuser)
          yield userId

          setUpAccount.commit()
    }
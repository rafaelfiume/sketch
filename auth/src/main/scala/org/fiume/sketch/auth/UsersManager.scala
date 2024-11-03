package org.fiume.sketch.auth

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.auth.domain.{User, UserId}
import org.fiume.sketch.shared.auth.domain.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth.domain.User.*
import org.fiume.sketch.shared.authorisation.{AccessControl, ContextualRole, GlobalRole}
import org.fiume.sketch.shared.common.algebras.syntax.StoreSyntax.*

trait UsersManager[F[_]]:
  def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole] = none): F[UserId]

object UsersManager:
  def make[F[_]: Sync, Txn[_]: Monad](store: UsersStore[F, Txn], accessControl: AccessControl[F, Txn]): F[UsersManager[F]] =
    Sync[F].delay {
      given UsersStore[F, Txn] = store

      new UsersManager[F]:
        override def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole]): F[UserId] =
          val credentials = for
            salt <- Salt.generate()
            hashedPassword <- HashedPassword.hashPassword(password, salt)
          yield UserCredentials(username, hashedPassword, salt)

          val setUpAccount = for
            creds <- store.lift { credentials }
            userId <- store.createAccount(creds)
            _ <- accessControl.grantAccess(userId, userId, ContextualRole.Owner)
            _ <- globalRole.fold(ifEmpty = ().pure[Txn])(accessControl.grantGlobalAccess(userId, _))
          yield userId

          setUpAccount.commit()
    }

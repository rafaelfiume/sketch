package org.fiume.sketch.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.UserId

trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit] =
    storeGlobalGrant(userId, role)

  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit] =
    storeGrant(userId, entityId, role)

  def ensureAccess[T <: Entity](userId: UserId, role: ContextualRole)(entityIdTxn: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    entityIdTxn.flatMap { entityId =>
      grantAccess(userId, entityId, role).as(entityId)
    }

  def canAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Boolean] =
    // a simplistic implementation that works since currently there is only one global and one contextual roles
    fetchRole(userId, entityId).map(_.isDefined)

  def attemptWithAuthorisation[T <: Entity, A](userId: UserId, entityId: EntityId[T])(
    ops: EntityId[T] => Txn[A]
  ): Txn[Either[Unauthorised, A]] =
    canAccess(userId, entityId).ifM(
      ifTrue = ops(entityId).map(Right(_)),
      ifFalse = Left("Unauthorised").pure[Txn]
    )

  // TODO Return role as well as ids?
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[Txn, EntityId[T]]

  def revokeAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] =
    deleteGrant(userId, entityId)

  def storeGlobalGrant(userId: UserId, role: GlobalRole): Txn[Unit]

  def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]

  def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

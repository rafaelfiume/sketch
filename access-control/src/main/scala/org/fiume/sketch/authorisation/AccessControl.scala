package org.fiume.sketch.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.UserId

/* Contextual roles, ie. a user can be a viewer of a document, but owner of another. */
trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def allowAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: Role): Txn[Unit] =
    // Both allowAccess and revokeAccess need to check if user can grant or revoke access before performing the action
    // For now, any user will be able to do it.
    // It should be OK giving allowAccess will be used upon entity creation, and similarly to revokeAccess
    storeGrant(userId, entityId, role)

  def createEntityThenAllowAccess[T <: Entity](userId: UserId, role: Role)(
    entityIdTxn: => Txn[EntityId[T]]
  ): Txn[EntityId[T]] =
    entityIdTxn.flatMap { entityId =>
      storeGrant(userId, entityId, role).as(entityId)
    }

  def canAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Boolean] =
    fetchRole(userId, entityId).map(_.map(_ == Role.Owner).getOrElse(false))

  def fetchEntityIfAuthorised[T <: Entity, A](userId: UserId, entityId: EntityId[T])(
    ops: EntityId[T] => Txn[A]
  ): Txn[Either[Unauthorised, A]] =
    canAccess(userId, entityId).ifM(
      ifTrue = ops(entityId).map(Right(_)),
      ifFalse = Left("Unauthorised").pure[Txn]
    )

  // TODO Return role as well as ids?
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId): fs2.Stream[Txn, EntityId[T]]

  def revokeAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] =
    deleteGrant(userId, entityId)

  def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: Role): Txn[Unit]

  def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

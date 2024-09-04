package org.fiume.sketch.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.UserId

/* Contextual roles, ie. a user can be a contributor of a document, but owner of another. */
trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def allowAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: Role): Txn[Unit] =
    storeGrant(userId, entityId, role)

  def ensureAccess[T <: Entity](userId: UserId, role: Role)(entityIdTxn: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    entityIdTxn.flatMap { entityId =>
      storeGrant(userId, entityId, role).as(entityId)
    }

  def canAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Boolean] =
    fetchRole(userId, entityId).map(_.map(_ == Role.Owner).getOrElse(false))

  def attemptWithAuthorisation[T <: Entity, A](userId: UserId, entityId: EntityId[T])(
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

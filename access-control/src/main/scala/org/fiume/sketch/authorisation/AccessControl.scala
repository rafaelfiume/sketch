package org.fiume.sketch.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.app.{Resource, ResourceId}
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.UserId

/* Contextual roles, ie. a user can be a viewer of a document, but owner of another. */
trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def allowAccess[T <: Resource](userId: UserId, resourceId: ResourceId[T], role: Role): Txn[Unit] =
    // Both allowAccess and revokeAccess need to check if user can grant or revoke access before performing the action
    // For now, any user will be able to do it.
    // It should be OK giving allowAccess will be used upon resource creation, and similarly to revokeAccess
    storeGrant(userId, resourceId, role)

  def revokeAccess[T <: Resource](userId: UserId, resourceId: ResourceId[T]): Txn[Unit] =
    deleteGrant(userId, resourceId)

  def canAccess[T <: Resource](userId: UserId, resourceId: ResourceId[T]): Txn[Boolean] =
    fetchRole(userId, resourceId).map(_.map(_ == Role.Owner).getOrElse(false))

  def canAccess[T <: Resource, A](userId: UserId, resourceId: ResourceId[T])(
    ops: ResourceId[T] => Txn[A]
  ): Txn[Either[Unauthorised, A]] =
    canAccess(userId, resourceId).ifM(
      ifTrue = ops(resourceId).map(Right(_)),
      ifFalse = Left("Unauthorised").pure[Txn]
    )

  def storeGrant[T <: Resource](userId: UserId, resourceId: ResourceId[T], role: Role): Txn[Unit]

  def fetchRole[T <: Resource](userId: UserId, resourceId: ResourceId[T]): Txn[Option[Role]]

  def deleteGrant[T <: Resource](userId: UserId, resourceId: ResourceId[T]): Txn[Unit]

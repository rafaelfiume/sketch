package org.fiume.sketch.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.authorisation.ContextualRole.*
import org.fiume.sketch.authorisation.GlobalRole.*
import org.fiume.sketch.authorisation.Role.{Contextual, Global}
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.UserId

/*
 * Privilegies: there's currently no distinction on what a user can once it obtain access to an entity,
 * but only which users can access certain entities.
 *
 * In other words, once a user has access to an entity, she is authorised to read, write and delete it.
 * There is currently no restrictions on creation aside from the requiring an authenticated user.
 *
 * The former should change when introducing contributors, which should be only allowed to edit and possibly
 * create entities. The latter could change with, for instance, limiting the number of entities of a certain
 * kind a user is authorised to create.
 */
trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit] = storeGlobalGrant(userId, role)

  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit] =
    storeGrant(userId, entityId, role)

  // TODO Rename to ensureOwnership?
  def ensureAccess[T <: Entity](userId: UserId, role: ContextualRole)(entityIdTxn: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    entityIdTxn.flatTap { grantAccess(userId, _, role) }

  def canAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Boolean] =
    // fetchRole should return the least permissive role first
    fetchRole(userId, entityId).map { role =>
      (role, entityId.entityType) match
        case (None, _)                                    => false
        case (Some(Role.Contextual(Owner)), _)            => true
        case (Some(Role.Global(Superuser)), "UserEntity") => false
        case (Some(Role.Global(Superuser)), _)            => true
        case (Some(Role.Global(Admin)), _)                => true
    }

  def attemptWithAuthorisation[T <: Entity, A](userId: UserId, entityId: EntityId[T])(
    ops: EntityId[T] => Txn[A]
  ): Txn[Either[Unauthorised, A]] =
    canAccess(userId, entityId).ifM(
      ifTrue = ops(entityId).map(Right(_)),
      ifFalse = Left("Unauthorised").pure[Txn]
    )

  // This needs to respect the same rules as `canAccess`
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[Txn, EntityId[T]]
  // if it is admin, access anything

  // Should revoke access work for global roles?
  def revokeAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] = deleteGrant(userId, entityId)

  protected def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  protected def storeGlobalGrant(userId: UserId, role: GlobalRole): Txn[Unit]

  protected def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]

  protected def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

package org.fiume.sketch.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.authorisation.ContextualRole.*
import org.fiume.sketch.authorisation.GlobalRole.*
import org.fiume.sketch.authorisation.Role.{Contextual, Global}
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.domain.UserId

trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit] = storeGlobalGrant(userId, role)

  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit] =
    storeGrant(userId, entityId, role)

  def ensureAccess[T <: Entity](userId: UserId, role: ContextualRole)(entityIdTxn: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    entityIdTxn.flatTap { grantAccess(userId, _, role) }

  // Should canAccess return `Either`? For example, `Left(NotFound)`?
  def canAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Boolean] =
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

  // It needs to respect the same rules as `canAccess`
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[Txn, EntityId[T]]

  def revokeContextualAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] =
    deleteContextualGrant(userId, entityId)

  protected def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  protected def storeGlobalGrant(userId: UserId, role: GlobalRole): Txn[Unit]

  protected def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]

  protected def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

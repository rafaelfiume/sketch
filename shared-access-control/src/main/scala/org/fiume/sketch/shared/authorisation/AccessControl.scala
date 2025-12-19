package org.fiume.sketch.shared.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.authorisation.ContextualRole.*
import org.fiume.sketch.shared.authorisation.GlobalRole.*
import org.fiume.sketch.shared.authorisation.Role.{Contextual, Global}
import org.fiume.sketch.shared.common.{Entity, EntityId, WithUuid}

trait AccessControl[Txn[_]: Monad]:

  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit] = storeGlobalGrant(userId, role)

  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit] =
    storeGrant(userId, entityId, role)

  def ensureAccess[E, R <: WithUuid[?]](userId: UserId, role: ContextualRole)(txn: => Txn[Either[E, R]]): Txn[Either[E, R]] =
    txn.flatTap {
      _.fold(_ => ().pure[Txn], result => grantAccess(userId, result.uuid, role))
    }

  def ensureAccess_[T <: Entity](userId: UserId, role: ContextualRole)(txn: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    // can be implemented in terms of `ensureAccess`, which is cool.
    txn.flatTap { id => grantAccess(userId, id, role) }

  def ensureRevoked[E, T <: Entity, R <: WithUuid[?]](userId: UserId, entityId: EntityId[T])(
    ops: EntityId[T] => Txn[Either[E, R]]
  ): Txn[Either[E, R]] =
    ops(entityId).flatTap { outcome =>
      revokeContextualAccess(userId, entityId).whenA(outcome.isRight)
    }

  def ensureRevoked_[T1 <: Entity, T2 <: Entity](userId: UserId, entityId: EntityId[T1])(
    ops: EntityId[T1] => Txn[EntityId[T2]]
  ): Txn[EntityId[T2]] =
    // can be implemented in terms of `ensureAccess`, which is cool.
    ops(entityId).flatTap(_ => revokeContextualAccess(userId, entityId))

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

  def attempt[T <: Entity, A](userId: UserId, entityId: EntityId[T])(
    ops: EntityId[T] => Txn[A]
  ): Txn[Either[AccessDenied.type, A]] =
    canAccess(userId, entityId).ifM(
      ifTrue = ops(entityId).map(Right(_)),
      ifFalse = AccessDenied.asLeft.pure[Txn]
    )

  // It needs to respect the same rules as `canAccess`
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[Txn, EntityId[T]]

  def revokeContextualAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] =
    deleteContextualGrant(userId, entityId)

  protected def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  protected def storeGlobalGrant(userId: UserId, role: GlobalRole): Txn[Unit]

  protected def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]

  protected def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

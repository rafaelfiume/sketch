package org.fiume.sketch.shared.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.auth.domain.UserId
import org.fiume.sketch.shared.authorisation.ContextualRole.*
import org.fiume.sketch.shared.authorisation.GlobalRole.*
import org.fiume.sketch.shared.authorisation.Role.{Contextual, Global}
import org.fiume.sketch.shared.common.{Entity, EntityId, WithUuid}
import org.fiume.sketch.shared.common.algebras.Store

trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  type Unauthorised = String

  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit] = storeGlobalGrant(userId, role)

  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit] =
    storeGrant(userId, entityId, role)

  def ensureAccess[E, R <: WithUuid[?]](userId: UserId, role: ContextualRole)(txn: => Txn[Either[E, R]]): Txn[Either[E, R]] =
    txn.flatTap {
      case Right(result) => grantAccess(userId, result.uuid, role)
      case Left(_)       => ().pure[Txn] // ignore TODO Is there a more ellegant way?
    }

  def ensureAccess_[T <: Entity](userId: UserId, role: ContextualRole)(entityIdTxn: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    ensureAccess(userId, role) { entityIdTxn.map(WithUuid.make(_).asRight) }.map {
      _.getOrElse(throw AssertionError("there is no left")).uuid
    }

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
      ifFalse = Left("Unauthorised").pure[Txn] // TODO Replace this left by AuthorisationError
    )

  // It needs to respect the same rules as `canAccess`
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[Txn, EntityId[T]]

  def revokeContextualAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] =
    deleteContextualGrant(userId, entityId)

  protected def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  protected def storeGlobalGrant(userId: UserId, role: GlobalRole): Txn[Unit]

  protected def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]

  protected def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

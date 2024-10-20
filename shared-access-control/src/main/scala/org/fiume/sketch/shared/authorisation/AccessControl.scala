package org.fiume.sketch.shared.authorisation

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.auth.domain.{AccountStateTransitionError, UserId}
import org.fiume.sketch.shared.authorisation.AuthorisationError.*
import org.fiume.sketch.shared.authorisation.ContextualRole.*
import org.fiume.sketch.shared.authorisation.GlobalRole.*
import org.fiume.sketch.shared.authorisation.Role.{Contextual, Global}
import org.fiume.sketch.shared.common.{Entity, EntityId, WithUuid}
import org.fiume.sketch.shared.common.algebras.Store

trait AccessControl[F[_], Txn[_]: Monad] extends Store[F, Txn]:

  def grantGlobalAccess(userId: UserId, role: GlobalRole): Txn[Unit] = storeGlobalGrant(userId, role)

  def grantAccess[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit] =
    storeGrant(userId, entityId, role)

  // TODO Test this
  def ensureAccess[E, R <: WithUuid[?]](userId: UserId, role: ContextualRole)(
    manageEntity: => Txn[Either[E, R]]
  ): Txn[Either[E, R]] =
    manageEntity.flatTap {
      case Right(result) => grantAccess(userId, result.uuid, role)
      case Left(_)       => ().pure[Txn] // TODO Is there a more ellegant way?
    }

  def ensureAccess_[T <: Entity](userId: UserId, role: ContextualRole)(manageEntity: => Txn[EntityId[T]]): Txn[EntityId[T]] =
    ensureAccess(userId, role) { manageEntity.map(WithUuid.make(_).asRight) }.map {
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
    accessEntity: EntityId[T] => Txn[A]
  ): Txn[Either[AuthorisationError, A]] =
    canAccess(userId, entityId).ifM(
      ifTrue = accessEntity(entityId).map(Right(_)),
      ifFalse = UnauthorisedError.asLeft.pure[Txn]
    )

  // TODO Test this
  def attemptAccountManagementWithAuthorisation[E <: AccountStateTransitionError, R](
    authenticated: UserId,
    account: UserId,
    isAuthenticatedAccountActive: UserId => Txn[Boolean]
  )(
    // TODO Define a lower bound for type R so result can only be related to account management?
    changeAccountIfAuthorised: UserId => Txn[Either[E, R]]
  ): Txn[Either[E | AuthorisationError, R]] =
    (
      isAuthenticatedAccountActive(authenticated), // for when the user deactivates their own account
      canAccess(authenticated, account)
    ).mapN(_ && _)
      .ifM(
        ifTrue = changeAccountIfAuthorised(account).map { _.leftMap[E | AuthorisationError](identity) }, // widening the left type
        ifFalse = UnauthorisedError.asLeft.pure[Txn]
      )

  // It needs to respect the same rules as `canAccess`
  def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[Txn, EntityId[T]]

  def revokeContextualAccess[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit] =
    deleteContextualGrant(userId, entityId)

  protected def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Option[Role]]

  protected def storeGlobalGrant(userId: UserId, role: GlobalRole): Txn[Unit]

  protected def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): Txn[Unit]

  protected def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Txn[Unit]

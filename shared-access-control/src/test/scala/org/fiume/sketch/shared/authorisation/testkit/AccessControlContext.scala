package org.fiume.sketch.shared.authorisation.testkit

import cats.effect.IO
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.authorisation.{AccessControl, ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.common.{Entity, EntityId}
import org.fiume.sketch.shared.testkit.TxRef

/*
 * Note that this is a > simplified < version of AccessControlContext that provides support
 * for a single entity type.
 * In a real-world scenario, there is support for multiple entity types.
 * See, for example, PostgresAccessControl.
 */
trait AccessControlContext:

  case class PermissionsState(
    global: Map[UserId, GlobalRole],
    contextual: Map[(UserId, EntityId[?]), ContextualRole]
  ):
    def +++(userId: UserId, role: GlobalRole): PermissionsState = copy(global = global + (userId -> role))

    def getGlobalRole(userId: UserId): Option[Role.Global] = global.get(userId).map(Role.Global(_))

    def ++(userId: UserId, entityId: EntityId[?], role: ContextualRole): PermissionsState =
      copy(contextual = contextual + ((userId, entityId) -> role))

    def --(userId: UserId, entityId: EntityId[?]): PermissionsState = copy(contextual = contextual - (userId -> entityId))

    def getContextualRole(userId: UserId, entityId: EntityId[?]): Option[Role.Contextual] =
      contextual.get(userId -> entityId).map(Role.Contextual(_))

  private object PermissionsState:
    val empty = PermissionsState(Map.empty, Map.empty)

  def makeAccessControl(): IO[(AccessControl[IO] & InspectAccessControl, TxRef[PermissionsState])] =
    makeAccessControl(PermissionsState.empty)

  def makeUnreliableAccessControl(): AccessControl[IO] = new AccessControl[IO]:
    override def storeGlobalGrant(userId: UserId, role: GlobalRole): IO[Unit] = error
    override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): IO[Unit] = error
    override def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[IO, EntityId[T]] =
      fs2.Stream.eval[IO, EntityId[T]](error)
    override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Option[Role]] = error
    override def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Unit] = error
    private def error[A]: IO[A] = IO.raiseError(new RuntimeException("boom"))

  private def makeAccessControl(
    state: PermissionsState
  ): IO[(AccessControl[IO] & InspectAccessControl, TxRef[PermissionsState])] =
    for txRef <- TxRef.of(state)
    yield (
      new AccessControl[IO] with InspectAccessControl:

        override def getGlobalRole(userId: UserId): IO[Option[GlobalRole]] =
          txRef.get.map(_.getGlobalRole(userId).map(_.designation))

        override def storeGlobalGrant(userId: UserId, role: GlobalRole): IO[Unit] =
          txRef.update(_ +++ (userId, role))

        override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): IO[Unit] =
          txRef.update(_ ++ (userId, entityId, role))

        override def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[IO, EntityId[T]] =
          fs2.Stream.evals(
            txRef.get.map(
              _.contextual
                .collect {
                  case ((u -> id), _) if u == userId => id.asInstanceOf[EntityId[T]]
                }
                .toSeq
            )
          )

        override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Option[Role]] =
          // mimics the behaviour of fetchRole in PostgresAccessControl
          // where the least permissive contextual role take precedence over global roles
          txRef.get.map(state => state.getContextualRole(userId, entityId).orElse(state.getGlobalRole(userId)))

        override def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Unit] =
          txRef.update(_ -- (userId, entityId))
    ) -> txRef

trait InspectAccessControl:
  def getGlobalRole(userId: UserId): IO[Option[GlobalRole]]

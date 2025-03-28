package org.fiume.sketch.shared.authorisation.testkit

import cats.effect.IO
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.authorisation.{AccessControl, ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.common.{Entity, EntityId}

/*
 * Note that this is a > simplified < version of AccessControlContext that provides support
 * for a single entity type.
 * In a real-world scenario, there is support for multiple entity types.
 * See, for example, PostgresAccessControl.
 */
trait AccessControlContext:

  private case class State(
    global: Map[UserId, GlobalRole],
    contextual: Map[(UserId, EntityId[?]), ContextualRole]
  ):
    def +++(userId: UserId, role: GlobalRole): State = copy(global = global + (userId -> role))

    def getGlobalRole(userId: UserId): Option[Role.Global] = global.get(userId).map(Role.Global(_))

    def ++(userId: UserId, entityId: EntityId[?], role: ContextualRole): State =
      copy(contextual = contextual + ((userId, entityId) -> role))

    def --(userId: UserId, entityId: EntityId[?]): State = copy(contextual = contextual - (userId -> entityId))

    def getContextualRole(userId: UserId, entityId: EntityId[?]): Option[Role.Contextual] =
      contextual.get(userId -> entityId).map(Role.Contextual(_))

  private object State:
    val empty = State(Map.empty, Map.empty)

  def makeAccessControl(): IO[AccessControl[IO, IO] & InspectAccessControl] = makeAccessControl(State.empty)

  private def makeAccessControl(state: State): IO[AccessControl[IO, IO] & InspectAccessControl] =
    IO.ref(state).map { ref =>
      new AccessControl[IO, IO] with InspectAccessControl:

        override def getGlobalRole(userId: UserId): IO[Option[GlobalRole]] =
          ref.get.map(_.getGlobalRole(userId).map(_.designation))

        override def storeGlobalGrant(userId: UserId, role: GlobalRole): IO[Unit] =
          ref.update(_ +++ (userId, role))

        override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): IO[Unit] =
          ref.update(_ ++ (userId, entityId, role))

        override def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[IO, EntityId[T]] =
          fs2.Stream.evals(
            ref.get.map(
              _.contextual
                .collect {
                  case ((u -> id), _) if u == userId => id.asInstanceOf[EntityId[T]]
                }
                .toSeq
            )
          )

        override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Option[Role]] =
          // miminics the behaviour of fetchRole in PostgresAccessControl
          // where the least permissive contextual role take precedence over global roles
          ref.get.map(state => state.getContextualRole(userId, entityId).orElse(state.getGlobalRole(userId)))

        override def deleteContextualGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Unit] =
          ref.update(_ -- (userId, entityId))

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action
    }

// This doesn't look very nice and should be temporary
trait InspectAccessControl:
  def getGlobalRole(userId: UserId): IO[Option[GlobalRole]]

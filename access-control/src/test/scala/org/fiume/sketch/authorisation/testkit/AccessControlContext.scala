package org.fiume.sketch.authorisation.testkit

import cats.effect.{IO, Ref}
import org.fiume.sketch.authorisation.{AccessControl, ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.auth0.UserId

import java.util.UUID

/*
 * Note that this is a > simplified < version of AccessControlContext that provides support
 * for a single entity type.
 * In a real-world scenario, there is support for multiple entity types.
 * See, for example, PostgresAccessControl.
 */
trait AccessControlContext:

  private case class State(global: Map[UserId, GlobalRole], contextual: Map[UserId, (UUID, ContextualRole)]):
    def +++(userId: UserId, role: GlobalRole): State = copy(global = global + (userId -> role))

    def getGlobalRole(userId: UserId): Option[Role.Global] = global.get(userId).map(Role.Global(_))

    def ++(userId: UserId, entityId: UUID, role: ContextualRole): State =
      copy(contextual = contextual + (userId -> (entityId, role)))

    def --(userId: UserId): State = copy(contextual = contextual - userId)

    // Improve this and key it by userId and entityId?
    def getContextualRole(userId: UserId): Option[Role.Contextual] = contextual.get(userId).map(_._2).map(Role.Contextual(_))

  private object State:
    val empty = State(Map.empty, Map.empty)

  def makeAccessControl(): IO[AccessControl[IO, IO]] = makeAccessControl(State.empty)

  private def makeAccessControl[T <: Entity](state: State): IO[AccessControl[IO, IO]] =
    Ref.of[IO, State](state).map { ref =>
      new AccessControl[IO, IO]:

        override def canAccessGlobal(userId: UserId): IO[Boolean] = ref.get.map(_.getGlobalRole(userId).isDefined)

        override def storeGlobalGrant(userId: UserId, role: GlobalRole): IO[Unit] =
          ref.update(_ +++ (userId, role))

        override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): IO[Unit] =
          ref.update(_ ++ (userId, entityId.value, role))

        // TODO This is a simplified version of fetchAllAuthorisedEntityIds that only supports a single entity type
        override def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): fs2.Stream[IO, EntityId[T]] =
          fs2.Stream.evals(
            ref.get.map(
              _.contextual
                .collect {
                  case (u, (id, _)) if u == userId => EntityId[T](id)
                }
                .toSeq
            )
          )

        override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Option[Role]] =
          ref.get.map(_.getContextualRole(userId))

        override def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Unit] =
          ref.update(_ -- userId)

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action
    }

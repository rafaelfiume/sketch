package org.fiume.sketch.authorisation.testkit

import cats.effect.{IO, Ref}
import org.fiume.sketch.authorisation.{AccessControl, Role}
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

  def makeAccessControl(): IO[AccessControl[IO, IO]] = makeAccessControll(state = Map.empty)

  def makeAccessControll[T <: Entity](state: (UserId, (UUID, Role))*): IO[AccessControl[IO, IO]] =
    makeAccessControll(Map.from(state))

  private def makeAccessControll[T <: Entity](state: Map[UserId, (UUID, Role)]): IO[AccessControl[IO, IO]] =
    Ref.of[IO, Map[UserId, (UUID, Role)]](state).map { ref =>
      new AccessControl[IO, IO]:
        override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: Role): IO[Unit] =
          ref.update(_ + (userId -> (entityId.value, role)))

        override def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId): fs2.Stream[IO, EntityId[T]] =
          fs2.Stream.evals(
            ref.get.map(_.collect {
              case (u, (id, _)) if u == userId => EntityId[T](id)
            }.toSeq)
          )

        override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Option[Role]] =
          ref.get.map(_.get(userId).map(_._2))

        override def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): IO[Unit] =
          ref.update(_ - userId)

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action
    }

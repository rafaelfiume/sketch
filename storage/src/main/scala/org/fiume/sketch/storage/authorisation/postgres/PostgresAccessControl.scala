package org.fiume.sketch.storage.authorisation.postgres

import cats.{~>, effect}
import cats.effect.kernel.Async
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.fiume.sketch.authorisation.{AccessControl, Role}
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.authorisation.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

object PostgresAccessControl:
  def make[F[_]: Async](tx: Transactor[F]): effect.Resource[F, PostgresAccessControl[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => PostgresAccessControl(l, tx))

private class PostgresAccessControl[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with AccessControl[F, ConnectionIO]:

  override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: Role): ConnectionIO[Unit] =
    Statements.insertGrant(userId, entityId, entityId.entityType, role).run.void

  override inline def fetchAllAuthorisedEntityIds[T <: Entity](userId: UserId): fs2.Stream[ConnectionIO, EntityId[T]] =
    ${ Macros.fetchAllAuthorisedEntityIdsMacro[T]('userId) }

  override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): ConnectionIO[Option[Role]] =
    Statements.selectRole(userId, entityId).option

  override def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): ConnectionIO[Unit] =
    Statements.deleteGrant[T](userId, entityId).run.void

private object Statements:
  def insertGrant[T <: Entity](userId: UserId, entityId: EntityId[T], entityType: String, role: Role): Update0 =
    sql"""
         |INSERT INTO auth.access_control (
         |  user_id,
         |  entity_id,
         |  entity_type,
         |  role
         |) VALUES (
         |  $userId,
         |  $entityId,
         |  $entityType,
         |  $role
         |)
    """.stripMargin.update

  def selectAlldEntityIds[T <: Entity](userId: UserId, entityType: String): Query0[EntityId[T]] =
    sql"""
         |SELECT
         |  entity_id
         |FROM auth.access_control
         |WHERE user_id = $userId AND entity_type = ${entityType}
    """.stripMargin.query

  def selectRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Query0[Role] =
    sql"""
         |SELECT
         |  role
         |FROM auth.access_control
         |WHERE user_id = $userId AND entity_id = $entityId
    """.stripMargin.query

  def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Update0 =
    sql"""
         |DELETE FROM auth.access_control
         |WHERE user_id = $userId AND entity_id = $entityId
    """.stripMargin.update

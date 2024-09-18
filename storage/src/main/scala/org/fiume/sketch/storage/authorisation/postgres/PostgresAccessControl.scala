package org.fiume.sketch.storage.authorisation.postgres

import cats.{~>, effect}
import cats.effect.kernel.Async
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.fiume.sketch.authorisation.{AccessControl, ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.authorisation.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.AbstractPostgresStore
import org.slf4j.LoggerFactory

object PostgresAccessControl:
  def make[F[_]: Async](tx: Transactor[F]): effect.Resource[F, PostgresAccessControl[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => PostgresAccessControl(l, tx))

private class PostgresAccessControl[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with AccessControl[F, ConnectionIO]:

  override def storeGlobalGrant(userId: UserId, role: GlobalRole): ConnectionIO[Unit] =
    Statements.insertGlobalGrant(userId, role).run.void

  override def storeGrant[T <: Entity](userId: UserId, entityId: EntityId[T], role: ContextualRole): ConnectionIO[Unit] =
    Statements.insertGrant(userId, entityId, entityId.entityType, role).run.void

  override def fetchAllAuthorisedEntityIds[T <: Entity](
    userId: UserId,
    entityType: String
  ): fs2.Stream[ConnectionIO, EntityId[T]] =
    Statements.selectAllAuthorisedEntityIds(userId, entityType).stream

  override def fetchRole[T <: Entity](userId: UserId, entityId: EntityId[T]): ConnectionIO[Option[Role]] =
    Statements.selectRole(userId, entityId).option

  override def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): ConnectionIO[Unit] =
    Statements.deleteGrant[T](userId, entityId).run.void

private object Statements:
  private val logger = LoggerFactory.getLogger(Statements.getClass)

  def insertGlobalGrant(userId: UserId, role: GlobalRole): Update0 =
    sql"""
         |INSERT INTO auth.global_access_control (
         |  user_id,
         |  role
         |) VALUES (
         |  $userId,
         |  $role
         |)
    """.stripMargin.update

  def insertGrant[T <: Entity](userId: UserId, entityId: EntityId[T], entityType: String, role: ContextualRole): Update0 =
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

  def selectAllAuthorisedEntityIds[T <: Entity](userId: UserId, entityType: String): Query0[EntityId[T]] =
    logger.debug(s"Fetching all authorised entity ids for user $userId and entity type $entityType")
    // using common table expression (cte)
    sql"""
         |WITH global_access_role AS (
         |  SELECT role
         |  FROM auth.global_access_control
         |  WHERE user_id = $userId
         |)
         |
         |SELECT entity_id
         |FROM auth.access_control
         |WHERE entity_type = $entityType
         |AND (
         |  (SELECT role FROM global_access_role) = 'Admin'
         |
         |  OR (SELECT role FROM global_access_role) = 'Superuser' AND entity_type != 'UserEntity'
         |
         |  OR (user_id = $userId)
         |)
    """.stripMargin.query

  def selectRole[T <: Entity](userId: UserId, entityId: EntityId[T]): Query0[Role] =
    // Contextual roles take precedence over global
    sql"""
         |SELECT access_role FROM (
         |  SELECT role as access_role, 1 as priority
         |  FROM auth.access_control ac
         |  WHERE ac.user_id = $userId AND ac.entity_id = $entityId
         |
         |  UNION ALL
         |
         |  SELECT role as access_role, 2 as priority
         |  FROM auth.global_access_control gac
         |  WHERE gac.user_id = $userId
         |) as roles
         |ORDER BY priority ASC
         |LIMIT 1
    """.stripMargin.query

  def deleteGrant[T <: Entity](userId: UserId, entityId: EntityId[T]): Update0 =
    sql"""
         |DELETE FROM auth.access_control
         |WHERE user_id = $userId AND entity_id = $entityId
    """.stripMargin.update

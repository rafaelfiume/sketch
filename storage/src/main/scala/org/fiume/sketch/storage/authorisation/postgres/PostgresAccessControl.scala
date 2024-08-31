package org.fiume.sketch.storage.authorisation.postgres

import cats.{~>, effect}
import cats.effect.kernel.Async
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.fiume.sketch.authorisation.{AccessControl, Role}
import org.fiume.sketch.shared.app.{Resource, ResourceId}
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

  override def storeGrant[T <: Resource](userId: UserId, resourceId: ResourceId[T], role: Role): ConnectionIO[Unit] =
    Statements.insertGrant(userId, resourceId, resourceId.resourceType, role).run.void

  override inline def fetchAllAuthorisedResourceIds[T <: Resource](userId: UserId): fs2.Stream[ConnectionIO, ResourceId[T]] =
    ${ Macros.fetchAllAuthorisedResourceIdsMacro[T]('userId) }

  override def fetchRole[T <: Resource](userId: UserId, resourceId: ResourceId[T]): ConnectionIO[Option[Role]] =
    Statements.selectRole(userId, resourceId).option

  override def deleteGrant[T <: Resource](userId: UserId, resourceId: ResourceId[T]): ConnectionIO[Unit] =
    Statements.deleteGrant[T](userId, resourceId).run.void

private object Statements:
  def insertGrant[T <: Resource](userId: UserId, resourceId: ResourceId[T], resourceType: String, role: Role): Update0 =
    sql"""
         |INSERT INTO auth.access_control (
         |  user_id,
         |  resource_id,
         |  resource_type,
         |  role
         |) VALUES (
         |  $userId,
         |  $resourceId,
         |  $resourceType,
         |  $role
         |)
    """.stripMargin.update

  // Experiment with macros to generate the resource type name from the type parameter

  def selectAlldResourceIds[T <: Resource](userId: UserId, resourceType: String): Query0[ResourceId[T]] =
    // println(s"resourceType: ${resourceType.getClass().getSimpleName().filterNot(_ == '$')}")
    sql"""
         |SELECT
         |  resource_id
         |FROM auth.access_control
         |WHERE user_id = $userId AND resource_type = ${resourceType}
    """.stripMargin.query

  def selectRole[T <: Resource](userId: UserId, resourceId: ResourceId[T]): Query0[Role] =
    sql"""
         |SELECT
         |  role
         |FROM auth.access_control
         |WHERE user_id = $userId AND resource_id = $resourceId
    """.stripMargin.query

  def deleteGrant[T <: Resource](userId: UserId, resourceId: ResourceId[T]): Update0 =
    sql"""
         |DELETE FROM auth.access_control
         |WHERE user_id = $userId AND resource_id = $resourceId
    """.stripMargin.update

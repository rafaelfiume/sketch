package org.fiume.sketch.storage.authorisation.postgres

import doobie.*
import doobie.implicits.*
import org.fiume.sketch.shared.app.{Meta as TypeMeta, Resource, ResourceId}
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.authorisation.postgres.DoobieMappings.given

import scala.quoted.*

object Macros:
  def fetchAllAuthorisedResourceIdsMacro[T <: Resource: Type](
    userId: Expr[UserId]
  )(using Quotes): Expr[fs2.Stream[ConnectionIO, ResourceId[T]]] =
    '{
      selectAlldResourceIds[T]($userId, TypeMeta.typeName[T]).stream
    }

  private def selectAlldResourceIds[T <: Resource](userId: UserId, resourceType: String): Query0[ResourceId[T]] =
    sql"""
         |SELECT
         |  resource_id
         |FROM auth.access_control
         |WHERE user_id = $userId AND resource_type = ${resourceType}
    """.stripMargin.query

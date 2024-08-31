package org.fiume.sketch.storage.authorisation.postgres

import doobie.*
import doobie.implicits.*
import org.fiume.sketch.shared.app.{Entity, EntityId, Macros as TypeMeta}
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.authorisation.postgres.DoobieMappings.given

import scala.quoted.*

object Macros:
  def fetchAllAuthorisedEntityIdsMacro[T <: Entity: Type](
    userId: Expr[UserId]
  )(using Quotes): Expr[fs2.Stream[ConnectionIO, EntityId[T]]] =
    '{
      selectAlldEntityIds[T]($userId, TypeMeta.typeName[T]).stream
    }

  private def selectAlldEntityIds[T <: Entity](userId: UserId, entityType: String): Query0[EntityId[T]] =
    sql"""
         |SELECT
         |  entity_id
         |FROM auth.access_control
         |WHERE user_id = $userId AND entity_type = ${entityType}
    """.stripMargin.query

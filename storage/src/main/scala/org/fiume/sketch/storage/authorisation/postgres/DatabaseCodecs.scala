package org.fiume.sketch.storage.authorisation.postgres

import cats.implicits.*
import doobie.{Meta, Read}
import doobie.postgres.implicits.*
import doobie.util.Write
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.authorisation.{ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.authorisation.Role.given

import java.util.UUID

private[storage] object DatabaseCodecs:

  given Meta[GlobalRole] = Meta[String].timap(GlobalRole.valueOf(_))(_.toString)

  given Meta[ContextualRole] = Meta[String].timap(ContextualRole.valueOf(_))(_.toString)

  given Meta[Role] = Meta[String].tiemap(_.parsed().leftMap(_.message))(_.asString())

  // TODO Move it to shared app package?
  given write[T <: Entity]: Write[EntityId[T]] = Write[UUID].contramap(_.value)

  import org.fiume.sketch.shared.domain.documents.DocumentId
  import org.fiume.sketch.shared.auth.domain.UserId
  given read[T <: Entity]: Read[EntityId[T]] = Read[(UUID, String)].map { case (uuid, entityType) =>
    entityType match
      case "DocumentEntity" => DocumentId(uuid).asInstanceOf[EntityId[T]]
      case "UserEntity"     => UserId(uuid).asInstanceOf[EntityId[T]]
  }

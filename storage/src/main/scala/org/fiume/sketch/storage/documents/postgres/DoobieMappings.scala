package org.fiume.sketch.storage.documents.postgres

import doobie.Meta
import doobie.postgres.implicits.*
import doobie.util.Read
import fs2.Stream
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithId}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*

import java.util.UUID

private[storage] object DoobieMappings:
  given Meta[DocumentId] = Meta[UUID].timap(DocumentId(_))(_.value)
  given Meta[Name] = Meta[String].timap(Name.notValidatedFromString)(_.value)
  given Meta[Description] = Meta[String].timap(Description.apply)(_.value)

  given readDocumentWithId[F[_]]: Read[DocumentWithId[F]] =
    Read[(DocumentId, Name, Description, UserId, UserId, Array[Byte])].map {
      case (uuid, name, description, createdBy, ownedBy, content) =>
        Document.withUuid(uuid, Metadata(name, description, createdBy, ownedBy), Stream.emits(content))
    }

package org.fiume.sketch.storage.documents.postgres

import doobie.Meta
import doobie.postgres.implicits.*
import doobie.util.Read
import fs2.Stream
import org.fiume.sketch.storage.documents.{Document, DocumentUuid, DocumentWithUuid}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*

import java.util.UUID

private[documents] object DoobieMappings:
  given Meta[DocumentUuid] = Meta[UUID].timap(DocumentUuid(_))(_.value)
  given Meta[Name] = Meta[String].timap(Name.notValidatedFromString)(_.value)
  given Meta[Description] = Meta[String].timap(Description.apply)(_.value)

  given readDocumentWithId[F[_]]: Read[DocumentWithUuid[F]] = Read[(DocumentUuid, Name, Description, Array[Byte])].map {
    case (uuid, name, description, content) => Document.withUuid(uuid, Metadata(name, description), Stream.emits(content))
  }

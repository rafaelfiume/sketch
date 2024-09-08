package org.fiume.sketch.storage.documents.postgres

import doobie.Meta
import doobie.postgres.implicits.*
import doobie.util.Read
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*

import java.util.UUID

private[storage] object DoobieMappings:
  given Meta[DocumentId] = Meta[UUID].timap(DocumentId(_))(_.value)
  given Meta[Name] = Meta[String].timap(Name.notValidatedFromString)(_.value)
  given Meta[Description] = Meta[String].timap(Description.apply)(_.value)

  given readDocumentWithId: Read[DocumentWithId] =
    Read[(DocumentId, Name, Description)].map { case (uuid, name, description) =>
      Document.make(uuid, Metadata(name, description))
    }

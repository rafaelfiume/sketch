package org.fiume.sketch.storage.documents.postgres

import doobie.Meta
import doobie.free.connection.ConnectionIO
import doobie.postgres.implicits.*
import doobie.util.Read
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given

import java.util.UUID

private[storage] object DatabaseCodecs:
  given Meta[DocumentId] = Meta[UUID].timap(DocumentId(_))(_.value)
  given Meta[Name] = Meta[String].timap(Name.makeUnsafeFromString)(_.value)
  given Meta[Description] = Meta[String].timap(Description.apply)(_.value)

  given readDocumentWithId: Read[DocumentWithId] =
    Read[(DocumentId, Name, Description, UserId)].map { case (uuid, name, description, ownerId) =>
      Document.make(uuid, Metadata(name, description, ownerId))
    }

  given Read[fs2.Stream[ConnectionIO, Byte]] = Read[Array[Byte]].map(bytes => fs2.Stream.chunk(fs2.Chunk.array(bytes)))

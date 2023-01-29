package org.fiume.sketch.datastore.postgres

import doobie.Meta
import org.fiume.sketch.domain.Document

private[postgres] object DoobieMappings:
  given Meta[Document.Metadata.Name] = Meta[String].timap(Document.Metadata.Name.apply)(_.value)
  given Meta[Document.Metadata.Description] = Meta[String].timap(Document.Metadata.Description.apply)(_.value)

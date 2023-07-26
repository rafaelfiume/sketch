package org.fiume.sketch.storage.documents.postgres

import doobie.Meta
import org.fiume.sketch.storage.documents.Document.Metadata

private[documents] object DoobieMappings:
  given Meta[Metadata.Name] = Meta[String].timap(Metadata.Name.apply)(_.value)
  given Meta[Metadata.Description] = Meta[String].timap(Metadata.Description.apply)(_.value)

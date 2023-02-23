package org.fiume.sketch.datastore.postgres

import doobie.Meta
import org.fiume.sketch.domain.documents.Metadata

private[postgres] object DoobieMappings:
  given Meta[Metadata.Name] = Meta[String].timap(Metadata.Name.apply)(_.value)
  given Meta[Metadata.Description] = Meta[String].timap(Metadata.Description.apply)(_.value)

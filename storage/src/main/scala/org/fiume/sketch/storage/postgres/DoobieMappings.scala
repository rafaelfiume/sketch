package org.fiume.sketch.storage.postgres

import doobie.Meta
import org.fiume.sketch.shared.domain.documents.Metadata

private[postgres] object DoobieMappings:
  given Meta[Metadata.Name] = Meta[String].timap(Metadata.Name.apply)(_.value)
  given Meta[Metadata.Description] = Meta[String].timap(Metadata.Description.apply)(_.value)

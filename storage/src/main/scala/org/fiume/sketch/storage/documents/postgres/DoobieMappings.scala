package org.fiume.sketch.storage.documents.postgres

import doobie.Meta
import org.fiume.sketch.storage.documents.Document.Metadata.*

private[documents] object DoobieMappings:
  given Meta[Name] = Meta[String].timap(Name.notValidatedFromString)(_.value)
  given Meta[Description] = Meta[String].timap(Description.apply)(_.value)

package org.fiume.sketch.datastore.postgres

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.datastore.support.DockerPostgresSuite
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.ClockContext
import org.fiume.sketch.support.Gens.Bytes.*
import org.fiume.sketch.support.Gens.Strings.*
import org.scalacheck.Gen
import org.scalacheck.Shrink
import org.scalacheck.effect.PropF.forAllF

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class PostgresStoreSpec extends DockerPostgresSuite with ScalaCheckEffectSuite with PostgresStoreSpecContext:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  test("store and fetch document metadata") {
    forAllF(documents) { document =>
      will(cleanDocuments) {
        PostgresStore.make[IO](clock, transactor()).use { store =>
          for
            _ <- store.commit { store.store(document) }

            result <- store.commit {
              store.fetchMetadata(document.metadata.name)
            }

            _ <- IO { assertEquals(result, document.metadata) }
          yield ()
        }
      }
    }
  }

  test("store and fetch document bytes") {
    forAllF(documents) { document =>
      will(cleanDocuments) {
        PostgresStore.make[IO](clock, transactor()).use { store =>
          for
            _ <- store.commit { store.store(document) }

            stream <- store.commit {
              store.fetchBytes(document.metadata.name)
            }
            result <- stream.compile.toList

            _ <- IO { assertEquals(result, document.bytes.toList) }
          yield ()
        }
      }
    }
  }

  // TODO Check with a real document (e.g. pdf)

  // TODO Check updated_at_utc is being updated

trait PostgresStoreSpecContext extends ClockContext:

  val clock = {
    val frozen = ZonedDateTime.of(2021, 12, 3, 10, 11, 12, 0, ZoneOffset.UTC)
    makeFrozenTime(frozen)
  }

  def documents: Gen[Document] =
    def metadataG: Gen[Document.Metadata] =
      for
        name <- alphaNumString.map(Document.Metadata.Name.apply)
        description <- alphaNumString.map(Document.Metadata.Description.apply)
      yield Document.Metadata(name, description)

    def bytesG: Gen[Array[Byte]] = Gen.nonEmptyListOf(bytes).map(_.toArray)

    for
      metadata <- metadataG
      bytes <- bytesG
    yield Document(metadata, bytes)

  def cleanDocuments: ConnectionIO[Unit] = sql"DELETE FROM documents".update.run.void

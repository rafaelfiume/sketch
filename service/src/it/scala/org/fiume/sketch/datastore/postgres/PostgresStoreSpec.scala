package org.fiume.sketch.datastore.postgres

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.datastore.postgres.DoobieMappings.given
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.datastore.support.DockerPostgresSuite
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.ClockContext
import org.fiume.sketch.support.Gens.Bytes.*
import org.fiume.sketch.support.Gens.Strings.*
import org.scalacheck.{Gen, Shrink}
import org.scalacheck.effect.PropF.forAllF

import java.time.{Instant, ZoneOffset, ZonedDateTime}
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

            result <- store.commit {
              store.fetchBytes(document.metadata.name)
            }.flatMap(_.compile.toList)

            _ <- IO { assertEquals(result, document.bytes.toList) }
          yield ()
        }
      }
    }
  }

  test("update document") {
    forAllF(documents, descriptions, bytesG) { (document, updatedDescription, updatedBytes) =>
      PostgresStore.make[IO](clock, transactor()).use { store =>
        for
          _ <- store.commit { store.store(document) }

          // TODO Replace copy by lenses
          updatedDoc = document.copy(metadata = document.metadata.copy(description = updatedDescription), bytes = updatedBytes)
          _ <- store.commit { store.store(updatedDoc) }

          metadata <- store.commit { store.fetchMetadata(document.metadata.name) }
          bytes <- store.commit { store.fetchBytes(document.metadata.name) }.flatMap(_.compile.toList)
          _ <- IO {
            assertEquals(metadata, updatedDoc.metadata)
            assertEquals(bytes, updatedDoc.bytes.toList)
          }
        yield ()
      }
    }
  }

  // TODO Check with a real document (e.g. pdf)

  test("updated document -> more recent updated_at_utc") {
    forAllF(documents, descriptions, bytesG) { (document, updatedDescription, updatedBytes) =>
      PostgresStore.make[IO](clock, transactor()).use { store =>
        for
          _ <- store.commit { store.store(document) }
          updatedAt1 <- store.commit { fetchUpdatedAt(document.metadata.name) }

          // TODO Replace copy by lenses
          updatedDoc = document.copy(metadata = document.metadata.copy(description = updatedDescription), bytes = updatedBytes)
          _ <- store.commit { store.store(updatedDoc) }

          updatedAt2 <- store.commit { fetchUpdatedAt(document.metadata.name) }
          _ <- IO {
            assert(updatedAt2.isAfter(updatedAt1), "updatedAt should be updated")
          }
        yield ()
      }
    }
  }

trait PostgresStoreSpecContext extends ClockContext:

  val clock = {
    val frozen = ZonedDateTime.of(2021, 12, 3, 10, 11, 12, 0, ZoneOffset.UTC)
    makeFrozenTime(frozen)
  }

  def descriptions: Gen[Document.Metadata.Description] = alphaNumString.map(Document.Metadata.Description.apply)

  def bytesG: Gen[Array[Byte]] = Gen.nonEmptyListOf(bytes).map(_.toArray)

  def documents: Gen[Document] =
    def metadataG: Gen[Document.Metadata] =
      for
        name <- alphaNumString.map(Document.Metadata.Name.apply)
        description <- descriptions
      yield Document.Metadata(name, description)

    for
      metadata <- metadataG
      bytes <- bytesG
    yield Document(metadata, bytes)

  def cleanDocuments: ConnectionIO[Unit] = sql"DELETE FROM documents".update.run.void

  def fetchUpdatedAt(name: Document.Metadata.Name): ConnectionIO[Instant] =
    sql"SELECT updated_at_utc FROM documents WHERE name = ${name}"
      .query[Instant]
      .unique

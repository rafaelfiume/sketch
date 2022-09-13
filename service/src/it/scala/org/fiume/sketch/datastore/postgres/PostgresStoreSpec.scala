package org.fiume.sketch.datastore.postgres

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import monocle.syntax.all.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.datastore.postgres.DoobieMappings.given
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.datastore.support.DockerPostgresSuite
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.FileContentContext
import org.fiume.sketch.support.gens.SketchGens.Documents.*
import org.scalacheck.Shrink
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import scala.concurrent.duration.*

class PostgresStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with FileContentContext
    with PostgresStoreSpecContext:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  test("store and fetch documents metadata") {
    forAllF(documents, documents) { (fst, snd) =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.commit { store.store(fst) }
            _ <- store.commit { store.store(snd) }

            fstResult <- store.commit {
              store.fetchMetadata(fst.metadata.name)
            }
            sndResult <- store.commit {
              store.fetchMetadata(snd.metadata.name)
            }

            _ <- IO { assertEquals(fstResult, fst.metadata) }
            _ <- IO { assertEquals(sndResult, snd.metadata) }
          yield ()
        }
      }
    }
  }

  test("store and fetch document bytes") {
    forAllF(documents) { document =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.commit { store.store(document) }

            result <- store
              .commit {
                store.fetchBytes(document.metadata.name)
              }
              .flatMap(_.compile.toList)

            _ <- IO { assertEquals(result, document.bytes.toList) }
          yield ()
        }
      }
    }
  }

  test("update document") {
    forAllF(documents, descriptions, bytesG) { (document, updatedDescription, updatedBytes) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(document) }

          updatedDoc = document.withDescription(updatedDescription).withBytes(updatedBytes)
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

  test("updated document -> more recent updated_at_utc") {
    forAllF(documents, descriptions, bytesG) { (document, updatedDescription, updatedBytes) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(document) }
          updatedAt1 <- store.commit { fetchUpdatedAt(document.metadata.name) }

          updatedDoc = document.withDescription(updatedDescription).withBytes(updatedBytes)
          _ <- store.commit { store.store(updatedDoc) }

          updatedAt2 <- store.commit { fetchUpdatedAt(document.metadata.name) }
          _ <- IO {
            assert(updatedAt2.isAfter(updatedAt1), "updatedAt should be updated")
          }
        yield ()
      }
    }
  }

  test("store jpg image") {
    bytesFrom[IO]("mountain-bike-liguria-ponent.jpg").compile.toVector.map(_.toArray).flatMap { bytes =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          val document = documents.sample.get.withBytes(bytes)
          for
            _ <- store.commit { store.store(document) }

            result <- store
              .commit {
                store.fetchBytes(document.metadata.name)
              }
              .flatMap(_.compile.toList)

            _ <- IO { assertEquals(result, document.bytes.toList) }
          yield ()
        }
      }
    }
  }

  test("play it".ignore) { // good to see it in action
    val filename = "mountain-bike-liguria-ponent.jpg"
    bytesFrom[IO](filename).compile.toVector.map(_.toArray).flatMap { bytes =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          val document = documents.sample.get.withBytes(bytes)
          for
            _ <- store.commit { store.store(document) }
            result <- store
              .commit {
                store.fetchBytes(document.metadata.name)
              }
              .flatMap { _.through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename))).compile.drain }
          yield ()
        }
      }
    }
  }

trait PostgresStoreSpecContext:

  /*
   * Queries
   */

  def cleanDocuments: ConnectionIO[Unit] = sql"DELETE FROM documents".update.run.void

  def fetchUpdatedAt(name: Document.Metadata.Name): ConnectionIO[Instant] =
    sql"SELECT updated_at_utc FROM documents WHERE name = ${name}"
      .query[Instant]
      .unique

  /*
   * Lenses
   */
  extension (doc: Document)
    def withDescription(description: Document.Metadata.Description): Document =
      doc.focus(_.metadata.description).replace(description)

    def withBytes(bytes: Array[Byte]): Document =
      doc.focus(_.bytes).replace(bytes)

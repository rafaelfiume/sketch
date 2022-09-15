package org.fiume.sketch.datastore.postgres

import cats.data.OptionT
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

  //override def scalaCheckInitialSeed = "DCHaHgKmD4XmEOKVUE1Grw8K2uWlohHvD-5gMuoh2pE="

  test("store and fetch documents metadata") {
    forAllF(documents, documents) { (fst, snd) =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.commit { store.store(fst) }
            fstResult <- store.commit { store.fetchMetadata(fst.metadata.name) }
            _ <- store.commit { store.store(snd) }
            sndResult <- store.commit { store.fetchMetadata(snd.metadata.name) }

            _ <- IO { assertEquals(fstResult, fst.metadata.some) }
            _ <- IO { assertEquals(sndResult, snd.metadata.some) }
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

            result <- OptionT(
              store.commit {
                store.fetchBytes(document.metadata.name)
              }
            ).semiflatMap(_.compile.toList).value

            _ <- IO { assertEquals(result, document.bytes.toList.some) }
          yield ()
        }
      }
    }
  }

  test("update document metadata") {
    forAllF(documents, descriptions) { (document, updatedDescription) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(document) }

          updatedDoc = document.withDescription(updatedDescription)
          _ <- store.commit { store.store(updatedDoc) }

          result <- store.commit { store.fetchMetadata(document.metadata.name) }
          _ <- IO {
            assertEquals(result, updatedDoc.metadata.some)
          }
        yield ()
      }
    }
  }

  test("update document bytes") {
    forAllF(documents, bytesG) { (document, updatedBytes) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(document) }

          updatedDoc = document.withBytes(updatedBytes)
          _ <- store.commit { store.store(updatedDoc) }

          result <- OptionT(
            store.commit {
              store.fetchBytes(document.metadata.name)
            }
          ).semiflatMap(_.compile.toList).value
          _ <- IO {
            assertEquals(result, updatedDoc.bytes.toList.some)
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

            result <- OptionT(
              store.commit {
                store.fetchBytes(document.metadata.name)
              }
            ).semiflatMap(_.compile.toList).value

            _ <- IO { assertEquals(result, document.bytes.toList.some) }
          yield ()
        }
      }
    }
  }

  test("no documents") {
    forAllF(names) { (name) =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for

            metadata <- store.commit { store.fetchMetadata(name) }
            bytes <- store.commit { store.fetchBytes(name) }

            _ <- IO { assertEquals(metadata, none) }
            _ <- IO { assertEquals(bytes, none) }
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
            result <- OptionT(
              store.commit {
                store.fetchBytes(document.metadata.name)
              }
            ).semiflatMap { _.through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename))).compile.drain }.value
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

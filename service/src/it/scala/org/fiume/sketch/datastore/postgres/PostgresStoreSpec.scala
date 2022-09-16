package org.fiume.sketch.datastore.postgres

import cats.data.OptionT
import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
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

  // override def scalaCheckInitialSeed = "DCHaHgKmD4XmEOKVUE1Grw8K2uWlohHvD-5gMuoh2pE="

  test("store and fetch documents metadata") {
    forAllF(documents[IO], documents[IO]) { (fst, snd) =>
      // TODO Wait till PropF.forAllF supports '==>' (scalacheck implication)
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.commit { store.store(fst) }
            fstResult <- store.commit { store.fetchMetadata(fst.metadata.name) }
            _ <- store.commit { store.store(snd) }
            sndResult <- store.commit { store.fetchMetadata(snd.metadata.name) }

            _ <- IO {
              assertEquals(fstResult, fst.metadata.some)
              assertEquals(sndResult, snd.metadata.some)
            }
          yield ()
        }
      }
    }
  }

  test("store and fetch document bytes") {
    forAllF(documents[IO]) { document =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.commit { store.store(document) }

            result <- OptionT(
              store.commit {
                store.fetchBytes(document.metadata.name)
              }
            ).semiflatMap(_.compile.toList).value

            originalBytes <- document.bytes.compile.toList
            _ <- IO {
              assertEquals(result, originalBytes.some)
            }
          yield ()
        }
      }
    }
  }

  test("update document metadata") {
    forAllF(documents[IO], descriptions) { (original, updatedDescription) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(original) }

          updated = original.withDescription(updatedDescription)
          _ <- store.commit { store.store(updated) }

          result <- store.commit { store.fetchMetadata(original.metadata.name) }
          _ <- IO {
            assertEquals(result, updated.metadata.some)
          }
        yield ()
      }
    }
  }

  test("update document bytes") {
    forAllF(documents[IO], bytesG[IO]) { (original, updatedBytes) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(original) }

          updated = original.withBytes(updatedBytes)
          _ <- store.commit { store.store(updated) }

          result <- OptionT(
            store.commit {
              store.fetchBytes(original.metadata.name)
            }
          ).semiflatMap(_.compile.toList).value
          originalBytes <- updated.bytes.compile.toList
          _ <- IO {
            assertEquals(result, originalBytes.some)
          }
        yield ()
      }
    }
  }

  test("updated document -> more recent updated_at_utc") {
    forAllF(documents[IO], descriptions, bytesG[IO]) { (original, updatedDescription, updatedBytes) =>
      PostgresStore.make[IO](transactor()).use { store =>
        for
          _ <- store.commit { store.store(original) }
          updatedAt1 <- store.commit { fetchUpdatedAt(original.metadata.name) }

          updated = original.withDescription(updatedDescription).withBytes(updatedBytes)
          _ <- store.commit { store.store(updated) }

          updatedAt2 <- store.commit { fetchUpdatedAt(original.metadata.name) }
          _ <- IO {
            assert(updatedAt2.isAfter(updatedAt1), "updatedAt should be updated")
          }
        yield ()
      }
    }
  }

  test("store jpg image") {
    IO { bytesFrom[IO]("mountain-bike-liguria-ponent.jpg") }.flatMap { bytes =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          val document = documents[IO].sample.get.withBytes(bytes)
          for
            _ <- store.commit { store.store(document) }

            result <- OptionT(
              store.commit {
                store.fetchBytes(document.metadata.name)
              }
            ).semiflatMap(_.compile.toList).value

            originalBytes <- document.bytes.compile.toList
            _ <- IO {
              assertEquals(result, originalBytes.some)
            }
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

            _ <- IO {
              assertEquals(metadata, none)
              assertEquals(bytes, none)
            }
          yield ()
        }
      }
    }
  }

  test("play it".ignore) { // good to see it in action
    val filename = "mountain-bike-liguria-ponent.jpg"
    IO { bytesFrom[IO](filename) }.flatMap { bytes =>
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          val document = documents[IO].sample.get.withBytes(bytes)
          for
            _ <- store.commit { store.store(document) }
            _ <- OptionT(
              store.commit {
                store.fetchBytes(document.metadata.name)
              }
            ).semiflatMap {
              _.through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(filename))).compile.drain
            }.value
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
  extension [F[_]](doc: Document[F])
    def withDescription(description: Document.Metadata.Description): Document[F] =
      doc.focus(_.metadata.description).replace(description)

    def withBytes(bytes: Stream[F, Byte]): Document[F] =
      doc.focus(_.bytes).replace(bytes)

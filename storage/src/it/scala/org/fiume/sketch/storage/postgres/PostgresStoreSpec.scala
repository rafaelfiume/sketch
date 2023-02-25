package org.fiume.sketch.storage.postgres

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
import org.fiume.sketch.domain.documents.Document
import org.fiume.sketch.domain.documents.Metadata.*
import org.fiume.sketch.storage.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.PostgresStore
import org.fiume.sketch.storage.support.DockerPostgresSuite
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
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(fst).ccommit

            result <- store.fetchMetadata(fst.metadata.name).ccommit

            _ <- IO {
              assertEquals(result, fst.metadata.some)
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
            _ <- store.store(document).ccommit

            result <- OptionT(
              store.fetchBytes(document.metadata.name).ccommit
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
          _ <- store.store(original).ccommit

          updated = original.withDescription(updatedDescription)
          _ <- store.store(updated).ccommit

          result <- store.fetchMetadata(original.metadata.name).ccommit
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
          _ <- store.store(original).ccommit

          updated = original.withBytes(updatedBytes)
          _ <- store.store(updated).ccommit

          result <- OptionT(
            store.fetchBytes(original.metadata.name).ccommit
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
          _ <- store.store(original).ccommit
          updatedAt1 <- store.fetchUpdatedAt(original.metadata.name).ccommit

          updated = original.withDescription(updatedDescription).withBytes(updatedBytes)
          _ <- store.store(updated).ccommit

          updatedAt2 <- store.fetchUpdatedAt(original.metadata.name).ccommit
          _ <- IO {
            assert(updatedAt2.isAfter(updatedAt1), s"updatedAt should be updated: (updatedAt2=$updatedAt2; updatedAt1=$updated)")
          }
        yield ()
      }
    }
  }

  test("delete document") {
    forAllF(documents[IO], documents[IO]) { (fst, snd) =>
      // TODO Wait till PropF.forAllF supports '==>' (scalacheck implication)
      will(cleanDocuments) {
        PostgresStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(fst).ccommit
            _ <- store.store(snd).ccommit

            _ <- store.delete(fst.metadata.name).ccommit

            fstResult <- IO.both(
              store.fetchMetadata(fst.metadata.name).ccommit,
              store.fetchBytes(fst.metadata.name).ccommit
            )
            sndResult <- IO.both(
              store.fetchMetadata(snd.metadata.name).ccommit,
              store.fetchBytes(snd.metadata.name).ccommit
            )
            _ <- IO {
              assertEquals(fstResult._1, none)
              assertEquals(fstResult._2, none)
              assert(sndResult._1.isDefined)
              assert(sndResult._2.isDefined)
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
            _ <- store.store(document).ccommit
            _ <- OptionT(
              store.fetchBytes(document.metadata.name).ccommit
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

  extension (store: PostgresStore[IO])
    def fetchUpdatedAt(name: Name): ConnectionIO[Instant] =
      sql"SELECT updated_at_utc FROM documents WHERE name = ${name}"
        .query[Instant]
        .unique

  /*
   * Lenses
   */
  extension [F[_]](doc: Document[F])
    def withDescription(description: Description): Document[F] =
      doc.focus(_.metadata.description).replace(description)

    def withBytes(bytes: Stream[F, Byte]): Document[F] =
      doc.focus(_.bytes).replace(bytes)

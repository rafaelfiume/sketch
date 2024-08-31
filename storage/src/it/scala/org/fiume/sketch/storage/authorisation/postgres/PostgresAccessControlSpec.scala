package org.fiume.sketch.storage.authorisation.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.authorisation.Role
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UsersStoreContext
import org.fiume.sketch.shared.domain.documents.{DocumentEntity, DocumentWithIdAndStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF
import org.fiume.sketch.shared.domain.documents.DocumentId

class PostgresAccessControlSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with UsersStoreContext
    with PostgresAccessControlSpecContext
    with ShrinkLowPriority:

  test("grants a user permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, role: Role) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.allowAccess(userId, entityId, role).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(result)
        }
      }
    }

  test("grants a user permission to access an entity and then perform the action"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, documentStore) =>
          for
            documentId <- accessControl
              .createEntityThenAllowAccess(userId, role)(documentStore.store(document))
              .ccommit

            result <- accessControl
              .fetchEntityIfAuthorised(userId, documentId)(documentStore.fetchDocument)
              .ccommit
//
          yield assertEquals(result.rightValue, document.some)
        }
      }
    }

  test("does not grant a user permission to access an entity"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, documentStore) =>
          for
            documentId <- documentStore.store(document).ccommit

            result <- accessControl
              .fetchEntityIfAuthorised(userId, documentId)(documentStore.fetchDocument)
              .ccommit
//
          yield assertEquals(result.leftValue, "Unauthorised")
        }
      }
    }

  test("fetches all authorised entity ids"):
    forAllF {
      (fstUserId: UserId,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndserId: UserId,
       trdDocument: DocumentWithIdAndStream[IO],
       role: Role
      ) =>
        will(cleanGrants) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore) =>
            for
              fstDocumentId <- accessControl
                .createEntityThenAllowAccess(fstUserId, role)(documentStore.store(fstDocument))
                .ccommit
              sndDocumentId <- accessControl
                .createEntityThenAllowAccess(fstUserId, role)(documentStore.store(sndDocument))
                .ccommit
              trdDocumentId <- accessControl
                .createEntityThenAllowAccess(sndserId, role)(documentStore.store(trdDocument))
                .ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds[DocumentEntity](fstUserId)
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocumentId, sndDocumentId))
          }
        }
    }

  test("revokes a user's permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, role: Role) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.allowAccess(userId, entityId, role).ccommit

            _ <- accessControl.revokeAccess(userId, entityId).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
          yield assert(!result)
        }
      }
    }

trait PostgresAccessControlSpecContext:
  import org.scalacheck.Gen
  import org.scalacheck.Arbitrary

  def cleanGrants: ConnectionIO[Unit] = sql"TRUNCATE TABLE auth.access_control".update.run.void

  given Arbitrary[Role] = Arbitrary(roles)
  def roles: Gen[Role] = Gen.const(Role.Owner)

package org.fiume.sketch.storage.authorisation.postgres

import cats.effect.{Clock, IO}
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.authorisation.{ContextualRole, GlobalRole, Role}
import org.fiume.sketch.authorisation.ContextualRole.Owner
import org.fiume.sketch.authorisation.GlobalRole.{Admin, Superuser}
import org.fiume.sketch.authorisation.testkit.AccessControlGens.given
import org.fiume.sketch.shared.auth0.User.UserCredentials
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UsersStoreContext
import org.fiume.sketch.shared.domain.documents.{DocumentEntity, DocumentId, DocumentWithIdAndStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class PostgresAccessControlSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with UsersStoreContext
    with PostgresAccessControlSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  /*
   ** Global Role Specs
   */

  test("grants Admin users permission to access all entities"):
    forAllF { (userId: UserId, entityId: DocumentId) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantGlobalAccess(userId, Admin).ccommit

            // note that it is up to the program to make sure that existing entity ids are passed
            grantedAccess <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(grantedAccess)
        }
      }
    }

  test("grants Superuser's permission to access all entities except UserEntity"):
    forAllF(userIds, entities) { (userId, entityId) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantGlobalAccess(userId, Superuser).ccommit

            grantedAccess <- accessControl.canAccess(userId, entityId).ccommit
//
          yield if entityId.entityType =!= "UserEntity" then assert(grantedAccess) else assert(!grantedAccess)
        }
      }
    }

  test("fetches all existent entity ids if user is Admin"):
    forAllF {
      (fstUser: UserCredentials,
       globalRole: GlobalRole,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndUser: UserCredentials,
       trdDocument: DocumentWithIdAndStream[IO]
      ) =>
        will(cleanGrants) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor()),
            PostgresUsersStore.make[IO](transactor(), Clock[IO])
          ).tupled.use { case (accessControl, docStore, usersStore) =>
            for
              fstUserId <- usersStore
                .store(fstUser)
                .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
                .ccommit
              sndUserId <- usersStore
                .store(sndUser)
                .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
                .ccommit
              fstDocId <- accessControl.ensureAccess(fstUserId, Owner) { docStore.store(fstDocument) }.ccommit
              sndDocId <- accessControl.ensureAccess(sndUserId, Owner) { docStore.store(sndDocument) }.ccommit
              trdDocId <- accessControl.ensureAccess(sndUserId, Owner) { docStore.store(trdDocument) }.ccommit
              _ <- accessControl.grantGlobalAccess(fstUserId, globalRole).ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocId, sndDocId, trdDocId))
          }
        }
    }

  // UserEntity is special in that it is extremely sensitive
  test("fetches all existent entity ids, except non-owned UserEntity, if user is Superuser"):
    forAllF { (fstUser: UserCredentials, globalRole: GlobalRole, sndUser: UserCredentials) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresUsersStore.make[IO](transactor(), Clock[IO])
        ).tupled.use { case (accessControl, usersStore) =>
          for
            fstUserId <- usersStore
              .store(fstUser)
              .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
              .ccommit
            sndUserId <- usersStore
              .store(sndUser)
              .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
              .ccommit
            _ <- accessControl.grantGlobalAccess(fstUserId, globalRole).ccommit

            result: List[UserId] <- accessControl
              .fetchAllAuthorisedEntityIds(fstUserId, "UserEntity")
              .ccommitStream
              .compile
              .toList
//
          yield
            if globalRole == Superuser then assertEquals(result, List(fstUserId))
            if globalRole == Admin then assertEquals(result, List(fstUserId, sndUserId))
        }
      }
    }

  /*
   ** Contextual Role Specs
   */
  test("grants a user ownership, and thus access, to an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, globalRole: GlobalRole, contextualRole: ContextualRole) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantAccess(userId, entityId, contextualRole).ccommit

            result <- accessControl.canAccess(userId, entityId).ccommit
//
          yield assert(result)
        }
      }
    }

  test("stores an entity and ensures that the user is its owner"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: ContextualRole) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, docStore) =>
          for
            docId <- accessControl.ensureAccess(userId, role) { docStore.store(document) }.ccommit

            result <- accessControl.attemptWithAuthorisation(userId, docId) { docStore.fetchDocument }.ccommit
//
          yield assertEquals(result.rightOrFail, document.some)
        }
      }
    }

  test("does not perform operation on entity if the user is not authorized to access it"):
    forAllF { (userId: UserId, document: DocumentWithIdAndStream[IO], role: Role) =>
      will(cleanGrants) {
        (
          PostgresAccessControl.make[IO](transactor()),
          PostgresDocumentsStore.make[IO](transactor())
        ).tupled.use { case (accessControl, docStore) =>
          for
            // no granting access to the user
            docId <- docStore.store(document).ccommit

            result <- accessControl.attemptWithAuthorisation(userId, docId) { docStore.fetchDocument }.ccommit
//
          yield assertEquals(result.leftOrFail, "Unauthorised")
        }
      }
    }

  test("fetches all entity ids of entities owned by the user"):
    forAllF {
      (fstUserId: UserId,
       fstDocument: DocumentWithIdAndStream[IO],
       sndDocument: DocumentWithIdAndStream[IO],
       sndUserId: UserId,
       trdDocument: DocumentWithIdAndStream[IO],
       role: ContextualRole
      ) =>
        will(cleanGrants) {
          (
            PostgresAccessControl.make[IO](transactor()),
            PostgresDocumentsStore.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore) =>
            for
              fstDocumentId <- accessControl.ensureAccess(fstUserId, role) { documentStore.store(fstDocument) }.ccommit
              sndDocumentId <- accessControl.ensureAccess(fstUserId, role) { documentStore.store(sndDocument) }.ccommit
              trdDocumentId <- accessControl.ensureAccess(sndUserId, role) { documentStore.store(trdDocument) }.ccommit

              result <- accessControl
                .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                .ccommitStream
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocumentId, sndDocumentId))
          }
        }
    }

  // TODO What if global access?
  test("revokes a user's permission to access an entity"):
    forAllF { (userId: UserId, entityId: DocumentId, role: ContextualRole) =>
      will(cleanGrants) {
        PostgresAccessControl.make[IO](transactor()).use { accessControl =>
          for
            _ <- accessControl.grantAccess(userId, entityId, role).ccommit

            _ <- accessControl.revokeAccess(userId, entityId).ccommit

            grantRemoved <- accessControl.canAccess(userId, entityId).map(!_).ccommit
          yield assert(grantRemoved)
        }
      }
    }

trait PostgresAccessControlSpecContext:
  import org.scalacheck.Gen

  def cleanGrants: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.access_control".update.run.void *>
      sql"TRUNCATE TABLE auth.global_access_control".update.run.void *>
      sql"TRUNCATE TABLE auth.users".update.run.void *>
      sql"TRUNCATE TABLE domain.documents".update.run.void

  def entities = Gen.oneOf(documentsIds, userIds)

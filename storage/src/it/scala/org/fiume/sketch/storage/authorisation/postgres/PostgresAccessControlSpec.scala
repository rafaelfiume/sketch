package org.fiume.sketch.storage.authorisation.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.User.UserCredentials
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.authorisation.{AccessDenied, ContextualRole, GlobalRole, Role}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.authorisation.GlobalRole.{Admin, Superuser}
import org.fiume.sketch.shared.authorisation.testkit.AccessControlGens.*
import org.fiume.sketch.shared.authorisation.testkit.AccessControlGens.given
import org.fiume.sketch.shared.domain.documents.{DocumentId, DocumentWithIdAndStream}
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.someOrFail
import org.fiume.sketch.storage.auth.postgres.PostgresUsersStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.postgres.PostgresTransactionManager
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class PostgresAccessControlSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with DockerPostgresSuite
    with UsersStoreContext
    with PostgresAccessControlSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(5)

  /*
   ** Global Role Specs
   */

  test("grants Admin users permission to access all entities"):
    forAllF(userIds, entitiesIds) { (userId, entityId) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, tx) =>
          for
            _ <- tx.commit { accessControl.grantGlobalAccess(userId, Admin) }

            // note that it is up to the developer to make sure that a pair of existing userId and entityId are passed
            grantedAccess <- tx.commit { accessControl.canAccess(userId, entityId) }
//
          yield assert(grantedAccess)
        }
      }
    }

  test("grants Superuser's permission to access all entities except UserEntity"):
    forAllF(userIds, entitiesIds) { (userId, entityId) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, tx) =>
          for
            _ <- tx.commit { accessControl.grantGlobalAccess(userId, Superuser) }

            grantedAccess <- tx.commit { accessControl.canAccess(userId, entityId) }
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
        will(cleanStorage) {
          (
            PostgresAccessControl.make[IO](),
            PostgresDocumentsStore.make[IO](),
            PostgresUsersStore.make[IO](),
            PostgresTransactionManager.make[IO](transactor())
          ).tupled.use { case (accessControl, docStore, usersStore, tx) =>
            for
              fstUserId <- tx.commit {
                usersStore
                  .createAccount(fstUser)
                  .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
              }
              sndUserId <- tx.commit {
                usersStore
                  .createAccount(sndUser)
                  .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
              }
              fstDocId <- tx.commit {
                accessControl.ensureAccess_(fstUserId, Owner) { docStore.store(fstDocument.bytes, fstDocument) }
              }
              sndDocId <- tx.commit {
                accessControl.ensureAccess_(sndUserId, Owner) { docStore.store(sndDocument.bytes, sndDocument) }
              }
              trdDocId <- tx.commit {
                accessControl.ensureAccess_(sndUserId, Owner) { docStore.store(trdDocument.bytes, trdDocument) }
              }
              _ <- tx.commit { accessControl.grantGlobalAccess(fstUserId, globalRole) }

              result <- tx
                .commitStream {
                  accessControl
                    .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                }
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
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresUsersStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, usersStore, tx) =>
          for
            fstUserId <- tx.commit {
              usersStore
                .createAccount(fstUser)
                .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
            }
            sndUserId <- tx.commit {
              usersStore
                .createAccount(sndUser)
                .flatTap { userId => accessControl.grantAccess(userId, userId, Owner) }
            }
            _ <- tx.commit { accessControl.grantGlobalAccess(fstUserId, globalRole) }

            result: List[UserId] <- tx
              .commitStream {
                accessControl
                  .fetchAllAuthorisedEntityIds(fstUserId, "UserEntity")
              }
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
    forAllF(userIds, entitiesIds) { (userId, entityId) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, tx) =>
          for
            _ <- tx.commit { accessControl.grantAccess(userId, entityId, Owner) }

            result <- tx.commit { accessControl.canAccess(userId, entityId) }
//
          yield assert(result)
        }
      }
    }

  test("stores an entity and ensures that the user is its owner"):
    forAllF { (userId: UserId, doc: DocumentWithIdAndStream[IO], role: ContextualRole) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, docStore, tx) =>
          for
            docId <- tx.commit {
              accessControl.ensureAccess_(userId, role) {
                docStore.store(doc.bytes, doc)
              }
            }

            result <- tx.commit {
              accessControl.attempt(userId, docId) { docStore.fetchDocument }
            }
//
          yield assertEquals(result.rightOrFail, doc.some)
        }
      }
    }

  test("does not perform operation on entity if the user is not authorized to access it"):
    forAllF { (userId: UserId, doc: DocumentWithIdAndStream[IO]) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresDocumentsStore.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, docStore, tx) =>
          for
            // no granting access to the user
            docId <- tx.commit {
              docStore.store(doc.bytes, doc)
            }

            result <- tx.commit { accessControl.attempt(userId, docId) { docStore.fetchDocument } }
//
          yield assertEquals(result.leftOrFail, AccessDenied)
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
        will(cleanStorage) {
          (
            PostgresAccessControl.make[IO](),
            PostgresDocumentsStore.make[IO](),
            PostgresTransactionManager.make[IO](transactor())
          ).tupled.use { case (accessControl, documentStore, tx) =>
            for
              fstDocumentId <- tx.commit {
                accessControl.ensureAccess_(fstUserId, role) { documentStore.store(fstDocument.bytes, fstDocument) }
              }
              sndDocumentId <- tx.commit {
                accessControl.ensureAccess_(fstUserId, role) { documentStore.store(sndDocument.bytes, sndDocument) }
              }
              _ <- tx.commit {
                accessControl.ensureAccess_(sndUserId, role) { documentStore.store(trdDocument.bytes, trdDocument) }
              }

              result <- tx
                .commitStream {
                  accessControl
                    .fetchAllAuthorisedEntityIds(fstUserId, "DocumentEntity")
                }
                .compile
                .toList
//
            yield assertEquals(result, List(fstDocumentId, sndDocumentId))
          }
        }
    }

  // TODO What if global access?
  test("revokes a user's permission to access an entity"):
    forAllF(userIds, entitiesIds, contextualRoles) { (userId, entityId, contextualRole) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, tx) =>
          for
            _ <- tx.commit {
              accessControl.grantAccess(userId, entityId, contextualRole)
            }

            _ <- tx.commit {
              accessControl.revokeContextualAccess(userId, entityId)
            }

            grantRemoved <- tx.commit {
              accessControl.canAccess(userId, entityId).map(!_)
            }
          yield assert(grantRemoved)
        }
      }
    }

  /*
   ** Roles precedence
   */

  // fetchRole returns the least permissive role first
  test("contextual roles takes precedence over global roles"):
    forAllF(userIds, entitiesIds, globalRoles) { (userId, entityId, globalRole) =>
      will(cleanStorage) {
        (
          PostgresAccessControl.make[IO](),
          PostgresTransactionManager.make[IO](transactor())
        ).tupled.use { case (accessControl, tx) =>
          for
            _ <- tx.commit {
              accessControl.grantGlobalAccess(userId, globalRole) *>
                accessControl.grantAccess(userId, entityId, Owner)
            }

            result <- tx.commit { accessControl.fetchRole(userId, entityId) }
//
          yield assertEquals(result.someOrFail, Role.Contextual(Owner))
        }
      }
    }

trait PostgresAccessControlSpecContext:
  import org.scalacheck.Gen

  def cleanStorage: ConnectionIO[Unit] =
    sql"""|TRUNCATE TABLE
          |  auth.access_control,
          |  auth.global_access_control,
          |  auth.account_deletion_scheduled_events,
          |  auth.users,
          |  domain.documents
      """.stripMargin.update.run.void

  def entitiesIds = Gen.oneOf(documentsIds, userIds)

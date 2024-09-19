package org.fiume.sketch.shared.auth0.algebras

import cats.FlatMap
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.{Account, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.jobs.PermanentAccountDeletionJob

import java.time.Instant
import scala.concurrent.duration.Duration

trait UsersStore[F[_], Txn[_]: FlatMap] extends Store[F, Txn]:
  def store(credentials: UserCredentials): Txn[UserId]

  def fetchAccount(username: Username): Txn[Option[Account]]

  def fetchCredentials(username: Username): Txn[Option[UserCredentialsWithId]]

  def updatePassword(uuid: UserId, password: HashedPassword): Txn[Unit]

  def markForDeletion(uuid: UserId, timeUntilPermanentDeletion: Duration): Txn[PermanentAccountDeletionJob] =
    softDeleteAccount(uuid).flatMap { deletedAt =>
      val permanentDeletionAt = deletedAt.plusSeconds(timeUntilPermanentDeletion.toSeconds)
      schedulePermanentDeletion(uuid, permanentDeletionAt)
    }

  protected def softDeleteAccount(uuid: UserId): Txn[Instant]

  protected def schedulePermanentDeletion(
    uuid: UserId,
    permanentDeletionAt: Instant
  ): Txn[PermanentAccountDeletionJob]

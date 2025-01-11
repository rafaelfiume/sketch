package org.fiume.sketch.users

import cats.Functor
import cats.implicits.*
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotificationConsumer
import org.fiume.sketch.shared.common.jobs.Job
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore

object UserDataDeletionJob:
  def make[F[_], Txn[_]: Functor](
    accountDeletedNotificationConsumer: AccountDeletedNotificationConsumer[Txn],
    store: DocumentsStore[F, Txn]
  ) =
    new UserDataDeletionJob[F, Txn](
      accountDeletedNotificationConsumer,
      store
    )

private class UserDataDeletionJob[F[_], Txn[_]: Functor] private (
  accountDeletedNotificationConsumer: AccountDeletedNotificationConsumer[Txn],
  store: DocumentsStore[F, Txn]
) extends Job[F, Unit]: // unit for now
  override val description: String = "Deletes user data following account deletion"
  override def run(): F[Unit] =
    // consume event
    // fetch all documents of a user
    // delete all documents of a user
    val job = accountDeletedNotificationConsumer
      .consumeEvent()
      .void // for now
    store.commit { job }

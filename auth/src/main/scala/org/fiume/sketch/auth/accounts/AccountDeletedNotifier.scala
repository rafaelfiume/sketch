package org.fiume.sketch.auth.accounts

import cats.effect.kernel.Sync
import org.fiume.sketch.shared.auth.UserId

case class UserDeletedEvent(userId: UserId, consumerName: String)

trait AccountDeletedNotifier[F[_]]:
  def notify(userId: UserId): F[Unit]

object AccountDeletedNotifier:
  def makeNoOp[F[_]: Sync](): AccountDeletedNotifier[F] = new AccountDeletedNotifier[F]:
    override def notify(userId: UserId): F[Unit] = Sync[F].delay { () }

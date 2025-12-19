package org.fiume.sketch.shared.testkit

import cats.effect.{IO, Ref}

object TxRef:
  def of[A](initial: A): IO[TxRef[A]] =
    for
      committed <- Ref.of[IO, A](initial)
      staged <- Ref.of[IO, Option[A]](None)
    yield new TxRef(committed, staged)

final class TxRef[A] private (
  private val committed: Ref[IO, A],
  private val staged: Ref[IO, Option[A]]
):

  def get: IO[A] =
    staged.get.flatMap {
      case Some(value) => IO.pure(value)
      case None        => committed.get
    }

  private def ensureStaged: IO[Unit] =
    staged.get.flatMap {
      case Some(_) => IO.unit
      case None    =>
        committed.get.flatMap { value =>
          staged.set(Some(value))
        }
    }

  def update(f: A => A): IO[Unit] =
    ensureStaged *> staged.update(_.map(f))

  def modify[B](f: A => (A, B)): IO[B] =
    ensureStaged *> staged.modify {
      case Some(a) =>
        val (next, b) = f(a)
        Some(next) -> b
      case None =>
        // impossible due to ensureStaged
        throw new IllegalStateException("staged not initialized")
    }

  def commit: IO[Unit] =
    staged
      .modify {
        case Some(value) => None -> Some(value)
        case None        => None -> None
      }
      .flatMap {
        case Some(value) => committed.set(value)
        case None        => IO.unit
      }

  def rollback: IO[Unit] = staged.set(None)

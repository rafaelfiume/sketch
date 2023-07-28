package org.fiume.sketch.shared.app.troubleshooting

import cats.data.NonEmptyChain
import cats.implicits.*

/*
 * Components where invariants hold depending on external input should implement `InvariantHolder`,
 * so they are guarded from lack of or invalid parameters upon creation.
 *
 * `InvariantHolder` is not meant for invariants that can be preserved internally by e.g. proper testing.
 *
 * Invariants: set of properties a component must hold at all times to be valid.
 */
trait InvariantHolder[E <: InvariantError]:
  val invariantErrors: Set[E]

/*
 * Invariant errors caused by lack of or wrong input sent by users or 3rd party services,
 * for instance `WeakPassword`, `WeakUsername` or `InvalidDocument`.
 *
 * `InvariantError` defines properties to provide feedback to the external agents, pointing them to the problem,
 * and giving them a chance to fix it.
 */
trait InvariantError:
  def uniqueCode: String
  def message: String

object InvariantError:
  def inputErrorsToMap(inputErrors: List[InvariantError]): Map[String, String] =
    inputErrors.map(e => e.uniqueCode -> e.message).toMap

object InvariantErrorSyntax:
  extension (inputErrors: NonEmptyChain[InvariantError])
    def asString: String = InvariantError.inputErrorsToMap(inputErrors.toList).mkString(", ")

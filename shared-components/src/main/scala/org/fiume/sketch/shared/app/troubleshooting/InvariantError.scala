package org.fiume.sketch.shared.app.troubleshooting

/*
 * Invariant errors caused by wrong input sent by users or 3rd party services.
 *
 * Classes or objects extending this trait are actual errors,
 * for example `WeakPassword`, `WeakUsername` or `InvalidDocument`.
 *
 * `InvariantError` defines properties to provide feedback to the external agents, pointing them to the problem,
 * and giving them a chance to fix it.
 *
 * They are not the same as other invariants that can be preserved internally by e.g. proper testing.
 */
trait InvariantError:
  def uniqueCode: String
  def message: String

object InvariantError:
  def inputErrorsToMap(inputErrors: List[InvariantError]): Map[String, String] =
    inputErrors.map(e => e.uniqueCode -> e.message).toMap

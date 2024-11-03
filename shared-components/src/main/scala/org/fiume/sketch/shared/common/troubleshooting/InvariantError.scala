package org.fiume.sketch.shared.common.troubleshooting

/*
 * Invariants: set of properties a component must hold at all times to be valid.
 *
 * Invariant errors caused by lack of or wrong input sent by users or 3rd party services,
 * for instance `WeakPassword`, `WeakUsername` or `InvalidDocument`.
 *
 * In other words, `InvariantError` is not meant for invariants that can be preserved internally by e.g. proper testing.
 *
 * `InvariantError` defines properties to provide feedback to the external agents, pointing them to the problem,
 * and giving them a chance to fix it.
 */
trait InvariantError:
  def key: String
  def detail: String

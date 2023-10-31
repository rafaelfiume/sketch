package org.fiume.sketch.shared.app.typeclasses

/*
 * Produces semantic messages (think on the end user).
 *
 * Compare with `Object#toString` and cats `Show` which are meant to developers.
 */
trait ToSemanticString[A]:
  def toSemanticString(value: A): String

object ToSemanticStringSyntax:
  extension [A](msg: A)(using T: ToSemanticString[A]) def asSemanticString(): String = T.toSemanticString(msg)

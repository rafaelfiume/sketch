package org.fiume.sketch.shared.app.typeclasses

/*
 * Examples of semantic strings:
 * - User-facing output
 * - Serialised instances (e.g. Json, Xml)
 * - Business entities (e.g. Id, Product)
 *
 * Example of non-semantic strings:
 * - Debug and logging.
 */
trait SemanticString[A]:
  def asString(value: A): String

object SemanticStringSyntax:
  extension [A](msg: A)(using T: SemanticString[A]) def asString: String = T.asString(msg)

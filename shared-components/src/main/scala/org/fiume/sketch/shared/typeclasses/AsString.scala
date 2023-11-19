package org.fiume.sketch.shared.typeclasses

/*
 * Use when types should have a semanic valid String representation.
 *
 * Examples of semantic strings:
 * - User-facing output
 * - Serialised instances (e.g. Json, Xml)
 * - Business entities (e.g. Id, Product)
 *
 * Example of non-semantic strings:
 * - Debug and logging.
 * 
 * Laws:
 * - Isomorphism: `AsString[T].asString(t: T)` <-> `FromString[T].fromString(t: T).rightValue`
 */
trait AsString[A]:
  def asString(value: A): String

object SemanticStringSyntax:
  extension [A](msg: A)(using T: AsString[A]) def asString: String = T.asString(msg)

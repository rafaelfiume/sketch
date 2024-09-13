package org.fiume.sketch.shared.typeclasses

/*
 * Use when types should have a semanic valid String representation.
 *
 * Examples of semantic strings:
 * - User-facing output
 * - Serialised instances (e.g. Json, Xml, dabatase values)
 * - Business entities (e.g. Id, Product)
 *
 * Example of non-semantic strings:
 * - Debug and logging.
 *
 * Laws:
 * - Isomorphism: `T.asString()` <-> `T.parsed().rightValue`
 */
trait AsString[A]:
  extension (value: A) def asString(): String

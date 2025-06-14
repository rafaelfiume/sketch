package org.fiume.sketch.shared.auth

case class Jwt private (value: String) extends AnyVal

/*
 * Pronounced as "jot".
 */
object Jwt:
  def makeUnsafeFromString(value: String): Jwt = new Jwt(value)

package org.fiume.sketch.shared.auth

case class Jwt private (value: String) extends AnyVal

object Jwt:
  def makeUnsafeFromString(value: String): Jwt = new Jwt(value)

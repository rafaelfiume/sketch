package org.fiume.sketch.shared.common

import org.fiume.sketch.shared.common.Version.{Build, Commit, Environment}

case class Version(env: Environment, build: Build, commit: Commit)

object Version:
  case class Environment(name: String) extends AnyVal
  case class Build(name: String) extends AnyVal
  case class Commit(name: String) extends AnyVal

  object json:
    import io.circe.{Decoder, Encoder}

    given Encoder[Environment] = Encoder.encodeString.contramap(_.name)
    given Decoder[Environment] = Decoder.decodeString.map(Environment.apply)
    given Encoder[Build] = Encoder.encodeString.contramap(_.name)
    given Decoder[Build] = Decoder.decodeString.map(Build.apply)
    given Encoder[Commit] = Encoder.encodeString.contramap(_.name)
    given Decoder[Commit] = Decoder.decodeString.map(Commit.apply)

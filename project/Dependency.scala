import sbt._

// format: off
object Dependency {
  private object Version {
    val cats                  = "2.6.1"
    val catsEffect            = "3.1.1"
    val circe                 = "0.14.1"
    val ciris                 = "2.0.1"
    val doobie                = "1.0.0-RC1"
    val flyway                = "7.5.2"
    val http4s                = "1.0.0-M30"
    val log4cats              = "2.1.1"
    val slf4j                 = "1.7.36"

    val munit                 = "0.7.29"
    val munitCatsEffect       = "1.0.7"
    val munitScalaCheckEffect = "1.0.3"
    val munitTestcontainers   = "0.39.12"
  }

  val cats                        = "org.typelevel"              %% "cats-free"                           % Version.cats
  val catsEffect                  = "org.typelevel"              %% "cats-effect"                         % Version.catsEffect
  val circeCore                   = "io.circe"                   %% "circe-core"                          % Version.circe
  val circeGeneric                = "io.circe"                   %% "circe-generic"                       % Version.circe
  val circeParser                 = "io.circe"                   %% "circe-parser"                        % Version.circe
  val ciris                       = "is.cir"                     %% "ciris"                               % Version.ciris
  val doobieCirce                 = "org.tpolecat"               %% "doobie-postgres-circe"               % Version.doobie
  val doobieCore                  = "org.tpolecat"               %% "doobie-core"                         % Version.doobie
  val doobiePostgres              = "org.tpolecat"               %% "doobie-postgres"                     % Version.doobie
  val doobieHikari                = "org.tpolecat"               %% "doobie-hikari"                       % Version.doobie
  val flyway                      = "org.flywaydb"               %  "flyway-core"                         % Version.flyway
  val http4sBlazeClient           = "org.http4s"                 %% "http4s-blaze-client"                 % Version.http4s
  val http4sBlazeServer           = "org.http4s"                 %% "http4s-blaze-server"                 % Version.http4s
  val http4sDsl                   = "org.http4s"                 %% "http4s-dsl"                          % Version.http4s
  val http4sCirce                 = "org.http4s"                 %% "http4s-circe"                        % Version.http4s
  val log4catsCore                = "org.typelevel"              %% "log4cats-core"                       % Version.log4cats
  val log4catsSlf4j               = "org.typelevel"              %% "log4cats-slf4j"                      % Version.log4cats
  val slf4jSimple                 = "org.slf4j"                  %  "slf4j-simple"                        % Version.slf4j

  //// Test Dependencies

  val munitTestcontainersScala    = "com.dimafeng"               %% "testcontainers-scala-munit"          % Version.munitTestcontainers
  val munitTestcontainersScalaPG  = "com.dimafeng"               %% "testcontainers-scala-postgresql"     % Version.munitTestcontainers
  val munit                       = "org.scalameta"              %% "munit"                               % Version.munit
  val munitCatsEffect             = "org.typelevel"              %% "munit-cats-effect-3"                 % Version.munitCatsEffect
  val munitScalaCheckEffect       = "org.typelevel"              %% "scalacheck-effect-munit"             % Version.munitScalaCheckEffect
}

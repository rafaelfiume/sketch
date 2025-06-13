import sbt.*

// format: off
object Dependency {
  private object Version {
    val jbcrypt               = "0.4"
    val jwtCirce              = "10.0.1"
    val bouncycastle          = "1.78.1"

    val cats                  = "2.12.0"
    val catsEffect            = "3.5.4"
    val circe                 = "0.14.9"
    val ciris                 = "3.6.0"
    val doobie                = "1.0.0-RC5"
    val flyway                = "10.17.2"
    val fs2                   = "3.11.0"
    val http4s                = "1.0.0-M41"
    val log4cats              = "2.7.0"
    val slf4j                 = "2.0.16"

    val gatling               = "3.11.5"
    val logstash              = "8.0"
    val munit                 = "1.0.1"
    val munitCatsEffect       = "2.1.0"
    val munitDiscipline       = "2.0.0"
    val munitScalaCheck       = "1.0.0"
    val munitScalaCheckEffect = "1.0.4"
    val munitTestcontainers   = "0.41.4"
  }

  // auth
  val jbcrypt                     = "org.mindrot"                %  "jbcrypt"                             % Version.jbcrypt
  val jwtCirce                    = "com.github.jwt-scala"       %% "jwt-circe"                           % Version.jwtCirce
  val bouncycastle                = "org.bouncycastle"           %  "bcprov-jdk18on"                      % Version.bouncycastle

  // common
  val cats                        = "org.typelevel"              %% "cats-core"                           % Version.cats
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
  val flywayPostgres              = "org.flywaydb"               %  "flyway-database-postgresql"          % Version.flyway
  val fs2Core                     = "co.fs2"                     %% "fs2-core"                            % Version.fs2
  val fs2Io                       = "co.fs2"                     %% "fs2-io"                              % Version.fs2
  val http4sCirce                 = "org.http4s"                 %% "http4s-circe"                        % Version.http4s
  val http4sDsl                   = "org.http4s"                 %% "http4s-dsl"                          % Version.http4s
  val http4sEmberClient           = "org.http4s"                 %% "http4s-ember-client"                 % Version.http4s
  val http4sEmberServer           = "org.http4s"                 %% "http4s-ember-server"                 % Version.http4s
  val log4catsSlf4j               = "org.typelevel"              %% "log4cats-slf4j"                      % Version.log4cats
  val slf4jSimple                 = "org.slf4j"                  %  "slf4j-simple"                        % Version.slf4j

  //// Test Dependencies
  val catsLaws                    = "org.typelevel"              %% "cats-laws"                           % Version.cats
  val gatlingHighcharts           = "io.gatling.highcharts"      %  "gatling-charts-highcharts"           % Version.gatling
  val gatlingTestFramework        = "io.gatling"                 %  "gatling-test-framework"              % Version.gatling
  val logstashLogbackEncoder      = "net.logstash.logback"       % "logstash-logback-encoder"             % Version.logstash
  val munit                       = "org.scalameta"              %% "munit"                               % Version.munit
  val munitCatsEffect             = "org.typelevel"              %% "munit-cats-effect"                   % Version.munitCatsEffect
  val munitDiscipline             = "org.typelevel"              %% "discipline-munit"                    % Version.munitDiscipline
  val munitScalaCheck             = "org.scalameta"              %% "munit-scalacheck"                    % Version.munitScalaCheck
  val munitScalaCheckEffect       = "org.typelevel"              %% "scalacheck-effect-munit"             % Version.munitScalaCheckEffect
  val munitTestcontainersScala    = "com.dimafeng"               %% "testcontainers-scala-munit"          % Version.munitTestcontainers
  val munitTestcontainersScalaPG  = "com.dimafeng"               %% "testcontainers-scala-postgresql"     % Version.munitTestcontainers
}

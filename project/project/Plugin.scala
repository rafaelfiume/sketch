import sbt.*

// format: off
object Plugin {
  private object Version {
    val Gatling             = "4.16.0"
    val Git                 = "2.1.0"
    val SbtNativePackager   = "1.11.1"
    val Scalafix            = "0.14.3"
    val ScalaFmt            = "2.5.4"
    val Tpolecat            = "0.5.2"
  }

  val Gatling               = "io.gatling"                    % "gatling-sbt"                 % Version.Gatling
  val Git                   = "com.github.sbt"                % "sbt-git"                     % Version.Git
  val SbtNativePackager     = "com.github.sbt"               %% "sbt-native-packager"         % Version.SbtNativePackager
  val Scalafix              = "ch.epfl.scala"                 % "sbt-scalafix"                % Version.Scalafix
  val ScalaFmt              = "org.scalameta"                 % "sbt-scalafmt"                % Version.ScalaFmt
  val Tpolecat              = "org.typelevel"                 % "sbt-tpolecat"                % Version.Tpolecat
}

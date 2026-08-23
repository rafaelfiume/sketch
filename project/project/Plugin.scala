import sbt.*

// format: off
object Plugin {
  private object Version {
    val Gatling             = "4.19.1"
    val Git                 = "2.1.0"
    val SbtNativePackager   = "1.11.7"
    val Scalafix            = "0.14.7"
    val ScalaFmt            = "2.6.2"
    val Tpolecat            = "0.5.2"
  }

  val Gatling               = "io.gatling"                    % "gatling-sbt"                 % Version.Gatling
  val Git                   = "com.github.sbt"                % "sbt-git"                     % Version.Git
  val SbtNativePackager     = "com.github.sbt"               %% "sbt-native-packager"         % Version.SbtNativePackager
  val Scalafix              = "ch.epfl.scala"                 % "sbt-scalafix"                % Version.Scalafix
  val ScalaFmt              = "org.scalameta"                 % "sbt-scalafmt"                % Version.ScalaFmt
  val Tpolecat              = "org.typelevel"                 % "sbt-tpolecat"                % Version.Tpolecat
}

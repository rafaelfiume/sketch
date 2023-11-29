import sbt.*

// format: off
object Plugin {
  private object Version {
    val DependencyGraph     = "0.9.2"
    val Gatling             = "4.6.0"
    val Git                 = "2.0.1"
    val SbtNativePackager   = "1.9.16"
    val Scalafix            = "0.11.1"
    val ScalaFmt            = "2.5.2"
    val Tpolecat            = "0.5.0"
  }

  val DependencyGraph       = "net.virtual-void"              % "sbt-dependency-graph"        % Version.DependencyGraph
  val Gatling               = "io.gatling"                    % "gatling-sbt"                 % Version.Gatling
  val Git                   = "com.github.sbt"                % "sbt-git"                     % Version.Git
  val SbtNativePackager     = "com.github.sbt"               %% "sbt-native-packager"         % Version.SbtNativePackager
  val Scalafix              = "ch.epfl.scala"                 % "sbt-scalafix"                % Version.Scalafix
  val ScalaFmt              = "org.scalameta"                 % "sbt-scalafmt"                % Version.ScalaFmt
  val Tpolecat              = "org.typelevel"                 % "sbt-tpolecat"                % Version.Tpolecat
}

import sbt.*

// format: off
object Plugin {
  private object Version {
    val DependencyGraph     = "0.9.2"
    val Git                 = "2.0.1"
    val OrganizeImports     = "0.6.0"
    val PortableScala       = "1.0.0"
    val SbtNativePackager   = "1.9.13"
    val Scalafix            = "0.10.4"
    val ScalaFmt            = "2.5.0"
    val ScalaJs             = "1.13.1"
    val Tpolecat            = "0.4.2"
  }

  val DependencyGraph       = "net.virtual-void"              % "sbt-dependency-graph"        % Version.DependencyGraph
  val Git                   = "com.github.sbt"                % "sbt-git"                     % Version.Git
  val OrganizeImports       = "com.github.liancheng"         %% "organize-imports"            % Version.OrganizeImports
  val PortableScala         = "org.portable-scala"            % "sbt-scalajs-crossproject"    % Version.PortableScala
  val SbtNativePackager     = "com.github.sbt"               %% "sbt-native-packager"         % Version.SbtNativePackager
  val Scalafix              = "ch.epfl.scala"                 % "sbt-scalafix"                % Version.Scalafix
  val ScalaFmt              = "org.scalameta"                 % "sbt-scalafmt"                % Version.ScalaFmt
  val ScalaJs               = "org.scala-js"                  % "sbt-scalajs"                 % Version.ScalaJs
  val Tpolecat              = "io.github.davidgregory084"     % "sbt-tpolecat"                % Version.Tpolecat
}

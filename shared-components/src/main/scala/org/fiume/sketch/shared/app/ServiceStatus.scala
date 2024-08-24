package org.fiume.sketch.shared.app

import cats.Eq
import cats.implicits.*
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.typeclasses.{AsString, FromString}

sealed abstract case class ServiceStatus(version: Version, status: Status, dependencies: List[DependencyStatus[?]])

object ServiceStatus:
  def make(version: Version, dependencies: List[DependencyStatus[?]]): ServiceStatus =
    val overallStatus: Status =
      dependencies.foldRight(Status.Ok) { (dependency, acc) =>
        if acc === Status.Ok && dependency.status === Status.Ok then Status.Ok else Status.Degraded
      }
    new ServiceStatus(version, overallStatus, dependencies) {}

  enum Status:
    case Ok
    case Degraded

  object Status:
    given AsString[Status] = new AsString[Status]:
      extension (value: Status) override def asString(): String = value.toString() // yolo

    given Eq[Status] = Eq.fromUniversalEquals

  case class DependencyStatus[T <: Dependency](dependency: T, status: Status)

  sealed trait Dependency:
    def name: String
    override def toString(): String = name

  object Dependency:
    trait Database extends Dependency:
      override def name: String = "database"

    trait Rustic extends Dependency:
      override def name: String = "rustic"

    val database: Database = new Database {}
    val rustic: Rustic = new Rustic {}

    given [T <: Dependency]: AsString[T] = new AsString[T]:
      extension (value: T) override def asString(): String = value.name // yolo

    given FromString[String, Dependency] = new FromString[String, Dependency]:
      extension (value: String)
        override def parsed() = value match // yolo
          case "database" => database.asRight[String]
          case "rustic"   => rustic.asRight[String]
          case _          => s"unknown Dependency $value".asLeft[Dependency]

  object json:
    import cats.implicits.*
    import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
    import io.circe.Decoder.Result
    import io.circe.syntax.*
    import org.fiume.sketch.shared.app.Version.json.given
    import org.fiume.sketch.shared.app.Version.Environment
    import org.fiume.sketch.shared.app.Version.Commit
    import org.fiume.sketch.shared.app.Version.Build
    import org.fiume.sketch.shared.app.ServiceStatus.Dependency.given

    given Encoder[Status] = Encoder.encodeString.contramap(_.asString())
    given Decoder[Status] = Decoder.decodeString.map(Status.valueOf(_))

    given Encoder[DependencyStatus[?]] = new Encoder[DependencyStatus[?]]:
      override def apply(dependency: DependencyStatus[?]): Json =
        Json.obj(
          dependency.dependency.asString() -> dependency.status.asJson
        )

    given Decoder[DependencyStatus[?]] = new Decoder[DependencyStatus[?]]:
      def apply(c: HCursor): Result[DependencyStatus[?]] =
        for
          dependency0 <- c.keys // assumes only one key: 'dependencies'
            .flatMap(_.headOption)
            .toRight(DecodingFailure("Missing 'dependencies' key", c.history))
          dependency <- dependency0.parsed().leftMap(e => DecodingFailure(e, c.history))
          status <- c.downField(dependency0).as[Status]
        yield DependencyStatus(dependency, status)

    given Encoder[ServiceStatus] = new Encoder[ServiceStatus]:
      override def apply(service: ServiceStatus): Json =
        Json.obj(
          "env" -> service.version.env.asJson,
          "build" -> service.version.build.asJson,
          "commit" -> service.version.commit.asJson,
          "status" -> service.status.asJson,
          "dependencies" -> service.dependencies.asJson
        )

    given Decoder[ServiceStatus] = new Decoder[ServiceStatus]:
      override def apply(c: HCursor): Result[ServiceStatus] =
        for
          env <- c.downField("env").as[Environment]
          build <- c.downField("build").as[Build]
          commit <- c.downField("commit").as[Commit]
          dependencies <- c.downField("dependencies").as[List[DependencyStatus[?]]]
        yield ServiceStatus.make(Version(env, build, commit), dependencies)

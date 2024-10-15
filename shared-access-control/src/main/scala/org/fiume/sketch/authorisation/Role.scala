package org.fiume.sketch.shared.authorisation

import cats.implicits.*
import cats.kernel.Eq
import org.fiume.sketch.shared.app.troubleshooting.InvariantError
import org.fiume.sketch.shared.authorisation.InvalidRole.UnparsableRole
import org.fiume.sketch.shared.typeclasses.{AsString, FromString}

import scala.util.Try

enum Role:
  case Global(designation: GlobalRole)
  case Contextual(designation: ContextualRole)

enum GlobalRole:
  case Admin
  case Superuser

enum ContextualRole:
  case Owner

object Role:
  given AsString[Role] with
    extension (role: Role)
      override def asString(): String = role match
        case Role.Global(designation)     => designation.toString()
        case Role.Contextual(designation) => designation.toString()

  given FromString[InvalidRole, Role] with
    extension (role: String)
      override def parsed() =
        Try(GlobalRole.valueOf(role))
          .map(Role.Global(_))
          .orElse(Try(ContextualRole.valueOf(role)).map(Role.Contextual(_)))
          .toEither
          .leftMap(_ => UnparsableRole(role))

  given Eq[Role] = Eq.fromUniversalEquals[Role]

enum InvalidRole(val uniqueCode: String, val message: String) extends InvariantError:
  case UnparsableRole(value: String) extends InvalidRole("invalid.role", s"invalid role '$value'")

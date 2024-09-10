package org.fiume.sketch.authorisation

import cats.implicits.*
import cats.kernel.Eq
import org.fiume.sketch.authorisation.InvalidRole.UnparsableRole
import org.fiume.sketch.shared.app.troubleshooting.InvariantError
import org.fiume.sketch.shared.typeclasses.{AsString, FromString}

import scala.util.Try
import scala.util.control.NoStackTrace

/* Contextual roles, ie. a user can be a contributor of a document, owner of another, unable to access others. */
enum Role:
  // designation typically refers to an official title or position assigned to someone, often indicating their role or status within an organization or system. It can be used to denote a formal role or rank that someone holds, which aligns well with the concept of roles like Superuser, Owner, or Contributor.
  // Formal Title: Designation suggests that the role is an official or formal title given to an individual. For example, a "Manager" or "Director" is a designation within a company.
  // Clarity: Using designation can help clarify that the value represents a specific assigned role or title. It emphasizes that the role is more than just a set of permissions; it's a formal position or rank within the system.
  // The term is neutral and can apply to various roles without implying specific attributes or powers associated with the role. It focuses on the fact that a role is assigned and recognized within the system.
  case Global(designation: GlobalRole)
  case Contextual(designation: ContextualRole)

enum GlobalRole:
  case Superuser

enum ContextualRole:
  case Owner
  // case Contributor (?) // coming soon(ish)

object Role:
  given AsString[Role] = new AsString[Role]:
    extension (role: Role)
      override def asString(): String = role match
        case Role.Global(designation)     => designation.toString()
        case Role.Contextual(designation) => designation.toString()

  given FromString[InvalidRole, Role] = new FromString[InvalidRole, Role]:
    extension (role: String)
      override def parsed() =
        Try(GlobalRole.valueOf(role))
          .map(Role.Global(_))
          .orElse(Try(ContextualRole.valueOf(role)).map(Role.Contextual(_)))
          .toEither
          .leftMap(_ => UnparsableRole(role))

  given Eq[Role] = Eq.fromUniversalEquals[Role]

trait InvalidRole extends InvariantError
object InvalidRole:
  case class UnparsableRole(value: String) extends InvalidRole with NoStackTrace:
    override def uniqueCode: String = "invalid.role"
    override val message: String = s"invalid role '$value'"

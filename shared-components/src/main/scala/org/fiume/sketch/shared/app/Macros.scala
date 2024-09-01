package org.fiume.sketch.shared.app

import java.util.UUID
import scala.quoted.*

// Source: https://github.com/lampepfl/dotty-macro-examples/blob/main/fullClassName/src/macros.scala
object Macros:
  private def typeNameMacro[T: Type](using Quotes) =
    import quotes.reflect.*

    Expr(TypeTree.of[T].symbol.name)

  inline def typeName[A] = ${ typeNameMacro[A] }

  def entityIdApplyMacro[T <: Entity: Type](value: Expr[UUID])(using Quotes): Expr[EntityId[T]] =
    '{
      new EntityId[T]($value):
        def entityType: String = Macros.typeName[T]
    }

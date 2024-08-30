package org.fiume.sketch.shared.app

import java.util.UUID
import scala.quoted.*

object Meta:
  private def typeNameMacro[T: Type](using Quotes) =
    import quotes.reflect.*

    Expr(TypeTree.of[T].symbol.name)

  private inline def typeName[A] = ${ typeNameMacro[A] }

  def resouceIdApplyMacro[T <: Resource: Type](value: Expr[UUID])(using Quotes): Expr[ResourceId[T]] =
    '{
      new ResourceId[T]($value):
        def resourceType: String = Meta.typeName[T]
    }

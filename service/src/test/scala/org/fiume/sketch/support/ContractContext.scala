package org.fiume.sketch.support

import cats.effect.kernel.{Resource, Sync}

import scala.io.Source

trait ContractContext:
  def jsonFrom[F[_]](path: String)(using F: Sync[F]): Resource[F, String] =
    Resource.fromAutoCloseable(F.delay(Source.fromResource(path))).map(_.mkString(""))

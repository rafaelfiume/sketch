package org.fiume.sketch.rustic

import cats.implicits.*
import com.comcast.ip4s.{Host, Port}
import org.http4s.Uri

case class RusticClientConfig(private val host: Host, private val port: Port):
  val httpRusticUri: Uri = Uri.unsafeFromString(s"http://$host:$port")

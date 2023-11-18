package org.fiume.sketch.profile

import cats.implicits.*
import com.comcast.ip4s.{Host, Port}
import org.http4s.Uri

case class ProfileClientConfig(private val host: Host, private val port: Port):
  val httpProfileUri: Uri = Uri.unsafeFromString(s"http://$host:$port")

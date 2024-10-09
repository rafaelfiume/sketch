package org.fiume.sketch.shared.auth0.http

import com.comcast.ip4s.{Host, Port}
import org.http4s.Uri

case class HttpAuthClientConfig(private val host: Host, private val port: Port):
  val baseUri: Uri = Uri.unsafeFromString(s"http://$host:$port")

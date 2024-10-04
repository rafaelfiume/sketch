package org.fiume.sketch.shared.testkit

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

trait HttpServiceContext:
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  def makeServer(port: Port)(httpApp: HttpRoutes[IO]): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port)
      .withHttpApp(httpApp.orNotFound)
      .build

  def freePort(): IO[Port] =
    Resource
      .fromAutoCloseable(IO.delay { new java.net.ServerSocket(0) })
      .use { socket =>
        IO.delay {
          Port.fromInt { socket.getLocalPort() }.getOrElse(throw new AssertionError("there must be a free port"))
        }
      }

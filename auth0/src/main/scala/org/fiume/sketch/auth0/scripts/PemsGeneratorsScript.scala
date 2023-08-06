package org.fiume.sketch.auth0.scripts

import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import cats.effect.{ExitCode, IO, IOApp}
import fs2.{Stream, io, text}
import fs2.io.file.Files
import fs2.io.file.Path
import org.fiume.sketch.auth0.KeysGenerator
import org.fiume.sketch.auth0.KeyStringifier

object PemsGeneratorsScript extends IOApp:

  private val resourcesPath = Path(getClass.getClassLoader().getResource("").getPath())
  private val privateKeyPath = Path("private_key.pem")
  private val publicKeyPath = Path("public_key.pem")

  def writeToFile(path: Path, content: String): Stream[IO, Unit] =
    Stream.emit(content).through(text.utf8.encode).through(Files[IO].writeAll(path))

  def run(args: List[String]): IO[ExitCode] =
    for {
      keyPair <- KeysGenerator.makeEcKeyPairs[IO]()
      privateKeyPem = KeyStringifier.toPemString(keyPair._1)
      publicKeyPem = KeyStringifier.toPemString(keyPair._2)
      _ <- writeToFile(resourcesPath./(privateKeyPath), privateKeyPem).compile.drain
      _ <- writeToFile(resourcesPath./(publicKeyPath), publicKeyPem).compile.drain
    } yield ExitCode.Success

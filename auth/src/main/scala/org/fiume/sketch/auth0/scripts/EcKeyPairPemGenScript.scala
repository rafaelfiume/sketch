package org.fiume.sketch.auth.scripts

import cats.effect.{ExitCode, IO, IOApp}
import fs2.{io, text, Stream}
import fs2.io.file.{Files, Path}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth.{KeyStringifier, KeysGenerator}

import java.security.Security

/*
 * Script to generate a pair of elliptic curve (EC) keys in PEM format in the `resources` folder.
 */
object EcKeyPairPemGenScript extends IOApp:

  private val resourcesPath = Path("src/main/resources")
  private val privateKeyPath = Path("private_key.pem")
  private val publicKeyPath = Path("public_key.pem")

  def run(args: List[String]): IO[ExitCode] =
    for
      _ <- IO.delay { Security.addProvider(new BouncyCastleProvider()) }
      keyPair <- KeysGenerator.makeEcKeyPairs[IO]()
      privateKeyPem = KeyStringifier.toPemString(keyPair._1)
      publicKeyPem = KeyStringifier.toPemString(keyPair._2)
      _ <- writeToFile(resourcesPath./(privateKeyPath), privateKeyPem).compile.drain
      _ <- writeToFile(resourcesPath./(publicKeyPath), publicKeyPem).compile.drain
    yield ExitCode.Success

  private def writeToFile(path: Path, content: String): Stream[IO, Unit] =
    Stream.emit(content).through(text.utf8.encode).through(Files[IO].writeAll(path))

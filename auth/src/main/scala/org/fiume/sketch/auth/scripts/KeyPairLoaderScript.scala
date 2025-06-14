package org.fiume.sketch.auth.scripts

import cats.effect.{Async, ExitCode, IO, IOApp}
import fs2.io.file.{Files, Path}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth.KeyStringifier

import java.security.Security
import java.security.interfaces.{ECPrivateKey, ECPublicKey}

/*
 * Script to load a pair of EC keys in PEM format from the `resources` folder.
 *
 * It currently doesn't serve any purposes other than playing a little with fs2.Streams.
 */
object KeyPairLoaderScript extends IOApp:

  case class EcKeyPair(privateKey: ECPrivateKey, publicKey: ECPublicKey)

  private val resourcesPath = Path("src/main/resources")

  def run(args: List[String]): IO[ExitCode] =
    IO.delay { Security.addProvider(new BouncyCastleProvider()) } *>
      fromResources[IO]().flatMap { IO.println(_) }.as(ExitCode.Success)

  def fromResources[F[_]: Async](): F[EcKeyPair] =
    def readFile(path: Path) = Files.forAsync[F].readAll(path).through(fs2.text.utf8.decode)

    def privateKey = readFile(resourcesPath./(Path("private_key.pem")))
      .map(KeyStringifier.ecPrivateKeyFromPem)
      .map(_.getOrElse(throw new Exception("Could not read private key.")))

    def publicKey = readFile(resourcesPath./(Path("public_key.pem")))
      .map(KeyStringifier.ecPublicKeyFromPem)
      .map(_.getOrElse(throw new Exception("Could not read public key.")))

    privateKey.zip(publicKey).map(t => EcKeyPair(t._1, t._2)).compile.lastOrError

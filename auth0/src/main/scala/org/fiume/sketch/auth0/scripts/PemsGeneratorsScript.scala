package org.fiume.sketch.auth0.scripts

import cats.effect.{ExitCode, IO, IOApp}
import fs2.{io, text, Stream}
import fs2.io.file.{Files, Path}
import org.fiume.sketch.auth0.{KeyStringifier, KeysGenerator}

object PemsGeneratorsScript extends IOApp:

  private val resourcesPath = Path(getClass.getClassLoader().getResource("").getPath())
  private val privateKeyPath = Path("private_key.pem")
  private val publicKeyPath = Path("public_key.pem")

  def writeToFile(path: Path, content: String): Stream[IO, Unit] =
    Stream.emit(content).through(text.utf8.encode).through(Files[IO].writeAll(path))

  def run(args: List[String]): IO[ExitCode] =
    for
      keyPair <- KeysGenerator.makeEcKeyPairs[IO]()
      privateKeyPem = KeyStringifier.toPemString(keyPair._1)
      publicKeyPem = KeyStringifier.toPemString(keyPair._2)
      _ <- writeToFile(resourcesPath./(privateKeyPath), privateKeyPem).compile.drain
      _ <- writeToFile(resourcesPath./(publicKeyPath), publicKeyPem).compile.drain
    yield ExitCode.Success

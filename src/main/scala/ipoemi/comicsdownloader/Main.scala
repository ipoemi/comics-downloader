package ipoemi.comicsdownloader

import java.io.{File => JFile}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config._
import akka.http.scaladsl.Http
import ipoemi.comicsdownloader.downloader.AkkaHttpDownloader
import ipoemi.comicsdownloader.parser._

import scala.concurrent.ExecutionContextExecutor

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("myActorSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  val downloader = AkkaHttpDownloader(marumaru.BookParser, marumaru.PageParser)
  for {
    _ <- downloader.download("http://marumaru.in/b/manga/183438", "comics/해황기").recover {
      case err => err.printStackTrace()
    }
    _ <- Http().shutdownAllConnectionPools()
    _ <- actorSystem.terminate()
  } yield ()

}

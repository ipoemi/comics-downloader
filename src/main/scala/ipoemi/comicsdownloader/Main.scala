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

  val downloader = AkkaHttpDownloader(zangsisi.BookParser, zangsisi.PageParser)
  for {
    _ <- downloader.download("http://zangsisi.net/?p=24838", "comics/플투토2").recover {
      case err => err.printStackTrace()
    }
    _ <- Http().shutdownAllConnectionPools()
    _ <- actorSystem.terminate()
  } yield ()

}

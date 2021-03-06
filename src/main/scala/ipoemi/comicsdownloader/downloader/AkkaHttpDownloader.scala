package ipoemi.comicsdownloader.downloader

import java.io.{File => JFile}
import java.net.{InetSocketAddress, URL, URLEncoder}

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{RawHeader, `User-Agent`}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import better.files._
import ipoemi.comicsdownloader.parser.{Link, Parser}
import cats._
import cats.data._
import cats.implicits._
import akka.event.Logging
import akka.http.scaladsl.settings.ClientConnectionSettings

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

abstract class AkkaHttpDownloader(protected val bookParser: Parser, protected val pageParser: Parser) extends Downloader {
  protected implicit val actorSystem: ActorSystem
  protected implicit val materializer: ActorMaterializer
  protected implicit val executionContext: ExecutionContextExecutor

  def download(from: String, to: String): Future[Unit] = {
    def createDirectory = Future {
      val dir = to.toFile
      if (dir.notExists) dir.createDirectories()
      if (!dir.isDirectory()) throw new Exception(s"$dir is not directory")
      actorSystem.log.info("Create Diriectory Done")
    }

    def getBookLinks =
      parseContent(from, new URL(from)) { content =>
        bookParser.parse(content) map (x => (x, new URL(from)))
      }

    def getPageLinks(bookPath: String, fromUrl: URL) =
      parseContent(bookPath, fromUrl) { content =>
        pageParser.parse(content) map (x => (x, fromUrl))
      }

    def downloadTo(response: HttpResponse, destPath: String) = {
      def isValidFile(file: File, response: HttpResponse) = {
        val contentLength = response.entity.contentLengthOption.getOrElse(0L)
        file.notExists() || file.toJava.length() < contentLength
      }

      def newFile(destFile: File) = Future {
        destFile.createIfNotExists(createParents = true)
      }

      val destFile = destPath.toFile
      if (isValidFile(destFile, response)) {
        for {
          _ <- newFile(destFile)
          _ <- processResponse(response)(x => destFile.appendByteArray(x.toArray))
        } yield ()
      } else {
        Future.successful(())
      }
    }

    def downloadBooks(bookLinks: Seq[(Link, URL)]): Future[Seq[String]] = {
      val result = bookLinks map { case (book, fromUrl) =>
        val ret = for {
          pages <- getPageLinks(book.uri, new URL(book.uri))
          newPages = pages.map(x => (x._1.copy(name = book.name + "/" + x._1.name), x._2))
          _ <- Future {actorSystem.log.info(s"${book.name} Start (${newPages.size} pages)")}
          _ <- downloadPages(newPages)
          _ <- zipPages(s"$to/${book.name}", s"$to/${book.name}.zip")
          _ <- Future {actorSystem.log.info(s"$to/${book.name}.zip Created")}
          _ <- Future {s"$to/${book.name}".toFile.delete()}
        } yield book.name
        Await.result(ret, Duration.Inf)
        ret
      }
      result.toVector.sequence.map(_.toSeq)
    }

    def zipPages(destDir: String, destFile: String): Future[Unit] = {
      Future {
        destDir.toFile.zipTo(destFile.toFile)
      }
    }

    def downloadPages(pageLinks: Seq[(Link, URL)]): Future[Unit] = {
      val result = pageLinks map { case (page, fromUrl) =>
        for {
          response <- requestUrl(pathToUrl(page.uri, fromUrl))
          filePath = to + "/" + page.name
          ret <- downloadTo(response, filePath)
          _ <- Future {actorSystem.log.info(s"Download ${pathToUrl(page.uri, fromUrl)} to $filePath Done")}
        } yield ret
      }
      result.toVector.sequence_
    }

    for {
      _ <- createDirectory
      bookLinks <- getBookLinks
      _ <- downloadBooks(bookLinks)
    } yield ()
  }


  private def parseContent[A](path: String, fromUrl: URL)(f: String => Seq[A]): Future[Seq[A]] = for {
    response <- requestUrl(pathToUrl(path, fromUrl))
    content <- stringFromResponse(response)
  } yield f(content)

  private def stringFromResponse(response: HttpResponse): Future[String] = {
    response.entity.dataBytes.runFold("")(_ + _.decodeString("utf-8")).recover {
      case ex: Exception =>
        actorSystem.log.error(ex.getMessage)
        ""
    }
  }

  private def processResponse(response: HttpResponse)(f: ByteString => Unit): Future[Done] = {
    response.entity.dataBytes.runForeach(f).recover {
      case ex: Exception =>
        actorSystem.log.error(ex.getMessage)
        Done
    }
  }

  private def processRedirect(response: HttpResponse, httpMethod: HttpMethod): Future[HttpResponse] =
    if (response.status.intValue >= 300 && response.status.intValue < 400)
      for {
        url <- Future(new URL(response.getHeader("Location").get.value))
        response <- requestUrl(url, httpMethod)
      } yield response
    else
      Future.successful(response)

  private def pathToUrl(toPath: String, fromUrl: URL): URL = {
    val protocol = fromUrl.getProtocol
    val host = fromUrl.getHost
    val portStr = if (fromUrl.getPort == -1 || fromUrl.getPort == 80) "" else s":${fromUrl.getPort}"
    val fromPath = fromUrl.getPath

    if (toPath.startsWith("http")) new URL(toPath)
    else if (toPath.startsWith("/")) new URL(s"$protocol://$host$portStr$toPath")
    else new URL(s"$protocol://$host$portStr$fromPath/$toPath")
  }

  private def requestUrl(
    url: URL, method: HttpMethod = HttpMethods.GET,
    headers: List[HttpHeader] = List(),
    entity: RequestEntity = HttpEntity.Empty
  ): Future[HttpResponse] = {
    var clientConnectionSettings =
      ClientConnectionSettings(actorSystem.settings.config)
        .withIdleTimeout(15 seconds)
        .withConnectingTimeout(15 seconds)

    val connection =
      if (url.getProtocol == "https")
        Http().outgoingConnectionHttps(url.getHost, 443, Http().defaultClientHttpsContext, None, clientConnectionSettings)
      else
        Http().outgoingConnection(url.getHost, 80, None, clientConnectionSettings)

    val uriBuilder = new StringBuilder
    if (url.getPath == null || url.getPath == "")
      uriBuilder.append("/")
    else {
      uriBuilder.append(url.getPath.split("/").map(URLEncoder.encode(_, "utf-8")).mkString("/"))
      if (url.getPath.last == '/') uriBuilder.append("/")
    }

    if (url.getQuery != null) uriBuilder.append("?" + url.getQuery)

    val agent = `User-Agent`("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36")
    val accept = RawHeader("Accept", "*/*")
    val defaultHeaders = if (headers.isEmpty) List(agent, accept) else headers
    val req = HttpRequest(method = method, uri = uriBuilder.toString, entity = entity, headers = defaultHeaders)
    retry(req, connection, 5).flatMap(processRedirect(_, method))

  }

  private def retry(request: HttpRequest, connection: Flow[HttpRequest, HttpResponse, _], count: Int) = {
    def responseFut = Source.single(request).via(connection).runWith(Sink.head)

    (0 until count).foldLeft(responseFut) { (fut, _) =>
      fut.recoverWith {
        case ex: Exception =>
          actorSystem.log.error(ex.getMessage)
          responseFut
      }
    }.recover {
      case ex: Exception =>
        actorSystem.log.error(ex.getMessage())
        HttpResponse(status = 404)
    }
  }

}

object AkkaHttpDownloader {
  def apply(bookParser: Parser, pageParser: Parser)(
    implicit _actorSystem: ActorSystem,
    _materializer: ActorMaterializer,
    _executionContext: ExecutionContextExecutor
  ): AkkaHttpDownloader = new AkkaHttpDownloader(bookParser, pageParser) {
    implicit val executionContext: ExecutionContextExecutor = _executionContext
    implicit val materializer: ActorMaterializer = _materializer
    implicit val actorSystem: ActorSystem = _actorSystem
  }

}

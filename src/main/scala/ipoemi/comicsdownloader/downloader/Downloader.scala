package ipoemi.comicsdownloader.downloader

import ipoemi.comicsdownloader.parser.Parser

import scala.concurrent.Future

trait Downloader {
  protected def bookParser: Parser
  protected def pageParser: Parser
  def download(from: String, to: String): Future[Unit]
}


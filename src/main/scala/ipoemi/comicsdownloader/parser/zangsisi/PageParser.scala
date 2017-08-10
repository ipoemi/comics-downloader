package ipoemi.comicsdownloader.parser.zangsisi

import ipoemi.comicsdownloader.parser.{Link, Parser}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element

object PageParser extends Parser {
  private val imageExtensions = List("png", "jpg", "gif")

  def parse(content: String): Seq[Link] = {
    val doc = JsoupBrowser().parseString(content)

    val postOpt = (doc >?> element("#recent-post")).orElse(doc >?> element("#post"))

    val imgTagsOpt = postOpt match {
      case Some(post) =>
        for {
          contents <- post >?> element(".contents")
          imgTags <- contents >?> elementList("img")
        } yield imgTags.toVector

      case None =>
        for {
          mainOuter <- doc >?> element(".main-outer")
          contents <- mainOuter >?> element(".post-body")
          imgTags <- contents >?> elementList("img")
        } yield imgTags.toVector

    }

    val imgTags = imgTagsOpt.getOrElse(Vector())

    def isValidElem(elem: Element, srcAttrName: String) =
      elem.attrs.keySet.contains(srcAttrName) &&
        imageExtensions.exists(ext => elem.attrs(srcAttrName).toLowerCase.contains("." + ext))

    def mkLink(src: String, idx: Int): Link = {
      val ext = src.substring(src.lastIndexOf("."))
      Link("%05d".format(idx) + ext, src)
    }

    val links = imgTags.zipWithIndex map { case (elem, i) =>
      if (isValidElem(elem, "src")) {
        elem.attrs.get("src").map(mkLink(_, i))
      } else if (isValidElem(elem, "data-src")) {
        elem.attrs.get("data-src").map(mkLink(_, i))
      } else None
    }

    links.filter(_.nonEmpty).map(_.get)

  }
}

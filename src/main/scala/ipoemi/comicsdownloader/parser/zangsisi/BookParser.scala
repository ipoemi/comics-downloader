package ipoemi.comicsdownloader.parser.zangsisi

import ipoemi.comicsdownloader.parser.{Link, Parser}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

object BookParser extends Parser {
  private def refineTitle(no: Int, title: String): String =
    s"${"%03d".format(no + 1)}.${title.replaceAll("[^ㄱ-ㅎ가-힣0-9a-zA-Z.\\-~ ]", "")}"


  def parse(content: String): Seq[Link] = {
    val doc = JsoupBrowser().parseString(content)

    val recentPostOpt = doc >?> element("#recent-post")
    val postOpt = doc >?> element("#post")

    val aTags =
      (for {
        recentPost <- recentPostOpt.orElse(postOpt)
        contents <- recentPost >?> element(".contents")
        aTagList <- contents >?> elementList("a")
      } yield aTagList.toVector).getOrElse(Vector())

    aTags.zipWithIndex map { case (aTag, i) =>
      Link(refineTitle(i, aTag.text), aTag.attr("href"))
    }
  }
}

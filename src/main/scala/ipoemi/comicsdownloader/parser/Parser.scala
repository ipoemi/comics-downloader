package ipoemi.comicsdownloader.parser


trait Parser {
  def parse(content: String): Seq[Link]
}

package everyui.seo

/** A page's content as a reader that runs no script needs it — a search engine's crawler, an answer engine's fetcher,
  * a screen reader before the app has drawn: headings, paragraphs, links, pictures with their words, lists, sections.
  * One node type, named by its HTML tag: `DocHtml` writes it as semantic HTML (what the server puts in the page for the
  * app to replace), `DocMarkdown` as Markdown (a page's `.md` form, and `llms-full.txt`).
  *
  * `text` is a node's own words (escaped when written); `html` is markup already made safe (a post's rendered body),
  * written as it is, with `markdown` its source where there is one (for `DocMarkdown`); `attributes` go on the element
  * as they are listed. */
case class Doc(tag: String,
               text: String = "",
               attributes: List[(String, String)] = Nil,
               children: List[Doc] = Nil,
               html: String = "",
               markdown: String = "") {
  def attribute(name: String): String = attributes.find(_._1 == name).map(_._2).getOrElse("")
}

object Doc {
  /** Nodes one after another, with no element of their own. */
  def fragment(children: Doc*): Doc = Doc("", children = children.toList)
  /** A heading of rank `level` (1 for the page's title). */
  def heading(level: Int, text: String): Doc = Doc("h" + math.max(1, math.min(6, level)), text)
  def paragraph(text: String): Doc = Doc("p", text)
  /** A paragraph of mixed words and links (`Doc.text`, `Doc.link`, `Doc.strong`…). */
  def paragraph(parts: Doc*): Doc = Doc("p", children = parts.toList)
  /** Words with no element of their own, inside a paragraph. */
  def text(words: String): Doc = Doc("", words)
  def strong(words: String): Doc = Doc("strong", words)
  def emphasis(words: String): Doc = Doc("em", words)
  def link(href: String, text: String): Doc = Doc("a", text, List("href" -> href))
  def image(src: String, alt: String, width: Int = 0, height: Int = 0): Doc =
    Doc("img", attributes = List("src" -> src, "alt" -> alt) ++
      (if (width > 0) List("width" -> width.toString) else Nil) ++
      (if (height > 0) List("height" -> height.toString) else Nil))
  def list(items: Doc*): Doc = Doc("ul", children = items.toList.map(i => if (i.tag == "li") i else Doc("li", children = List(i))))
  def listItem(parts: Doc*): Doc = Doc("li", children = parts.toList)
  def time(iso: String, text: String): Doc = Doc("time", text, List("datetime" -> iso))
  /** Markup already made safe, written as it is: a post's body rendered from its source, which (`markdown`) is what
    * its Markdown form gives instead. */
  def trusted(html: String, markdown: String = ""): Doc = Doc("div", html = html, markdown = markdown)

  def main(children: Doc*): Doc = Doc("main", children = children.toList)
  def article(children: Doc*): Doc = Doc("article", children = children.toList)
  def section(children: Doc*): Doc = Doc("section", children = children.toList)
  def header(children: Doc*): Doc = Doc("header", children = children.toList)
  def footer(children: Doc*): Doc = Doc("footer", children = children.toList)
  /** A set of links around the site, named `label` when a page has more than one. */
  def navigation(label: String, links: Doc*): Doc =
    Doc("nav", attributes = if (label.isEmpty) Nil else List("aria-label" -> label), children = links.toList)
}

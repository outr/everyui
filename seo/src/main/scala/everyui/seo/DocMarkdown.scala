package everyui.seo

/** A `Doc` as Markdown: the page as an answer engine reads it most cheaply (its `.md` form, `llms-full.txt`) — the
  * words, headings, links and pictures, none of the page around them. Links on the site are written absolute with
  * `absolute`, since Markdown read elsewhere has no page to resolve them against. */
object DocMarkdown {
  def render(doc: Doc, absolute: String => String = identity): String =
    blocks(doc, absolute).mkString("\n\n").trim + "\n"

  // the blocks a node makes, each separated from the next by a blank line
  private def blocks(doc: Doc, absolute: String => String): List[String] = doc.tag match {
    case "h1" | "h2" | "h3" | "h4" | "h5" | "h6" =>
      List("#" * doc.tag.substring(1).toInt + " " + line(doc, absolute))
    case "p" => List(line(doc, absolute)).filter(_.nonEmpty)
    case "ul" | "ol" =>
      val items = doc.children.zipWithIndex.map { case (item, i) =>
        (if (doc.tag == "ol") (i + 1).toString + ". " else "- ") + line(item, absolute)
      }
      if (items.isEmpty) Nil else List(items.mkString("\n"))
    case "img" => List(image(doc, absolute))
    // the site's header, footer and navigation are the page around the content, not the content
    case "nav" | "header" | "footer" => Nil
    case _ if doc.markdown.nonEmpty => List(doc.markdown.trim)
    case _ if doc.html.nonEmpty => List(plain(doc.html))
    case "a" | "strong" | "em" | "time" | "" if doc.children.isEmpty => List(line(doc, absolute)).filter(_.nonEmpty)
    case _ => doc.children.flatMap(blocks(_, absolute))
  }

  // a node's words run together, with its links, emphasis and pictures inline
  private def runs(doc: Doc, absolute: String => String): String = doc.tag match {
    case "a" => "[" + words(doc, absolute).trim + "](" + absolute(doc.attribute("href")) + ")"
    case "strong" => "**" + words(doc, absolute).trim + "**"
    case "em" => "*" + words(doc, absolute).trim + "*"
    case "img" => image(doc, absolute)
    case "br" => "\n"
    case _ => words(doc, absolute)
  }

  // the words run together: spaces between the pieces kept, so "Read " and a link after it stay two words
  private def words(doc: Doc, absolute: String => String): String =
    (doc.text + doc.children.map(runs(_, absolute)).mkString).replaceAll("[ \\t\\n]+", " ")

  // a block's words, as a line: trimmed once, at its ends
  private def line(doc: Doc, absolute: String => String): String = runs(doc, absolute).trim

  private def image(doc: Doc, absolute: String => String): String =
    "![" + doc.attribute("alt") + "](" + absolute(doc.attribute("src")) + ")"

  /** Markup with no Markdown source kept: its words, one paragraph to a block. */
  private def plain(html: String): String =
    html.replaceAll("(?i)</(p|div|h[1-6]|li|pre|blockquote)>", "\n\n").replaceAll("<[^>]+>", "")
      .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
      .split("\n\n").map(_.replaceAll("\\s+", " ").trim).filter(_.nonEmpty).mkString("\n\n")
}

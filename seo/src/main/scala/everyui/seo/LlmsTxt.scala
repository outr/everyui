package everyui.seo

/** `llms.txt` (llmstxt.org): the site for an answer engine or an assistant in one short Markdown file — what it is, and
  * its pages by section, each with a line on what it holds and a link to its Markdown form where it has one. `full`
  * is `llms-full.txt`: the same with every page's Markdown in it, for a reader that wants all of it in one fetch. */
object LlmsTxt {
  /** `about` is a paragraph or two on the site beyond its one-line `summary`; sections keep the order they first
    * appear in. */
  def render(name: String, summary: String, about: String, entries: Seq[IndexEntry], absolute: String => String): String = {
    val sections = entries.map(_.section).distinct
    val body = sections.map { section =>
      val lines = entries.filter(_.section == section).map { e =>
        val link = if (e.markdown.nonEmpty) e.markdown else e.path
        "- [" + e.title + "](" + absolute(link) + ")" + (if (e.description.nonEmpty) ": " + oneLine(e.description) else "")
      }
      (if (section.isEmpty) "" else "## " + section + "\n\n") + lines.mkString("\n")
    }
    ("# " + name :: "> " + oneLine(summary) :: Option.when(about.nonEmpty)(about.trim).toList ::: body.toList)
      .mkString("\n\n") + "\n"
  }

  /** Every page in full: `pages` pairs each entry with its Markdown. */
  def full(name: String, summary: String, pages: Seq[(IndexEntry, String)], absolute: String => String): String =
    ("# " + name + "\n\n> " + oneLine(summary) + "\n") + pages.map { case (e, markdown) =>
      "\n---\n\nSource: " + absolute(e.path) + "\n\n" + markdown.trim + "\n"
    }.mkString

  private def oneLine(s: String): String = s.replaceAll("\\s+", " ").trim
}

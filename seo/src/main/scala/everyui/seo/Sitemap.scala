package everyui.seo

/** `sitemap.xml`: every page a search engine should know of, with when it last changed. */
object Sitemap {
  def render(entries: Seq[IndexEntry], absolute: String => String): String = {
    val urls = entries.map { e =>
      "  <url><loc>" + Escape.html(absolute(e.path)) + "</loc>" +
        (if (e.modified.nonEmpty) "<lastmod>" + Escape.html(e.modified) + "</lastmod>" else "") + "</url>"
    }
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" +
      urls.mkString("\n") + "\n</urlset>\n"
  }
}

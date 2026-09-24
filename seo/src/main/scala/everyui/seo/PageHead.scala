package everyui.seo

/** An `SEO` as the tags of a page's `<head>`, as the server writes it: title, description, canonical address,
  * robots, Open Graph and Twitter previews, other forms (a feed, the Markdown copy) and JSON-LD. Every tag but the title
  * is marked `data-everyui-meta`, so the app, setting its own as it moves from page to page (`Platform.setSEO`),
  * replaces them rather than adding a second of each. `absolute` makes a site path a whole URL. */
object PageHead {
  def render(meta: SEO, absolute: String => String): String = {
    def tag(name: String, attributes: (String, String)*): String =
      "<" + name + attributes.map { case (k, v) => " " + k + "=\"" + Escape.attribute(v) + "\"" }.mkString +
        " data-everyui-meta>"
    List(
      Some("<title>" + Escape.html(meta.title) + "</title>"),
      Option.when(meta.description.nonEmpty)(tag("meta", "name" -> "description", "content" -> meta.description)),
      Option.when(meta.canonical.nonEmpty)(tag("link", "rel" -> "canonical", "href" -> absolute(meta.canonical))),
      Option.when(meta.noindex)(tag("meta", "name" -> "robots", "content" -> "noindex")),
      Option.when(meta.siteName.nonEmpty)(tag("meta", "property" -> "og:site_name", "content" -> meta.siteName)),
      Some(tag("meta", "property" -> "og:title", "content" -> meta.title)),
      Option.when(meta.description.nonEmpty)(tag("meta", "property" -> "og:description", "content" -> meta.description)),
      Some(tag("meta", "property" -> "og:type", "content" -> meta.kind)),
      Option.when(meta.canonical.nonEmpty)(tag("meta", "property" -> "og:url", "content" -> absolute(meta.canonical))),
      Option.when(meta.image.nonEmpty)(tag("meta", "property" -> "og:image", "content" -> absolute(meta.image))),
      Some(tag("meta", "name" -> "twitter:card", "content" -> (if (meta.image.nonEmpty) "summary_large_image" else "summary")))
    ).flatten.mkString("\n  ") +
      meta.alternates.map(a => "\n  " + tag("link", "rel" -> "alternate", "type" -> a.contentType, "href" -> a.href,
        "title" -> a.title)).mkString +
      meta.jsonLd.map(json => "\n  <script type=\"application/ld+json\" data-everyui-meta>" + Escape.script(json) + "</script>").mkString
  }
}

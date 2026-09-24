package everyui.seo

/** What a page says about itself to whatever reads it rather than looks at it — a search engine, a link preview, an
  * answer engine: its title, a description, where it canonically lives, a picture for previews, its Open Graph kind,
  * structured data (JSON-LD, each a JSON object as text), other forms of it (`alternates`: a feed, a Markdown copy), and
  * whether it belongs in search results at all.
  *
  * The same value serves both ends: an app's server writes it into the page it sends (`everyui.seo.PageHead`), so a
  * crawler that runs no script reads it, and a screen says it (`Screen.seo`) as it is shown, which the app writes into
  * the head (`Platform.setSEO`), so the address bar's page and its head agree. `title` is the whole title, as a tab
  * shows it. */
case class SEO(title: String,
               description: String = "",
               canonical: String = "",
               image: String = "",
               kind: String = "website",
               siteName: String = "",
               jsonLd: List[String] = Nil,
               alternates: List[Alternate] = Nil,
               noindex: Boolean = false)

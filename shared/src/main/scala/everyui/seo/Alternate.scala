package everyui.seo

/** Another form of a page: its address, its media type ("application/rss+xml" for a feed, "text/markdown" for the
  * page as Markdown, which an answer engine reads more cheaply than the page) and a title for it. */
case class Alternate(href: String, contentType: String, title: String = "")

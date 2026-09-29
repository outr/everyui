package everyui.markdown

/** Where an inline link goes, `(href "title")`, and the index just past its `)`. */
private[markdown] case class LinkTarget(href: String, title: String, end: Int)

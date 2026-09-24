package everyui.seo

/** A page as the site's indexes list it: its path, title and description, when it last changed (ISO 8601 date or time,
  * empty if unknown), the section `llms.txt` files it under, and where its Markdown form is (empty for none). */
case class IndexEntry(path: String,
                      title: String,
                      description: String = "",
                      modified: String = "",
                      section: String = "",
                      markdown: String = "")

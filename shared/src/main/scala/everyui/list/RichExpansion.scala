package everyui.list

/** What a `RichList`'s item shows under its title while it is expanded (`RichList.expansion`): its whole `text`,
  * wrapped and never cut short, as Markdown (`MarkdownView`) unless `markdown` is off, and its `actions` as buttons
  * under it, in order (Pin, Delete), a `danger`ous one in the danger colour. */
case class RichExpansion(text: String, actions: List[RichAction] = Nil, markdown: Boolean = true)

package everyui.markdown

/** A link reference definition, `[label]: href "title"`: `label` normalised (lower case, its spaces collapsed). */
case class MdReference(label: String, href: String, title: String)

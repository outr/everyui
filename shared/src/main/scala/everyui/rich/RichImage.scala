package everyui.rich

/** A picture in a rich document: where it is shown from (an address, or one the app made for bytes it holds:
  * `Files.source`), its words for whatever cannot see it, and the size it is drawn at, where it is given one (else its
  * own, kept within the editor's width). */
case class RichImage(source: String, alt: String = "", width: Option[Double] = None, height: Option[Double] = None)

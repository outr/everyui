package everyui.monaco

/** A model's text kept on the app's side, edit by edit, so reading it costs nothing and the guest never sends the
  * whole text for a keystroke. */
class TextMirror(initial: String, version: Int) {
  private var text: String = initial
  private var at: Int = version

  def value: String = text
  def versionId: Int = at

  /** `change` applied. The edits refer to the text before any of them and do not overlap, so they are made from the
    * end of the text back. */
  def apply(change: MonacoContentChange): String = {
    change.value match {
      case Some(whole) => text = whole
      case None =>
        val out = new java.lang.StringBuilder(text)
        change.changes.sortBy(c => -c.rangeOffset).foreach { c =>
          val from = math.max(0, math.min(c.rangeOffset, out.length))
          val to = math.max(from, math.min(from + c.rangeLength, out.length))
          out.replace(from, to, c.text)
        }
        text = out.toString
    }
    at = change.versionId
    text
  }

  /** The text replaced whole, as the guest has it now. */
  def reset(value: String, version: Int): Unit = {
    text = value
    at = version
  }
}

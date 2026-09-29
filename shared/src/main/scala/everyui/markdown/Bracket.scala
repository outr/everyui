package everyui.markdown

/** A `[` or `![` that might open a link or an image (`Inlines`): its piece of text, where the words after it start in
  * the source, the emphasis delimiter on top of the stack when it came, and whether a link may still start there (a
  * link inside a link may not). */
private[markdown] class Bracket(val piece: Piece, val image: Boolean, val from: Int, val bottom: Delimiter,
                                val before: Bracket) {
  var active: Boolean = true
}

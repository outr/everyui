package everyui.markdown

/** A run of `*`, `_` or `~` that might open or close emphasis (`Inlines`): its piece of text, the character, how many
  * are left of it and were at first, whether it can open and close, and its neighbours on the stack. */
private[markdown] class Delimiter(val piece: Piece, val char: Char, var count: Int, val canOpen: Boolean,
                                  val canClose: Boolean) {
  val original: Int = count
  var prev: Delimiter = null
  var next: Delimiter = null
}

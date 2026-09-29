package everyui.markdown

/** A piece of a paragraph while `Inlines` parses it, in a list linked both ways, so what emphasis and links take in
  * can be lifted out from between two pieces. */
private[markdown] class Piece(var inline: MdInline) {
  var prev: Piece = null
  var next: Piece = null
}

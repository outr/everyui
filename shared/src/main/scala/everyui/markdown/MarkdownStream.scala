package everyui.markdown

/** A Markdown document whose text changes, most often by growing at its end (an answer streaming in), parsed again
  * only from where the change could reach: from the last block for text added at the end (a paragraph running on, a
  * fence still open, a list gaining an item), from the block before the change for any other edit. The blocks before
  * that are kept as they were, the same objects, so what shows them need not be made again. Where the change brings
  * a new link reference definition, everything is parsed again, since a link anywhere may use it.
  *
  * {{{
  *   val doc = new MarkdownStream
  *   doc.update("# Title\n\nSome wor")
  *   val from = doc.update("# Title\n\nSome words.")   // 1: the heading is kept
  *   doc.blocks
  * }}} */
class MarkdownStream {
  private var source: String = ""
  private var parsed: Vector[MdBlock] = Vector.empty
  private var offsets: Vector[Int] = Vector.empty
  private var refs: Map[String, MdReference] = Map.empty

  /** The document's blocks, as the text last given has them. */
  def blocks: Vector[MdBlock] = parsed

  /** The text last given. */
  def text: String = source

  /** Take `text` as the document now: the index of the first block that may differ from before (every block before
    * it is the one that was there). */
  def update(text: String): Int =
    if (text == source) parsed.size
    else {
      val appended = text.startsWith(source)
      val changed = if (appended) source.length else common(source, text)
      var k = 0
      while (k + 1 < offsets.size && offsets(k + 1) <= changed) k += 1
      val from = if (parsed.isEmpty) 0 else if (appended) k else math.max(0, k - 1)
      val kept = parsed.take(from)
      val start = if (from < offsets.size) offsets(from) else 0
      val lines = Markdown.lines(text.substring(start))
      val run = BlockParser.parse(lines.lines)
      val before = Markdown.references(kept.toList, Map.empty)
      val now = Markdown.references(run.blocks.toList, before)
      source = text
      if (now != refs && from > 0) {
        // a new definition may change a link in any block: all of it again
        refs = Map.empty
        parsed = Vector.empty
        offsets = Vector.empty
        source = ""
        update(text)
        0
      }
      else {
        refs = now
        parsed = kept ++ run.blocks.map(b => Markdown.resolve(b, now))
        offsets = offsets.take(from) ++ run.starts.map(i => start + lines.starts(i))
        from
      }
    }

  /** How many characters `a` and `b` start with in common. */
  private def common(a: String, b: String): Int = {
    var k = 0
    val n = math.min(a.length, b.length)
    while (k < n && a.charAt(k) == b.charAt(k)) k += 1
    k
  }
}

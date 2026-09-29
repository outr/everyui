package everyui.markdown

/** Blocks as `BlockParser` finds them: each with the index of the line it starts on, and whether a blank line stands
  * between any two of them (which makes the list item they are in loose). */
private[markdown] case class BlockRun(blocks: Vector[MdBlock], starts: Vector[Int], gapped: Boolean)

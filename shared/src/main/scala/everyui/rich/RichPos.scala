package everyui.rich

/** A place in a rich document: a block, and how many characters into it (a picture counting as one). */
case class RichPos(block: Int, offset: Int) extends Ordered[RichPos] {
  def compare(that: RichPos): Int = if (block != that.block) block.compare(that.block) else offset.compare(that.offset)
}

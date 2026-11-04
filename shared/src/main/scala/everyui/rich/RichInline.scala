package everyui.rich

/** A piece of a block's words: a run of text with its marks, or a picture among them. A picture counts as one
  * character of its block, as the caret moves past it. */
sealed trait RichInline {
  /** How many characters it counts as. */
  def length: Int
  def marks: Set[Mark]
}

object RichInline {
  /** Words styled by `marks`. A line break inside a block is a "\n" among them. */
  case class Text(text: String, marks: Set[Mark] = Set.empty) extends RichInline {
    def length: Int = text.length
  }

  /** A picture among the words, a link when its marks have one. */
  case class Image(image: RichImage, marks: Set[Mark] = Set.empty) extends RichInline {
    def length: Int = 1
  }

  /** What stands for a picture in a block's text (`RichBlock.text`): the object replacement character. */
  val imageChar: Char = '￼'
}

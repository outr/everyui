package everyui.rich

/** What a block of a rich document is. A list item's `depth` is how far it is nested, 0 outermost. */
sealed trait BlockKind

object BlockKind {
  case object Paragraph extends BlockKind
  /** A heading, `level` 1 to 3. */
  case class Heading(level: Int) extends BlockKind
  case class Bullet(depth: Int = 0) extends BlockKind
  case class Numbered(depth: Int = 0) extends BlockKind
  case object Quote extends BlockKind
  /** Code as it is written, its words unmarked, in `language` where it names one. */
  case class Code(language: String = "") extends BlockKind
  /** A line across: it holds no words. */
  case object Rule extends BlockKind
  /** A picture standing as a block of its own: it holds no words. */
  case class Picture(image: RichImage) extends BlockKind

  /** Whether a block of this kind holds words. */
  def holdsWords(kind: BlockKind): Boolean = kind match {
    case Rule | Picture(_) => false
    case _ => true
  }

  /** A list item's depth, None for any other block. */
  def depth(kind: BlockKind): Option[Int] = kind match {
    case Bullet(d) => Some(d)
    case Numbered(d) => Some(d)
    case _ => None
  }
}

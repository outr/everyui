package everyui.rich

import everyui.rich.RichInline.{Image, Text}

/** A block of a rich document: what it is (`kind`) and its words, a run at a time, in order. A block that holds no
  * words (a rule, a picture of its own) has none, and is 0 characters long. */
case class RichBlock(kind: BlockKind, inlines: Vector[RichInline] = Vector.empty) {
  /** How many characters it holds, a picture counting as one. */
  def length: Int = inlines.foldLeft(0)(_ + _.length)

  /** Its words, each picture among them as `RichInline.imageChar`: offsets into it are offsets into the block. */
  def text: String = inlines.map {
    case Text(t, _) => t
    case Image(_, _) => RichInline.imageChar.toString
  }.mkString

  /** The runs from `from` to `to`, cut where they fall inside one. */
  def slice(from: Int, to: Int): Vector[RichInline] = {
    var at = 0
    var out = Vector.empty[RichInline]
    inlines.foreach { i =>
      val start = at
      val end = at + i.length
      val a = math.max(from, start)
      val b = math.min(to, end)
      if (b > a) i match {
        case Text(t, m) => out = out :+ Text(t.substring(a - start, b - start), m)
        case image => out = out :+ image
      }
      at = end
    }
    out
  }

  /** The block with `from` to `to` replaced by `pieces`, its runs joined again where they are styled alike. */
  def replace(from: Int, to: Int, pieces: Vector[RichInline]): RichBlock =
    copy(inlines = RichBlock.normal(slice(0, from) ++ pieces ++ slice(to, length)))

  /** What typing at `offset` is styled by: the marks of the character before it, or after it at the start; a link
    * only from inside it, so words typed at a link's end are not part of the link. */
  def marksAt(offset: Int): Set[Mark] = {
    val before = if (offset > 0) slice(offset - 1, offset).headOption.map(_.marks) else None
    val after = slice(offset, offset + 1).headOption.map(_.marks)
    before match {
      case Some(m) => m.filter {
        case Mark.Link(href) => after.exists(_.contains(Mark.Link(href)))
        case _ => true
      }
      case None => after.getOrElse(Set.empty).filterNot(_.isInstanceOf[Mark.Link])
    }
  }

  /** The marks every character from `from` to `to` has; at a caret (`from == to`), those typing there takes. */
  def marksIn(from: Int, to: Int): Set[Mark] =
    if (from >= to) marksAt(from)
    else {
      val pieces = slice(from, to)
      if (pieces.isEmpty) Set.empty else pieces.map(_.marks).reduce(_ intersect _)
    }

  /** Each character from `from` to `to` with its marks changed by `f`. */
  def remark(from: Int, to: Int, f: Set[Mark] => Set[Mark]): RichBlock =
    replace(from, to, slice(from, to).map {
      case Text(t, m) => Text(t, f(m))
      case Image(image, m) => Image(image, f(m))
    })
}

object RichBlock {
  /** A paragraph of plain words. */
  def paragraph(words: String = ""): RichBlock =
    RichBlock(BlockKind.Paragraph, if (words.isEmpty) Vector.empty else Vector(Text(words)))

  /** Runs with the empty ones gone and the neighbours styled alike joined. */
  def normal(pieces: Vector[RichInline]): Vector[RichInline] =
    pieces.foldLeft(Vector.empty[RichInline]) { (out, i) =>
      i match {
        case Text("", _) => out
        case Text(t, m) => out.lastOption match {
          case Some(Text(u, n)) if n == m => out.init :+ Text(u + t, m)
          case _ => out :+ i
        }
        case image => out :+ image
      }
    }
}

package everyui.rich

import everyui.rich.RichInline.{Image, Text}

/** A rich document: its blocks in order, never none (an empty document is one empty paragraph). Every change makes a
  * new document and says where the caret goes, so an editor's undo keeps documents, and the same edits run alike on
  * every platform. Positions are `RichPos`: a block and an offset into it, a picture counting as one character. */
case class RichDoc(blocks: Vector[RichBlock]) {
  /** The block at `i`, kept within the document. */
  def block(i: Int): RichBlock = blocks(math.max(0, math.min(i, blocks.length - 1)))

  /** `at` kept within the document: a block that is there, an offset within it. */
  def clamp(at: RichPos): RichPos = {
    val b = math.max(0, math.min(at.block, blocks.length - 1))
    RichPos(b, math.max(0, math.min(at.offset, blocks(b).length)))
  }

  /** The document's start and end. */
  def start: RichPos = RichPos(0, 0)
  def end: RichPos = RichPos(blocks.length - 1, blocks.last.length)

  /** Whether it holds nothing: one empty paragraph. */
  def isEmpty: Boolean = blocks.length == 1 && blocks.head.kind == BlockKind.Paragraph && blocks.head.length == 0

  /** Its words alone, a line for each block, a picture as its words for whatever cannot see it. */
  def plain: String = blocks.map { b =>
    b.kind match {
      case BlockKind.Picture(image) => image.alt
      case BlockKind.Rule => ""
      case _ => b.inlines.map {
        case Text(t, _) => t
        case Image(image, _) => image.alt
      }.mkString
    }
  }.mkString("\n")

  /** As Markdown: CommonMark with GitHub's strikethrough, an underline as `<u>…</u>` and a picture with a size as
    * `<img>`, which `RichDoc.fromMarkdown` reads back to the same document. */
  def markdown: String = RichMarkdown.write(this)

  /** As HTML, for what takes it (an email's body): the same blocks and marks, every word and address escaped. */
  def html: String = RichHtml.write(this)

  /** Where `at` falls in `markdown`, in characters: the start of the words it is before. */
  def markdownOffset(at: RichPos): Int = RichMarkdown.offset(this, clamp(at))

  /** The runs from `range`'s start to its end, as a document of their own (what a copy takes). */
  def slice(range: RichRange): RichDoc = {
    val (s, e) = (clamp(range.start), clamp(range.end))
    if (s.block == e.block) RichDoc(Vector(blocks(s.block).copy(inlines = blocks(s.block).slice(s.offset, e.offset))))
    else RichDoc(
      Vector(blocks(s.block).copy(inlines = blocks(s.block).slice(s.offset, blocks(s.block).length))) ++
        blocks.slice(s.block + 1, e.block) :+
        blocks(e.block).copy(inlines = blocks(e.block).slice(0, e.offset)))
  }

  /** `range` replaced by `pieces`, inline, the caret after them. Across blocks, the first block keeps its kind and
    * takes the last one's words after the range. */
  def replace(range: RichRange, pieces: Vector[RichInline]): (RichDoc, RichPos) = {
    val (cut, at) = delete(range)
    val b = cut.blocks(at.block)
    if (!BlockKind.holdsWords(b.kind)) (cut, at)
    else {
      val kept = b.kind match {
        case BlockKind.Code(_) => pieces.map {
          case Text(t, _) => Text(t)
          case image => image
        }
        case _ => pieces
      }
      val n = kept.foldLeft(0)(_ + _.length)
      (cut.set(at.block, b.replace(at.offset, at.offset, kept)), RichPos(at.block, at.offset + n))
    }
  }

  /** `words` typed at `range`, styled by `marks`. */
  def typed(range: RichRange, words: String, marks: Set[Mark]): (RichDoc, RichPos) =
    replace(range, Vector(Text(words, marks)))

  /** `range` taken out, the blocks it spans joined; the caret where it began. */
  def delete(range: RichRange): (RichDoc, RichPos) = {
    val (s, e) = (clamp(range.start), clamp(range.end))
    if (s == e) (this, s)
    else if (s.block == e.block) (set(s.block, blocks(s.block).replace(s.offset, e.offset, Vector.empty)), s)
    else {
      val first = blocks(s.block)
      val last = blocks(e.block)
      val joined =
        if (!BlockKind.holdsWords(first.kind)) last.copy(inlines = last.slice(e.offset, last.length))
        else first.copy(inlines = RichBlock.normal(first.slice(0, s.offset) ++ last.slice(e.offset, last.length)))
      (RichDoc.of(blocks.take(s.block) ++ Vector(joined) ++ blocks.drop(e.block + 1)), RichPos(s.block, s.offset))
    }
  }

  /** The block at `at` split there (Enter): the words after it in a new block below, of the same kind where it is a
    * list item, a quote or code, a paragraph after a heading. Enter in an empty list item ends the list instead: the
    * item becomes a paragraph. */
  def split(range: RichRange): (RichDoc, RichPos) = {
    val (cut, at) = delete(range)
    val b = cut.blocks(at.block)
    b.kind match {
      case BlockKind.Bullet(_) | BlockKind.Numbered(_) if b.length == 0 =>
        (cut.set(at.block, b.copy(kind = BlockKind.Paragraph)), at)
      case BlockKind.Rule | BlockKind.Picture(_) =>
        (RichDoc.of(cut.blocks.patch(at.block + 1, Vector(RichBlock.paragraph()), 0)), RichPos(at.block + 1, 0))
      case kind =>
        val next = kind match {
          case BlockKind.Heading(_) => BlockKind.Paragraph
          case k => k
        }
        val before = b.copy(inlines = b.slice(0, at.offset))
        val after = RichBlock(next, b.slice(at.offset, b.length))
        (RichDoc.of(cut.blocks.patch(at.block, Vector(before, after), 1)), RichPos(at.block + 1, 0))
    }
  }

  /** Backspace at `at` with nothing chosen: the character before it gone; at a block's start, a list item outdented
    * (a paragraph from the outermost), any other block joined to the one above, a rule or a picture above taken out. */
  def backspace(at: RichPos): (RichDoc, RichPos) = {
    val p = clamp(at)
    val b = blocks(p.block)
    if (p.offset > 0) delete(RichRange(RichPos(p.block, p.offset - 1), p))
    else BlockKind.depth(b.kind) match {
      case Some(d) if d > 0 => (indent(RichRange.at(p), -1), p)
      case Some(_) => (set(p.block, b.copy(kind = BlockKind.Paragraph)), p)
      case None if b.kind != BlockKind.Paragraph && BlockKind.holdsWords(b.kind) =>
        (set(p.block, b.copy(kind = BlockKind.Paragraph)), p)
      case None if p.block == 0 => (this, p)
      case None =>
        val above = blocks(p.block - 1)
        if (!BlockKind.holdsWords(above.kind))
          (RichDoc.of(blocks.patch(p.block - 1, Vector.empty, 1)), RichPos(p.block - 1, 0))
        else delete(RichRange(RichPos(p.block - 1, above.length), p))
    }
  }

  /** Each block `range` touches made `kind`, where both hold words; a list item keeps its depth as it changes kind. */
  def setKind(range: RichRange, kind: BlockKind): RichDoc = {
    val (s, e) = (clamp(range.start), clamp(range.end))
    RichDoc.of(blocks.zipWithIndex.map { case (b, i) =>
      if (i < s.block || i > e.block || !BlockKind.holdsWords(b.kind) || !BlockKind.holdsWords(kind)) b
      else {
        val depth = BlockKind.depth(b.kind).getOrElse(0)
        b.copy(kind = kind match {
          case BlockKind.Bullet(_) => BlockKind.Bullet(depth)
          case BlockKind.Numbered(_) => BlockKind.Numbered(depth)
          case k => k
        }, inlines = kind match {
          case BlockKind.Code(_) => RichBlock.normal(b.inlines.map {
            case Text(t, _) => Text(t)
            case image => image
          })
          case _ => b.inlines
        })
      }
    })
  }

  /** Each list item `range` touches nested `by` further in (out where negative), from 0 to `RichDoc.deepest`. */
  def indent(range: RichRange, by: Int): RichDoc = {
    val (s, e) = (clamp(range.start), clamp(range.end))
    def to(d: Int): Int = math.max(0, math.min(RichDoc.deepest, d + by))
    RichDoc.of(blocks.zipWithIndex.map { case (b, i) =>
      if (i < s.block || i > e.block) b
      else b.kind match {
        case BlockKind.Bullet(d) => b.copy(kind = BlockKind.Bullet(to(d)))
        case BlockKind.Numbered(d) => b.copy(kind = BlockKind.Numbered(to(d)))
        case _ => b
      }
    })
  }

  /** The marks every character in `range` has; at a caret, those typing there takes. */
  def marksIn(range: RichRange): Set[Mark] = {
    val (s, e) = (clamp(range.start), clamp(range.end))
    if (s == e) blocks(s.block).marksAt(s.offset)
    else {
      val parts = (s.block to e.block).flatMap { i =>
        val b = blocks(i)
        val from = if (i == s.block) s.offset else 0
        val to = if (i == e.block) e.offset else b.length
        if (to > from) Some(b.marksIn(from, to)) else None
      }
      if (parts.isEmpty) Set.empty else parts.reduce(_ intersect _)
    }
  }

  /** `mark` put on every character in `range`, or taken off where all of them have it already (a toolbar's toggle).
    * Code takes the others off, and they take code off. */
  def toggleMark(range: RichRange, mark: Mark): RichDoc = {
    val on = !marksIn(range).contains(mark)
    remark(range, m =>
      if (!on) m - mark
      else if (mark == Mark.Code) m.filter(_.isInstanceOf[Mark.Link]) + mark
      else (m - Mark.Code) + mark)
  }

  /** Every character in `range` linked to `href`, or no longer linked where None. */
  def setLink(range: RichRange, href: Option[String]): RichDoc =
    remark(range, m => m.filterNot(_.isInstanceOf[Mark.Link]) ++ href.map(Mark.Link(_)))

  /** Each character in `range` with its marks changed by `f` (not in code blocks, whose words are unmarked). */
  def remark(range: RichRange, f: Set[Mark] => Set[Mark]): RichDoc = {
    val (s, e) = (clamp(range.start), clamp(range.end))
    RichDoc.of(blocks.zipWithIndex.map { case (b, i) =>
      if (i < s.block || i > e.block) b
      else b.kind match {
        case BlockKind.Code(_) => b
        case _ => b.remark(if (i == s.block) s.offset else 0, if (i == e.block) e.offset else b.length, f)
      }
    })
  }

  /** A picture put in at `range`: among the words, or as a block of its own below the one it is in (`block`). */
  def insertImage(range: RichRange, image: RichImage, block: Boolean = false): (RichDoc, RichPos) =
    if (!block) replace(range, Vector(Image(image, marksIn(RichRange.at(clamp(range.start))))))
    else {
      val (cut, at) = delete(range)
      val (two, below) = cut.split(RichRange.at(at))
      val placed = RichDoc.of(two.blocks.patch(below.block, Vector(RichBlock(BlockKind.Picture(image))), 0))
      (placed, RichPos(below.block + 1, 0))
    }

  /** Another document put in at `range` (a paste of several blocks): its first block's words join the block the range
    * starts in, its last block takes the words after the range, and those between stand as they are. */
  def insert(range: RichRange, other: RichDoc): (RichDoc, RichPos) =
    if (other.blocks.length == 1 && BlockKind.holdsWords(other.blocks.head.kind))
      replace(range, other.blocks.head.inlines)
    else {
      val (cut, at) = delete(range)
      val b = cut.blocks(at.block)
      val head = b.copy(inlines = b.slice(0, at.offset))
      val tail = b.copy(inlines = b.slice(at.offset, b.length))
      val first = other.blocks.head
      val last = other.blocks.last
      val joinedHead = if (BlockKind.holdsWords(first.kind) && BlockKind.holdsWords(b.kind))
        Vector(head.copy(inlines = RichBlock.normal(head.inlines ++ first.inlines))) else Vector(head, first)
      val middle = other.blocks.slice(1, other.blocks.length - 1)
      val joinedTail = if (BlockKind.holdsWords(last.kind))
        Vector(last.copy(inlines = RichBlock.normal(last.inlines ++ tail.inlines))) else Vector(last, tail)
      val put = joinedHead ++ middle ++ joinedTail
      val caretBlock = at.block + put.length - 1
      val caret = RichPos(caretBlock, if (BlockKind.holdsWords(last.kind)) last.length else 0)
      (RichDoc.of(cut.blocks.patch(at.block, put, 1)), caret)
    }

  private def set(i: Int, b: RichBlock): RichDoc = RichDoc.of(blocks.updated(i, b))
}

object RichDoc {
  /** How far a list may be nested. */
  val deepest: Int = 6

  /** One empty paragraph. */
  val empty: RichDoc = RichDoc(Vector(RichBlock.paragraph()))

  /** `blocks` as a document: one empty paragraph where there are none. */
  def of(blocks: Vector[RichBlock]): RichDoc = if (blocks.isEmpty) empty else RichDoc(blocks)

  /** Paragraphs of plain words, a line each. */
  def plain(words: String): RichDoc = of(words.split("\n", -1).toVector.map(RichBlock.paragraph))

  /** A document read from Markdown, as `markdown` writes it, and as people write it: what this model has no place for
    * (a table, raw HTML) kept as the words it is. */
  def fromMarkdown(source: String): RichDoc = RichMarkdown.read(source)
}

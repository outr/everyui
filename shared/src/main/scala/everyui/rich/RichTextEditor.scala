package everyui.rich

import everyui.async.Task
import everyui.component.{Container, TextView}
import everyui.component.types.Semantics
import everyui.i18n.Words
import everyui.theme.Theme
import everyui.focus.Focus
import everyui.render.Platform
import everyui.text.TextArea
import reactify.*

/** A rich document edited in place (`RichDoc`): words with their marks, headings, lists, quotes, code, rules and
  * pictures, on the platform's own editing surface (`Platform.richEditing`: the web's), so its caret, its selection,
  * its input methods, its spell check and its dictation are the platform's. Every change it says (`RichInput`) is made
  * to the document here, the same on every platform, and drawn again; the platform never edits the document itself.
  * The app draws its own toolbar: the commands (`toggleMark`, `setLink`, `setKind`, `indent`, `insertImage`, `undo`,
  * `redo`) and what is on where the caret is (`marks`, `blockKind`) are for it.
  *
  * Its keys: Primary+B, I and U a mark, Primary+K asks the app for a link (`linkAsked`), Primary+Z and Primary+Shift+Z
  * (or Y) undo and redo, Tab and Shift+Tab nest a list item further in and out (elsewhere they move the focus on),
  * Enter splits a block (an empty list item ends its list) and Shift+Enter breaks a line. Typed at a paragraph's start,
  * "- " makes a bulleted item, "1. " a numbered one, "> " a quote and "# " to "### " a heading.
  *
  * A paste of HTML is read as the document's blocks and marks (`RichDoc.fromHtml`), else as plain lines, through
  * `pasting` where the app gives one; a picture pasted or dropped is handed to `pictures` for an address to show it
  * from (by default its bytes as a source of their own: `Files.source`). It is as tall as what it holds between
  * `minHeight` and `maxHeight` with `grow`, scrolling beyond; `minHeight` without.
  *
  * Where the platform has no editing surface of its own yet (`Platform.richEditing` false: everywhere but the web, for
  * now), the document is edited as its Markdown in a text area, read back as it changes.
  *
  * {{{
  *   val editor = new RichTextEditor(RichDoc.fromMarkdown(draft), width = 560.0)
  *   bold.selected := editor.marks().contains(Mark.Bold)
  *   bold.event.click.attach(_ => editor.toggleMark(Mark.Bold))
  *   send(editor.document().markdown, editor.document().html)
  * }}} */
class RichTextEditor(initial: RichDoc = RichDoc.empty, width: Double = 480.0, val minHeight: Double = 96.0,
                     val maxHeight: Double = 360.0, grow: Boolean = true) extends Container {
  private val start: RichDoc = initial
  /** The document, both ways: what is edited, and what the app sets. */
  val document: Var[RichDoc] = own(Var(start))
  /** What is chosen, or the caret, in the document's places. */
  val selection: Var[RichRange] = own(Var(RichRange.at(RichPos(0, 0))))
  /** Shows the document, to be chosen and copied, but not changed. */
  val readOnly: Var[Boolean] = own(Var(false))
  /** What shows while it is empty. */
  val placeholder: Var[String] = own(Var(""))
  /** How it is drawn: the theme's, followed as it changes, until the app sets its own. */
  val look: Var[RichLook] = own(Var(RichLook.themed()))
  /** A pasted document made what the app wants it to be (marks taken off, pictures left out) before it lands; None
    * lets it land as it is. */
  val pasting: Var[Option[RichDoc => RichDoc]] = own(Var(None))
  /** A picture pasted or dropped (its type and bytes), made a picture to show: stored by the app, and the address it
    * gives back; None leaves it out. By default the bytes as a source of their own (`Files.source`). */
  val pictures: Var[(String, Array[Byte]) => Task[Option[RichImage]]] =
    own(Var((mime: String, bytes: Array[Byte]) => Task.pure(Some(RichImage(everyui.file.Files.source(bytes, mime))))))
  /** Primary+K, with what is chosen: for the app to ask for an address and `setLink` it. */
  val linkAsked: Channel[RichRange] = Channel[RichRange]
  /** A link pressed: with Primary held while editing, or any press while read-only. */
  val linkPressed: Channel[String] = Channel[String]
  /** A suggestion of what comes next, as Markdown: a reply drafted for an empty message, the end of a sentence being
    * written. It is drawn dimmed after the caret and is never the document's, the caret and the focus left where they
    * are, and a screen reader is told of it. Tab, `acceptSuggestion()`, or the Accept chip where there is no Tab to
    * press (a phone) makes it the document's, one undo taking it back. Anything else typed, deleted or pasted, the
    * caret moved, Escape, or the app setting None, puts it away. Where the platform edits the document as its Markdown
    * (`Platform.richEditing` off), the chip alone offers it, and takes it at the end. */
  val suggestion: Var[Option[String]] = own(Var(None))
  /** Each suggestion taken, its Markdown. */
  val suggestionAccepted: Channel[String] = Channel[String]

  // the marks the next words typed are given, toggled at a caret before they are typed; gone as the caret moves
  private val pendingMarks: Var[Option[Set[Mark]]] = own(Var(None))
  /** The marks where the caret is, or every chosen character has: what a toolbar shows as on. */
  val marks: Val[Set[Mark]] = own(Val(pendingMarks().getOrElse(document().marksIn(selection()))))
  /** The kind of block the caret is in: what a toolbar shows as chosen. */
  val blockKind: Val[BlockKind] = own(Val(document().block(document().clamp(selection().start).block).kind))

  // undo's steps: each document and its selection, `at` the one showing
  private var steps: Vector[(RichDoc, RichRange)] = Vector(start -> RichRange.at(RichPos(0, 0)))
  private var at = 0
  private var lastTyped = 0.0
  private val undoable: Var[Boolean] = own(Var(false))
  private val redoable: Var[Boolean] = own(Var(false))
  /** Whether there is a change to undo, or one undone to redo. */
  val canUndo: Val[Boolean] = undoable
  val canRedo: Val[Boolean] = redoable

  private val rich = Platform.richEditing()
  private val surface: Option[RichSurface] = if (rich) Some(new RichSurface(() => selection())) else None
  private val markdownArea: Option[TextArea] =
    if (rich) None else Some(new TextArea(width = width, grow = grow, minHeight = minHeight, maxHeight = maxHeight))

  size.width @= width
  surface.foreach { s =>
    s.size.width := size.width()
    s.size.height := size.height()
    size.height @= minHeight
    s.node.onRichInput(heard)
    s.node.onRichHeight(h => {
      size.height @= (if (grow) math.max(minHeight, math.min(maxHeight, h)) else minHeight)
    })
    s.node.setRichLook(look(), placeholder(), readOnly())
    look.attach(_ => s.node.setRichLook(look(), placeholder(), readOnly()))
    placeholder.attach(_ => s.node.setRichLook(look(), placeholder(), readOnly()))
    readOnly.attach(_ => s.node.setRichLook(look(), placeholder(), readOnly()))
    s.node.setRichDoc(document(), selection())
    document.attach(d => s.node.setRichDoc(d, selection()))
    children += s
  }
  markdownArea.foreach { area =>
    area.value @= document().markdown
    area.value.attach { v =>
      if (document().markdown != v) {
        // typed: a suggestion offered is put away, as it is on the editor's own surface
        if (suggestion().nonEmpty) suggestion @= None
        document @= RichDoc.fromMarkdown(v)
      }
    }
    document.attach(d => if (area.value() != d.markdown) area.value @= d.markdown)
    size.height := area.size.height()
    children += area
  }
  selection.attach(_ => pendingMarks @= None)

  // ---- a suggestion ----

  // what is drawn of it after the caret: its blocks' words, a line each, while the caret is one
  private def ghostLines: List[String] = suggestion() match {
    case Some(md) if !readOnly() && selection().collapsed => RichDoc.fromMarkdown(md).blocks.map(_.text).toList
    case _ => Nil
  }
  private def drawSuggestion(): Unit = surface.foreach(_.node.setRichGhost(selection().end, ghostLines))
  private def suggested: String = suggestion().map(md => RichDoc.fromMarkdown(md).blocks.map(_.text).mkString(" "))
    .getOrElse("")
  suggestion.attach { s =>
    drawSuggestion()
    // (its own last stop left to the words around it)
    if (s.nonEmpty) {
      val words = suggested.take(240).trim
      Platform.announce(Words.suggestion(words.reverse.dropWhile(".!?。！？".contains(_)).reverse))
    }
  }
  selection.attach(_ => if (suggestion().nonEmpty) drawSuggestion())

  // the Accept chip, where there is no Tab to press (a phone, a tablet) or no suggestion drawn (the document edited as
  // Markdown, where it says what it would put in)
  private val accepting: Boolean = !rich || (Platform.touchSupport() && !Platform.keyboardSupport())
  private val chipShown: Val[Boolean] = own(Val(accepting && suggestion().nonEmpty && !readOnly()))
  private val chipWords = new TextView {
    content := (if (rich) Words.acceptSuggestion else Words.acceptSuggestion + ": " +
      (if (suggested.length > 40) suggested.take(40).trim + "…" else suggested))
    font.size @= 13.0
    color := Theme.onAccent()
    position.left @= 12.0
    position.top @= 5.0
  }
  private val chip = new Container {
    focusable := chipShown()
    visible := chipShown()
    semantics := Semantics.button(Words.acceptSuggestion + ": " + suggested.take(240))
    size.width := chipWords.size.width() + 24.0
    size.height := chipWords.size.height() + 10.0
    radius @= 12.0
    backgroundColor := Theme.accent()
    children += chipWords
    event.click.attach { e =>
      e.consume()
      acceptSuggestion()
      RichTextEditor.this.focus()
    }
  }
  chip.position.left := size.width() - chip.size.width() - 6.0
  chip.position.top := size.height() - chip.size.height() - 6.0
  children += chip

  // ---- what the app does ----

  /** The document's Markdown (`RichDoc.markdown`). */
  def markdown: String = document().markdown
  /** The document's HTML (`RichDoc.html`). */
  def html: String = document().html
  /** The document read from Markdown, as a change undo takes back. */
  def setMarkdown(source: String, atEnd: Boolean = false): Unit = {
    val d = RichDoc.fromMarkdown(source)
    change(d, RichRange.at(if (atEnd) d.end else RichPos(0, 0)), "set")
  }

  /** The suggestion made the document's, after the caret (or at the end, where the document is edited as Markdown), as
    * one change undo takes back. Whether there was one to take. */
  def acceptSuggestion(): Boolean = suggestion() match {
    case Some(md) if !readOnly() =>
      val at = if (rich) selection().end else document().end
      val (d, caret) = document().insert(RichRange.at(at), RichDoc.fromMarkdown(md))
      suggestion @= None
      change(d, RichRange.at(caret), "suggestion")
      suggestionAccepted @= md
      true
    case _ => false
  }

  /** `mark` toggled on what is chosen; at a caret, on the words typed next. */
  def toggleMark(mark: Mark): Unit = {
    val range = selection()
    if (range.collapsed) {
      val now = marks()
      pendingMarks @= Some(
        if (now.contains(mark)) now - mark else if (mark == Mark.Code) Set(Mark.Code) else now + mark)
    }
    else change(document().toggleMark(range, mark), range, "mark")
  }

  /** What is chosen linked to `href`, or not linked where None. */
  def setLink(href: Option[String], range: RichRange = selection()): Unit =
    if (!range.collapsed) change(document().setLink(range, href), range, "link")

  /** The blocks the selection touches made `kind`. */
  def setKind(kind: BlockKind): Unit = change(document().setKind(selection(), kind), selection(), "kind")

  /** The list items the selection touches nested `by` further in, or out. */
  def indent(by: Int): Unit = change(document().indent(selection(), by), selection(), "indent")

  /** A picture put in at the caret: among the words, or as a block of its own. */
  def insertImage(image: RichImage, block: Boolean = false): Unit = {
    val (d, caret) = document().insertImage(selection(), image, block)
    change(d, RichRange.at(caret), "picture")
  }

  /** The last change taken back, or the last undone made again. */
  def undo(): Unit = if (at > 0) {
    at -= 1
    restore()
  }
  def redo(): Unit = if (at < steps.length - 1) {
    at += 1
    restore()
  }

  /** `range` chosen. */
  def select(range: RichRange): Unit = {
    selection @= range
    surface.foreach(_.node.setRichDoc(document(), range))
  }

  // ---- what the person does ----

  private def heard(input: RichInput): Unit = if (!readOnly() || input.isInstanceOf[RichInput.Selected] ||
    input.isInstanceOf[RichInput.LinkPressed]) {
    // a suggestion showing is taken by Tab and put away by Escape; anything else done, but a caret left where it is,
    // puts it away first
    input match {
      case RichInput.Command("indent" | "dismiss") | RichInput.LinkPressed(_) => ()
      case RichInput.Selected(range) if range == selection() => ()
      case _ => if (suggestion().nonEmpty) suggestion @= None
    }
    respond(input)
  }

  private def respond(input: RichInput): Unit = input match {
    case RichInput.Command("indent") if suggestion().nonEmpty => acceptSuggestion()
    case RichInput.Command("dismiss") => suggestion @= None
    case RichInput.Typed(range, words) =>
      val (d, caret) = document().typed(range, words, marks())
      val shortcut = if (words == " " && range.collapsed) shortcutAt(d, caret) else None
      shortcut match {
        case Some((next, place)) => change(next, RichRange.at(place), "kind")
        // a space ends a run of typing, so undo takes back a word at a time
        case None => change(d, RichRange.at(caret), if (words.exists(_.isWhitespace)) "spaced" else "type")
      }
    case RichInput.Deleted(range, backward, _) =>
      val doc = document()
      val (d, caret) =
        if (!range.collapsed) doc.delete(range)
        else if (backward) doc.backspace(range.start)
        else {
          val p = doc.clamp(range.start)
          val b = doc.blocks(p.block)
          if (p.offset < b.length) doc.delete(RichRange(p, RichPos(p.block, p.offset + 1)))
          else if (p.block < doc.blocks.length - 1) doc.delete(RichRange(p, RichPos(p.block + 1, 0)))
          else (doc, p)
        }
      change(d, RichRange.at(caret), "delete")
    case RichInput.Split(range) =>
      val (d, caret) = document().split(range)
      change(d, RichRange.at(caret), "split")
    case RichInput.LineBreak(range) =>
      val (d, caret) = document().typed(range, "\n", marks())
      change(d, RichRange.at(caret), "type")
    case RichInput.Pasted(range, html, text) =>
      val read = if (html.trim.nonEmpty) RichDoc.fromHtml(html) else RichDoc.plain(text)
      val landed = pasting().map(f => f(read)).getOrElse(read)
      val (d, caret) = document().insert(range, landed)
      change(d, RichRange.at(caret), "paste")
    case RichInput.Pictures(range, found) =>
      select(range)
      found.foreach { case (mime, bytes) =>
        pictures()(mime, bytes).map(_.foreach(image => insertImage(image))).start()
      }
    case RichInput.Selected(range) => if (range != selection()) selection @= range
    case RichInput.Command("bold") => toggleMark(Mark.Bold)
    case RichInput.Command("italic") => toggleMark(Mark.Italic)
    case RichInput.Command("underline") => toggleMark(Mark.Underline)
    case RichInput.Command("link") => linkAsked @= selection()
    case RichInput.Command("undo") => undo()
    case RichInput.Command("redo") => redo()
    case RichInput.Command("indent") =>
      if (BlockKind.depth(blockKind()).nonEmpty) indent(1) else Focus.handBack("Tab")
    case RichInput.Command("outdent") =>
      if (BlockKind.depth(blockKind()).nonEmpty) indent(-1) else Focus.handBack("Tab", shift = true)
    case RichInput.Command(_) => ()
    case RichInput.LinkPressed(href) => linkPressed @= href
  }

  // Markdown typed at a paragraph's start, a space after it: the block made what it says, the marks typed taken off
  private def shortcutAt(d: RichDoc, caret: RichPos): Option[(RichDoc, RichPos)] = {
    val b = d.blocks(caret.block)
    if (b.kind != BlockKind.Paragraph) None
    else {
      val typed = b.text.substring(0, caret.offset)
      val made: Option[BlockKind] = typed match {
        case "- " | "* " | "+ " => Some(BlockKind.Bullet())
        case "1. " | "1) " => Some(BlockKind.Numbered())
        case "> " => Some(BlockKind.Quote)
        case "# " => Some(BlockKind.Heading(1))
        case "## " => Some(BlockKind.Heading(2))
        case "### " => Some(BlockKind.Heading(3))
        case _ => None
      }
      made.map { k =>
        val (cut, at) = d.delete(RichRange(RichPos(caret.block, 0), caret))
        (cut.setKind(RichRange.at(at), k), at)
      }
    }
  }

  // a change made: the document and the caret, and a step for undo (typing run together while it goes on)
  private def change(next: RichDoc, caret: RichRange, what: String): Unit = {
    val now = Platform.ticks()
    val joins = what == "type" && lastStep == "type" && now - lastTyped < 1500.0 && at == steps.length - 1 && at > 0
    lastTyped = now
    lastStep = what
    if (joins) steps = steps.updated(at, next -> caret)
    else {
      steps = (steps.take(at + 1) :+ (next -> caret)).takeRight(RichTextEditor.steps)
      at = steps.length - 1
    }
    show(next, caret)
  }
  private var lastStep = ""

  private def restore(): Unit = {
    lastStep = ""
    val (d, s) = steps(at)
    show(d, s)
  }

  private def show(d: RichDoc, caret: RichRange): Unit = {
    selection @= caret
    if (document() != d) document @= d
    else surface.foreach(_.node.setRichDoc(d, caret))
    undoable @= at > 0
    redoable @= at < steps.length - 1
  }

  /** The editor's surface, focused with the caret where it was. */
  override def focus(): Unit = surface.map(_.focus()).getOrElse(markdownArea.foreach(_.focus()))

  override def kind: String = "RichTextEditor"
}

object RichTextEditor {
  /** How many changes undo goes back through. */
  val steps: Int = 200
}

package everyui.chat

import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.{Semantics, TextDirection}
import everyui.event.{Key, KeyEvent, PointerDrag}
import everyui.font.MaterialIcon
import everyui.kit.Mirror
import everyui.theme.Theme
import everyui.Color
import reactify.*

/** A message waiting in a [[Composer]]'s queue ([[DraftQueue]]): a handle to drag it by, the first line of its words
  * (cut short to fit), how many files go with it, and Edit and Remove. It is one stop for the focus, its keys handed
  * to `keys` (`QueueChip.keeps` says which), and a press on it, or Enter, edits it; its buttons are for a pointer or
  * a finger. `index` and `count` are where it stands, for a screen reader. */
class QueueChip(val draft: Draft, edit: () => Unit, remove: () => Unit, keys: KeyEvent => Unit,
                drag: Double => Unit, dropped: () => Unit) extends Container {
  /** Where it stands in the queue, from 0, and how many there are. */
  val index: Var[Int] = own(Var(0))
  val count: Var[Int] = own(Var(1))

  private val points = 13.0
  /** What a pointer or a finger drags it by. */
  val handle: Container = new Container
  private val gripIcon = new MaterialIconView(MaterialIcon.DragIndicator, 16.0)
  private val words = new TextView {
    content @= QueueChip.firstLine(draft)
    // the way its own words run, whichever way the app reads
    direction @= TextDirection.Auto
    font.size @= points
    color := Theme.text()
  }
  private val attached = draft.attachments.length
  private val clip = new MaterialIconView(MaterialIcon.AttachFile, 14.0)
  private val fileCount = TextView(attached.toString, 12.0, Theme.muted())
  /** What edits it and what takes it off the queue. */
  val editButton: Container = QueueChip.button(MaterialIcon.Edit, ChatWords.edit, edit)
  val removeButton: Container = QueueChip.button(MaterialIcon.Close, ChatWords.remove, remove)

  focusable @= true
  size.height @= QueueChip.height
  radius @= 8.0
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Theme.surface())
  borderWidth := (if (focusShown()) 2.0 else 1.0)
  borderColor := (if (focusShown()) Theme.accent() else Theme.border())
  semantics := Semantics.button(ChatWords.queuedItem(index() + 1, count(), QueueChip.said(draft)) + ". " +
    ChatWords.queuedHelp)

  // the handle, a little wider than its glyph so a finger finds it; a press on it drags rather than edits
  handle.size.width @= 24.0
  handle.size.height := size.height()
  handle.tooltip @= ChatWords.reorder
  gripIcon.color := Theme.muted()
  gripIcon.position.left @= 4.0
  gripIcon.position.middle := handle.size.middle()
  handle.children += gripIcon
  handle.event.click.attach(_.consume())
  new PointerDrag(handle, (_, y) => drag(handle.absoluteTop + y), dropped)

  clip.color := Theme.muted()
  clip.visible @= attached > 0
  fileCount.visible @= attached > 0
  // the room at the end: Remove, Edit, then the files
  // a queued message's chip is a line tall, its first line cut short and read whole in its tip (pressed, it is edited)
  words.textOverflow @= everyui.component.types.TextOverflow.Ellipsis
  words.tooltip := (if (words.elided()) words.content() else "")
  words.maxWidth := math.max(20.0, size.width() - 26.0 - 8.0 - 4.0 - 54.0 -
    (if (attached > 0) 6.0 + fileCount.size.width() + 2.0 + 14.0 else 0.0))
  Mirror.place(handle, () => size.width(), () => 0.0)
  Mirror.place(words, () => size.width(), () => 26.0)
  Mirror.place(removeButton, () => size.width(), () => size.width() - 4.0 - 26.0)
  Mirror.place(editButton, () => size.width(), () => size.width() - 4.0 - 26.0 - 2.0 - 26.0)
  Mirror.place(fileCount, () => size.width(), () => size.width() - 64.0 - fileCount.size.width())
  Mirror.place(clip, () => size.width(), () => size.width() - 64.0 - fileCount.size.width() - 2.0 - 14.0)
  words.position.middle := size.middle()
  fileCount.position.middle := size.middle()
  clip.position.middle := size.middle()
  editButton.position.middle := size.middle()
  removeButton.position.middle := size.middle()
  children ++= List(handle, words, clip, fileCount, editButton, removeButton)

  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    edit()
  }
  event.key.down.attach(e => if (QueueChip.keeps(e)) keys(e))

  override def keepsEvent(e: KeyEvent): Boolean = QueueChip.keeps(e) || super.keepsEvent(e)

  override def kind: String = "QueueChip"
}

object QueueChip {
  /** How tall a chip is, and the room between two. */
  val height: Double = 30.0
  val gap: Double = 4.0

  /** The keys a focused chip acts on: Delete and Backspace remove it, E edits it (as Enter does, by pressing it),
    * Alt+Up and Alt+Down move it, and Escape goes back to the field. */
  def keeps(e: KeyEvent): Boolean = {
    val plain = !e.ctrl && !e.meta && !e.alt
    val moving = e.alt && !e.ctrl && !e.meta && !e.shift && (e.key == Key.ArrowUp || e.key == Key.ArrowDown)
    moving || (plain && !e.shift && (e.key == Key.Backspace || e.name == "Delete" || e.key == Key.Escape)) ||
      (plain && (e.name == "e" || e.name == "E"))
  }

  /** What a chip shows of a draft: the first line of its words with any, else the first file's name. */
  def firstLine(d: Draft): String = {
    val text = d.text.trim
    val end = text.indexOf("\n")
    val line = (if (end < 0) text else text.substring(0, end)).trim
    if (line.nonEmpty) line else d.attachments.headOption.map(_.name).getOrElse("")
  }

  /** What a screen reader hears of a draft: its words, and how many files go with it. */
  def said(d: Draft): String = {
    val n = d.attachments.length
    d.text.trim + (if (n == 0) "" else (if (d.text.trim.isEmpty) "" else ", ") + ChatWords.fileCount(n))
  }

  // a small round glyph that does something, for a pointer or a finger: the chip itself is the focus's stop
  private def button(glyph: MaterialIcon, tip: String, action: () => Unit): Container = {
    val b = new Container
    val icon = new MaterialIconView(glyph, 16.0)
    b.size.width @= 26.0
    b.size.height @= 26.0
    b.radius @= 13.0
    b.tooltip @= tip
    b.semantics @= Semantics.button(tip)
    b.backgroundColor := (if (b.event.pointer.over()) Theme.control() else Color.Clear)
    icon.color := (if (b.event.pointer.over()) Theme.text() else Theme.muted())
    icon.position.left @= 5.0
    icon.position.top @= 5.0
    b.children += icon
    b.tapSlop @= true
    b.event.click.attach { e =>
      e.consume()
      action()
    }
    b
  }
}

package everyui.text

import everyui.chat.{Completion, CompletionPopup}
import everyui.component.{Component, InputView}
import everyui.event.KeyEvent
import everyui.render.Platform
import everyui.ui
import reactify.*

/** A field's list of completions ([[everyui.component.TextInput]]'s and [[TextArea]]'s `completions`), worked for
  * `field` over the platform's own field under it (`input`): what is typed (`value`) and where the caret is asked
  * about as the piece being typed (`CompletionToken`), the source's answer listed under the field (or over it where
  * there is no room under it, as on a phone with its keyboard up) as wide as it, and the one chosen put in the piece's
  * place, the caret after it and the field still being typed in, and told on `completed`. `open` follows whether the
  * list shows. It closes as the field loses the focus or stops being typed in, on Escape, when the source answers
  * nothing, and as the field is taken apart (`close`). */
private[everyui] class FieldCompletion(field: Component, input: InputView, value: Var[String], editing: Var[Boolean],
                                       source: Var[Option[CompletionSource]], open: Var[Boolean],
                                       completed: Channel[Completion], points: Double, label: () => String) {
  private val lit: Var[Int] = Var(0)
  private var shown: Vector[Completion] = Vector.empty
  private var popup: Option[CompletionPopup] = None
  // the piece the list is for, and the piece last asked about (a caret moved within it asks nothing again)
  private var token: Option[CompletionToken] = None
  private var asking: Option[CompletionToken] = None
  // the answers asked for so far: an answer for one before the last is dropped
  private var asked = 0
  // what the field holds just after a completion went in, which asks for nothing until it is typed on
  private var quiet: Option[String] = None

  private val reactions: List[() => Unit] = {
    val typed = value.attach(_ => if (source().nonEmpty) Platform.later(() => lookup()))
    val moved = input.selected.attach(_ => if (popup.nonEmpty) Platform.later(() => lookup()))
    val left = field.focused.attach(f => if (!f) close())
    val stopped = editing.attach(e => if (!e) close())
    val changed = source.attach(_ => close())
    val lighting = lit.attach(_ => said())
    List(() => value.reactions -= typed, () => input.selected.reactions -= moved,
      () => field.focused.reactions -= left, () => editing.reactions -= stopped, () => source.reactions -= changed,
      () => lit.reactions -= lighting)
  }
  said()

  /** Whether `e` is a key the list takes while it shows (`CompletionKey`): kept from the platform's field and from the
    * focus layer, so Escape closes the list and nothing else (not Back, not a dialog the field is in). */
  def keeps(e: KeyEvent): Boolean =
    popup.nonEmpty && editing() && !Platform.keyComposing() && CompletionKey.of(e).nonEmpty

  /** `e` done, where the list takes it (`keeps`); whether it did. */
  def key(e: KeyEvent): Boolean = if (!keeps(e)) false else {
    CompletionKey.of(e).get match {
      case CompletionKey.Next => lit @= CompletionKey.step(lit(), shown.length, forward = true)
      case CompletionKey.Previous => lit @= CompletionKey.step(lit(), shown.length, forward = false)
      case CompletionKey.Choose => choose(lit())
      case CompletionKey.Close => close()
    }
    true
  }

  // the piece at the caret asked about, where the field is being typed in and the source wants it asked
  private def lookup(): Unit = source() match {
    case Some(s) if field.focused() && editing() && !quiet.contains(value()) =>
      quiet = None
      val now = value()
      val t = CompletionToken.at(now, math.max(0, math.min(now.length, input.caret)), s.separators)
      if (t.text.trim.length < math.max(1, s.minimum)) close()
      else if (!asking.contains(t)) {
        asked += 1
        val mine = asked
        asking = Some(t)
        s.complete(t.text).map(found => if (mine == asked && field.focused() && editing()) show(t, found)).startUnit()
      }
    case _ => if (!quiet.contains(value())) close()
  }

  private def show(t: CompletionToken, found: Vector[Completion]): Unit = {
    drop()
    if (found.isEmpty) close()
    else {
      token = Some(t)
      shown = found
      lit @= 0
      val rows = source().map(_.rows).getOrElse(0)
      var p = new CompletionPopup(found, lit, field.size.width(), points, rows)
      ui.children += p
      // where neither under the field nor over it has room for its rows (a phone with its keyboard up), as many as fit
      val room = math.max(ui.size.height() - 12.0 - field.absoluteTop - field.size.height(), field.absoluteTop - 12.0)
      val shows = if (rows > 0) math.min(rows, found.length) else found.length
      if (p.size.height() > room && shows > 1) {
        val line = p.rowAt(0).map(_.size.height()).getOrElse(0.0)
        val fit = if (line > 0.0) math.max(1, ((room - (p.size.height() - line * shows)) / line).toInt) else shows
        if (fit < shows) {
          ui.children -= p
          p.dispose()
          p = new CompletionPopup(found, lit, field.size.width(), points, fit)
          ui.children += p
        }
      }
      val list = p
      list.position.left := math.max(0.0, math.min(field.absoluteLeft, ui.size.width() - list.size.width()))
      list.position.top := {
        val top = field.absoluteTop
        val under = top + field.size.height() + 4.0
        val over = top - list.size.height() - 4.0
        val below = ui.size.height() - 8.0 - under
        // under it where it fits there, else over it where it fits, else on the side with more room
        if (list.size.height() <= below || (over < 8.0 && below >= top)) under else math.max(8.0, over)
      }
      list.chosen.attach(i => choose(i))
      popup = Some(list)
      open @= true
      said()
    }
  }

  // the completion at `i` in place of the piece, the caret after it, the field still being typed in
  private def choose(i: Int): Unit = {
    val chosen = if (i >= 0 && i < shown.length) Some(shown(i)) else None
    val at = token
    val s = source()
    close()
    chosen.foreach { c =>
      at.foreach { t =>
        val (next, caret) = CompletionToken.replace(value(), t, c.insert, s.map(_.after).getOrElse(""),
          s.map(_.separators).getOrElse(""))
        quiet = Some(next)
        value @= next
        input.moveCaret(caret)
        Platform.later(() => input.moveCaret(caret))
        field.focus()
        // a click on the list may have taken the platform's field out of typing
        editing @= true
      }
      completed @= c
    }
  }

  /** The list closed, and any answer still to come dropped. */
  def close(): Unit = {
    asked += 1
    asking = None
    token = None
    shown = Vector.empty
    drop()
    if (open()) open @= false
    said()
  }

  private def drop(): Unit = {
    popup.foreach { p =>
      ui.children -= p
      p.dispose()
    }
    popup = None
  }

  // the field said to a screen reader as a combo box, its list the one showing and its lit row the active one
  private def said(): Unit = input.node.setCompletionList(source().nonEmpty, popup.map(_.node),
    popup.flatMap(_.rowAt(lit())).map(_.node),
    () => if (lit() < 0 || lit() >= shown.length) label()
      else shown(lit()).label + shown(lit()).detail.map(d => ", " + d).getOrElse(""))

  /** Closed, and no longer following the field: as it is taken apart. */
  def dispose(): Unit = {
    close()
    reactions.foreach(f => f())
  }
}

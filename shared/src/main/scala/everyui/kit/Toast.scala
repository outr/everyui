package everyui.kit

import everyui.component.*
import everyui.component.types.{Semantics, WhiteSpace}
import everyui.event.{PointerDrag, ScrollPress}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Words
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A short message over the screen for a few seconds, saying what just happened: one at a time, the newest in place of
  * the last. It sits where `placement` says (the foot of the screen, 32 in from it, unless the app says otherwise), or
  * where a call's own `top` and `margin` say, and goes after `seconds`: longer for a long message, longer again where
  * people asked for more time to read (`TimeLimits`), and not while a pointer rests on it. Each message's words pass
  * through `shown` as it shows (`RemoteLog.install(toasts = true)` sends them to the server's log as warnings). A
  * screen reader reads each out as it shows, politely.
  *
  * It keeps out of the way: a top one sits under `topInset` (the app's header), and while a component kept clear
  * (`keepClear`: a chat's composer) is on the screen a bottom one rises above it and a top one sits below it. A tap
  * closes it, and so does a swipe toward its edge; a screen reader activates it to close it.
  *
  * Given an `action` (its words and what it does: "Undo"), a button after the words does it and takes the message
  * away, and the message stays four seconds longer while it is offered; a screen reader hears the action after the
  * words. A tap is the button's alone then, a swipe still closes it, and (where a pointer or a finger is used) a close
  * button after the action closes it too. A toast never takes the focus.
  * {{{
  *   Toast.placement @= ToastPlacement.Top
  *   Toast.topInset := header.size.height()
  *   Toast.keepClear(composer)
  *   Toast.show("Archived", action = Some("Undo" -> (() => unarchive(thread))))
  * }}} */
object Toast {
  private var showing: Option[Container] = None
  private var made = 0
  /** The words of every message shown, as it shows: for an app to keep them (its log, its server's), since a message
    * goes after a few seconds whether anybody read it or not. */
  val shown: Channel[String] = Channel[String]

  /** Where every toast shows, unless its call says (`show`'s `top` and `margin`): set once by the app. */
  val placement: Var[ToastPlacement] = Var(ToastPlacement.Bottom)
  /** How far down the screen a top toast starts at least: the app's header, bound to its height
    * (`Toast.topInset := header.size.height()`). The system's bars and a camera's cutout are already outside the
    * screen the app is laid out in (`Platform.safeArea`), so this is only what the app puts there. */
  val topInset: Var[Double] = Var(0.0)
  /** How far a toast keeps from `topInset` and from what is kept clear. */
  val clearance: Double = 8.0

  // what toasts keep clear of, while each is on the screen
  private val clear: Var[List[Component]] = Var(Nil)

  /** Keep toasts clear of `c` while it is on the screen: a bottom toast rises above it and a top one sits below it,
    * following its size and place. Whether it is at the head or the foot is where its middle is; of several at the
    * foot, a toast clears the highest, and of several at the head, the lowest. It ends as `c` is taken apart
    * (`dispose`), or by the function returned. */
  def keepClear(c: Component): () => Unit = {
    if (!keepsClear(c)) {
      clear @= clear() :+ c
      c.onDispose(() => release(c))
    }
    () => release(c)
  }

  private def release(c: Component): Unit = if (keepsClear(c)) clear @= clear().filterNot(_ eq c)

  /** Whether toasts keep clear of `c` now. */
  def keepsClear(c: Component): Boolean = clear().exists(_ eq c)

  /** Where a toast `height` tall goes down the screen, at its head (`top`) or its foot, `margin` in from that edge,
    * and `clearance` from `topInset` and from what is kept clear (`keepClear`). Read in a binding, it follows them. */
  def place(height: Double, top: Boolean, margin: Double): Double = {
    val screen = ui.size.height()
    val there = clear().filter(c => c.visible() && Focus.showing(c))
    val heads = there.filter(c => c.absoluteTop + c.size.height() / 2.0 < screen / 2.0)
    val feet = there.filterNot(c => heads.exists(_ eq c))
    val at =
      if (top) math.max(margin, heads.map(c => c.absoluteTop + c.size.height()).foldLeft(topInset())(math.max) + clearance)
      else math.min(screen - margin, feet.map(_.absoluteTop).foldLeft(screen)(math.min) - clearance) - height
    math.max(0.0, math.min(at, screen - height))
  }

  def show(words: String, seconds: Double = 4.0, top: Boolean = placement().top, margin: Double = placement().margin,
           action: Option[(String, () => Unit)] = None): Unit = {
    hide()
    made += 1
    val mark = made
    val plate = new Container
    // a close button after the action, where a pointer or a finger is used: what a screen reader closes it with
    val closable = action.nonEmpty && !Platform.remoteNavigation()
    // the action after the words, which does it and takes the message away
    val button: Option[Button] = action.map { case (label, act) =>
      new Button(label, () => {
        hide()
        act()
      }, ButtonStyle.Text)
    }
    val close: Option[IconButton] =
      if (closable) Some(new IconButton(MaterialIcon.Close, Words.close, () => gone(plate))) else None
    val tail: List[Component] = button.toList ++ close.toList
    val text = new TextView {
      whiteSpace @= WhiteSpace.Normal
      // (less the room of what follows the words, where something does)
      size.width := math.min(560.0, ui.size.width() - 160.0 -
        button.map(b => b.size.width() + 12.0).getOrElse(0.0) - close.map(c => c.size.width() - 8.0).getOrElse(0.0))
      font.size @= 14.0
      color := Theme.text()
      content @= words
    }
    text.position.left @= 20.0
    text.position.top := (plate.size.height() - text.size.height()) / 2.0
    button.foreach { b =>
      b.position.left := text.position.left() + text.size.width() + 12.0
      b.position.top := (plate.size.height() - b.size.height()) / 2.0
    }
    close.foreach { c =>
      c.position.left := button.map(b => b.position.left() + b.size.width()).getOrElse(0.0) + 4.0
      c.position.top := (plate.size.height() - c.size.height()) / 2.0
    }
    // how far a swipe has drawn it toward its edge, where the finger went down, and the furthest it went either way
    val pulled: Var[Double] = plate.own(Var(0.0))
    val from: Var[Option[Double]] = plate.own(Var(None))
    val went: Var[Double] = plate.own(Var(0.0))
    // whether the last press on it was a drag, whose click (where the platform makes one) is not a tap
    val dragged: Var[Boolean] = plate.own(Var(false))
    plate.size.width := close.map(c => c.position.left() + c.size.width() + 8.0)
      .orElse(button.map(b => b.position.left() + b.size.width() + 20.0))
      .getOrElse(text.size.width() + 40.0)
    plate.size.height := math.max(text.size.height(), tail.map(_.size.height()).foldLeft(0.0)(math.max)) + 28.0
    plate.backgroundColor := Theme.raised()
    plate.radius @= 8.0
    plate.shadow @= Some(Shadow(0.0, 4.0, 16.0, Color.Black.withAlpha(0.5)))
    plate.children ++= text :: tail
    plate.position.left := (ui.size.width() - plate.size.width()) / 2.0
    plate.position.top := place(plate.size.height(), top, margin) + pulled()
    // a screen reader's way to close one with no action: it is a button that does
    if (action.isEmpty) {
      plate.semantics @= Semantics.button(words + ". " + Words.close)
      plate.event.click.attach(_ => if (!dragged()) gone(plate))
    }
    // a tap closes one with no action, and a swipe toward its edge (further than a third of it, or 24) any
    new PointerDrag(plate, (_, y) => {
      val now = y + plate.absoluteTop
      if (from().isEmpty) {
        from @= Some(now)
        dragged @= false
      }
      val d = now - from().getOrElse(now)
      went @= math.max(went(), math.abs(d))
      pulled @= (if (top) math.min(0.0, d) else math.max(0.0, d))
    }, () => {
      val far = math.abs(pulled())
      val tapped = went() < ScrollPress.slop
      dragged @= !tapped
      from @= None
      went @= 0.0
      if (far >= math.max(24.0, plate.size.height() / 3.0)) gone(plate)
      else if (tapped && action.isEmpty) gone(plate)
      else pulled @= 0.0
    }, take = (x, y) => !tail.exists(b => x >= b.position.left() && x < b.position.left() + b.size.width() &&
      y >= b.position.top() && y < b.position.top() + b.size.height()))
    ui.children += plate
    showing = Some(plate)
    shown @= words
    everyui.access.LiveRegions.say(words + action.map(a => ". " + a._1).getOrElse(""))
    // as long as people are to be given to read it (`TimeLimits`), longer for more words, and kept while a pointer
    // rests on it
    // (and four seconds more while an action is offered, to be read and reached)
    val stays = everyui.time.TimeLimits.scaled(
      math.max(seconds, words.length / 20.0) + (if (action.isEmpty) 0.0 else 4.0))
    def expire(): Unit = if (mark == made) {
      if (plate.event.pointer.hover()) Platform.after(1.0, () => expire()) else hide()
    }
    Platform.after(stays, () => expire())
  }

  // `plate` closed, where it is still the one showing
  private def gone(plate: Container): Unit = if (showing.exists(_ eq plate)) hide()

  /** The message showing taken away now, and taken apart a turn later. */
  def hide(): Unit = {
    showing.foreach { t =>
      ui.children -= Component.heldAside(t)
      Platform.later(() => t.dispose())
    }
    showing = None
  }
}

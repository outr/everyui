package everyui.theme

import everyui.async.Task
import everyui.i18n.Words
import everyui.keymap.{Chord, Command, Keymap}
import everyui.slider.SliderScale
import everyui.store.Stored
import reactify.*

/** How large the whole app is drawn, as a person turns it up or down: everything, not only the words, as a browser's
  * zoom draws a page. `factor` 1.5 draws every component half as large again and lays the app out in a screen that
  * much smaller (`ui.size` is the screen over the factor), so a row 30 high is 45 on the screen with its words at
  * 21, a field still fits its line, and whatever follows `ui.size` reflows as it would for a smaller window.
  *
  * It multiplies what the platform already does (a browser's zoom, the screen's density, the system's display
  * scale), never replaces it, and changes take effect at once. A Roku draws the app enlarged as a picture is, its words
  * a little soft above 1; an Apple TV the same.
  *
  * `remembered` keeps it on the device from one launch to the next; `commands` gives it the keys a browser zooms with;
  * `ScaleControl` (everyui.kit) is − / 100% / + for a settings screen or a menu.
  *
  * {{{
  *   UiScale.remembered()           // as the app starts: the size chosen last time, kept from now on
  *   UiScale.commands(Keymap.global) // Ctrl/Cmd + =, - and 0
  *   settings.children += new ScaleControl
  * }}} */
object UiScale {
  /** The least and most it may be: anything set outside is taken to the nearer. */
  val least: Double = 0.5
  val most: Double = 3.0

  /** The size asked for, 1 as the app is designed. Set outside `least` to `most`, it is taken to the nearer end (by
    * `ui`, which draws it). */
  val factor: Var[Double] = Var(1.0)
  /** The steps the control and the commands take it in: 0.8 to 2.0 by tenths unless the app says. */
  val steps: Var[SliderScale] = Var(SliderScale(0.8, 2.0, 0.1))

  /** The size the app is drawn at: `chosen`, times the text size asked for on the device where it follows that
    * (`system`, `followsSystem`), in range. */
  def apply(): Double = drawnAt(chosen(), if (followsSystem()) system() else 1.0, room())

  /** The size drawn at for the size `chosen` and the device's text size `asked`, on a screen `room` across in the
    * platform's own units (0 unknown): `asked` held to what leaves the app `narrowest` across, never under 1 for that,
    * then the two together, in range. */
  def drawnAt(chosen: Double, asked: Double, room: Double): Double = {
    val most = if (room > 0.0) math.max(1.0, room / (narrowest * chosen)) else asked
    clamp(chosen * math.max(math.min(asked, 1.0), math.min(asked, most)))
  }

  /** The size the app or the person chose (`factor`), in range: what the steps, the commands and `ScaleControl` go
    * by. */
  def chosen(): Double = clamp(factor())

  /** How large the device asks text to be, 1 as it comes: Android's font size, a browser's default text size over
    * 16 pixels; 1 on the desktop, a Roku and an Apple TV, which say nothing of it. Followed as it changes. */
  lazy val system: Var[Double] = {
    val asked = Var(everyui.render.Platform.systemTextScale())
    everyui.render.Platform.watchTextScale(s => if (asked() != s) asked @= s)
    asked
  }
  /** Whether the app is drawn larger as the device asks for larger text (`system`): the whole app, not the words
    * alone, so nothing laid out to its words overflows. On, except where a remote moves the focus: a television's
    * screens are made for its screen, and turn it on themselves. */
  val followsSystem: Var[Boolean] = Var(!everyui.render.Platform.remoteNavigation())
  /** The least the app has across to lay out in for the device's text size to draw it larger (WCAG's reflow, 320). */
  val narrowest: Double = 320.0
  // how wide the screen is in the platform's own units, before any scale here (set by `ui` as it measures it)
  private[everyui] val room: Var[Double] = Var(0.0)

  /** `f` in range: NaN, which no comparison holds for, is 1. */
  def clamp(f: Double): Double =
    if (f >= least && f <= most) f
    else if (f > most) most
    else if (f < least) least
    else 1.0

  /** A step larger, up to the steps' top; nothing past it. */
  def larger(): Unit = {
    val now = chosen()
    val next = neat(steps().snap(now + steps().keyStep))
    if (next > now) factor @= next
  }

  /** A step smaller, down to the steps' bottom; nothing past it. */
  def smaller(): Unit = {
    val now = chosen()
    val next = neat(steps().snap(now - steps().keyStep))
    if (next < now) factor @= next
  }

  // to a millionth: a step counted on from 0.8 is 1.2, not 1.2000000000000002
  private def neat(f: Double): Double = math.floor(f * 1000000.0 + 0.5) / 1000000.0

  /** Back to the size the app is designed at. */
  def reset(): Unit = factor @= 1.0

  /** As a percentage, "100%", "150%". */
  def percent(f: Double = chosen()): String = math.floor(f * 100.0 + 0.5).toInt.toString + "%"

  /** Kept on this device from now on, under `key` in `space`: the size chosen last time is read (and taken, unless it
    * was changed first), and each change after is kept. Off until an app asks; ask once, as the app starts. Answers
    * the size it took, as `Stored.connect` does. */
  def remembered(space: String = "everyui", key: String = "scale"): Task[Option[Double]] =
    Stored.connect[Double](space, key, factor)

  /** `ui.larger`, `ui.smaller` and `ui.defaultSize` in `keymap`, on the keys a browser zooms with: Primary with = or
    * + (Shift held or not, for keyboards that type + with it or without), with -, and with 0. */
  def commands(keymap: Keymap = Keymap.global): Unit = {
    // made whole (the chords a list, the action a function): the Roku's channel ran a run block handed with chords
    // as one more chord
    keymap.add(new Command("ui.larger", Words.larger, List(Chord("=", primary = true),
      Chord("=", primary = true, shift = true), Chord("+", primary = true), Chord("+", primary = true, shift = true)),
      () => larger()))
    keymap.add(new Command("ui.smaller", Words.smaller, List(Chord("-", primary = true)), () => smaller()))
    keymap.add(new Command("ui.defaultSize", Words.defaultSize, List(Chord("0", primary = true)), () => reset()))
  }
}

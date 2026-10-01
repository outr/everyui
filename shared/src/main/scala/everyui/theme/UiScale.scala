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

  /** The size the app is drawn at: `factor`, in range. */
  def apply(): Double = clamp(factor())

  /** `f` in range: NaN, which no comparison holds for, is 1. */
  def clamp(f: Double): Double =
    if (f >= least && f <= most) f
    else if (f > most) most
    else if (f < least) least
    else 1.0

  /** A step larger, up to the steps' top; nothing past it. */
  def larger(): Unit = {
    val now = apply()
    val next = neat(steps().snap(now + steps().keyStep))
    if (next > now) factor @= next
  }

  /** A step smaller, down to the steps' bottom; nothing past it. */
  def smaller(): Unit = {
    val now = apply()
    val next = neat(steps().snap(now - steps().keyStep))
    if (next < now) factor @= next
  }

  // to a millionth: a step counted on from 0.8 is 1.2, not 1.2000000000000002
  private def neat(f: Double): Double = math.floor(f * 1000000.0 + 0.5) / 1000000.0

  /** Back to the size the app is designed at. */
  def reset(): Unit = factor @= 1.0

  /** As a percentage, "100%", "150%". */
  def percent(f: Double = apply()): String = math.floor(f * 100.0 + 0.5).toInt.toString + "%"

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

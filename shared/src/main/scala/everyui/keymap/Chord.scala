package everyui.keymap

import everyui.event.KeyEvent
import everyui.render.Platform

/** A key and the modifiers held with it: `Chord("W", primary = true)`, `Chord("Tab", ctrl = true, shift = true)`,
  * `Chord("Escape")`, or the same written out, `Chord.parse("Primary+Shift+N")`.
  *
  * The key is the web's name for it ("Escape", "Tab", "ArrowUp", "F5", "Space") or the character it types, matched
  * without regard to case, so Shift+N is `Chord("N", shift = true)` whether the platform reports "n" or "N".
  * `primary` is the platform's own shortcut modifier: Command (Meta) where shortcuts are made with it (macOS, iOS),
  * Ctrl everywhere else. A chord matches only with exactly its modifiers held. */
case class Chord(key: String, primary: Boolean = false, ctrl: Boolean = false, meta: Boolean = false,
                 alt: Boolean = false, shift: Boolean = false) {
  /** Whether `e` is this chord. */
  def matches(e: KeyEvent): Boolean = {
    val command = Platform.commandKey()
    e.ctrl == (ctrl || (primary && !command)) && e.meta == (meta || (primary && command)) && e.alt == alt &&
      e.shift == shift && Chord.sameKey(e.name, key)
  }

  /** How a list of shortcuts shows it: "Ctrl+Shift+N", "Cmd+W". */
  def label: String = {
    val command = Platform.commandKey()
    val held = List(
      if (ctrl || (primary && !command)) "Ctrl" else "",
      if (meta || (primary && command)) (if (command) "Cmd" else "Meta") else "",
      if (alt) (if (command) "Option" else "Alt") else "",
      if (shift) "Shift" else "").filter(_.nonEmpty)
    (held :+ (if (key.length == 1) key.toUpperCase else key)).mkString("+")
  }
}

object Chord {
  /** A chord written as its modifiers and key joined by `+`: "Primary+Shift+N", "Ctrl+Tab", "Escape", "Ctrl++". The
    * modifiers are Primary (or Mod), Ctrl (Control), Meta (Cmd, Command), Alt (Option) and Shift, in any case. */
  def parse(text: String): Chord = {
    val plus = text.endsWith("++")
    val parts = (if (plus) text.dropRight(2) else text).split('+').toList.map(_.trim).filter(_.nonEmpty)
    val key = if (plus) "+" else parts.lastOption.getOrElse("")
    val held = (if (plus) parts else parts.dropRight(1)).map(_.toLowerCase)
    Chord(key,
      primary = held.contains("primary") || held.contains("mod"),
      ctrl = held.contains("ctrl") || held.contains("control"),
      meta = held.contains("meta") || held.contains("cmd") || held.contains("command"),
      alt = held.contains("alt") || held.contains("option"),
      shift = held.contains("shift"))
  }

  /** Whether the platform's name for a key is the key a chord names: the same without case, and "Space" for " ". */
  def sameKey(name: String, key: String): Boolean =
    name.toLowerCase == key.toLowerCase || (name == " " && key.toLowerCase == "space")
}

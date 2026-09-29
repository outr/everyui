package everyui.keymap

import everyui.event.KeyEvent
import everyui.render.Platform

/** A key and the modifiers held with it: `Chord("W", primary = true)`, `Chord("Tab", ctrl = true, shift = true)`,
  * `Chord("Escape")`, or the same written out, `Chord.parse("Primary+Shift+N")`.
  *
  * The key is the web's name for it ("Escape", "Tab", "ArrowUp", "F5", "Space") or the character it types, matched
  * without regard to case, so Shift+N is `Chord("N", shift = true)` whether the platform reports "n" or "N".
  * `primary` is the platform's own shortcut modifier: Command (Meta) where shortcuts are made with it (macOS, iOS),
  * Ctrl everywhere else. A chord matches only with exactly its modifiers held.
  *
  * A double-tap is a modifier on its own, pressed and released twice in quick succession: `Chord.doubleTap("Shift")`,
  * `Chord.parse("Ctrl Ctrl")`. Its key is the modifier ("Control", "Shift", "Alt", "Meta" or "Primary"); no single key
  * event matches it, and the keymap runs it on the second release (`Keymap.doubleTapWindow`). */
case class Chord(key: String, primary: Boolean = false, ctrl: Boolean = false, meta: Boolean = false,
                 alt: Boolean = false, shift: Boolean = false, doubleTap: Boolean = false) {
  /** Whether `e` is this chord. A double-tap is two presses, which no one event is. */
  def matches(e: KeyEvent): Boolean = {
    val command = Platform.commandKey()
    !doubleTap && e.ctrl == (ctrl || (primary && !command)) && e.meta == (meta || (primary && command)) &&
      e.alt == alt && e.shift == shift && Chord.sameKey(e.name, key)
  }

  /** The modifier a double-tap is of, by the web's name ("Control", "Shift", "Alt", "Meta"), Primary being Meta where
    * shortcuts are made with Command and Control elsewhere; "" for a chord that is not a double-tap. */
  def tapped: String =
    if (!doubleTap) ""
    else if (key == "Primary") (if (Platform.commandKey()) "Meta" else "Control")
    else key

  /** How a list of shortcuts shows it: "Ctrl+Shift+N", "Cmd+W", "Shift Shift", "Cmd Cmd". */
  def label: String = {
    val command = Platform.commandKey()
    if (doubleTap) {
      val m = tapped
      val name =
        if (m == "Control") "Ctrl"
        else if (m == "Meta") (if (command) "Cmd" else "Meta")
        else if (m == "Alt") (if (command) "Option" else "Alt")
        else m
      name + " " + name
    }
    else {
      val held = List(
        if (ctrl || (primary && !command)) "Ctrl" else "",
        if (meta || (primary && command)) (if (command) "Cmd" else "Meta") else "",
        if (alt) (if (command) "Option" else "Alt") else "",
        if (shift) "Shift" else "").filter(_.nonEmpty)
      (held :+ (if (key.length == 1) key.toUpperCase else key)).mkString("+")
    }
  }
}

object Chord {
  /** A chord written as its modifiers and key joined by `+`: "Primary+Shift+N", "Ctrl+Tab", "Escape", "Ctrl++". The
    * modifiers are Primary (or Mod), Ctrl (Control), Meta (Cmd, Command), Alt (Option) and Shift, in any case. A
    * modifier written twice with a space between is its double-tap: "Ctrl Ctrl", "Shift Shift", "Primary Primary". */
  def parse(text: String): Chord = {
    val words = text.split(' ').toList.map(_.trim).filter(_.nonEmpty)
    if (!text.contains("+") && words.size == 2 && modifier(words.head).nonEmpty &&
      modifier(words.head) == modifier(words(1))) doubleTap(words.head)
    else {
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
  }

  /** A modifier on its own pressed and released twice in quick succession, with no other key between: "Control" (or
    * Ctrl), "Shift", "Alt" (Option), "Meta" (Cmd, Command) or "Primary" (Mod: Command on a Mac, Ctrl elsewhere). */
  def doubleTap(modifier: String): Chord = {
    val m = Chord.modifier(modifier)
    Chord(if (m.nonEmpty) m else modifier, doubleTap = true)
  }

  /** The modifier a name is written for, as a double-tap's key: "Control", "Shift", "Alt", "Meta" or "Primary"; ""
    * for a name that is none of them. */
  def modifier(name: String): String = {
    val n = name.toLowerCase
    if (n == "ctrl" || n == "control") "Control"
    else if (n == "shift") "Shift"
    else if (n == "alt" || n == "option") "Alt"
    else if (n == "meta" || n == "cmd" || n == "command") "Meta"
    else if (n == "primary" || n == "mod") "Primary"
    else ""
  }

  /** Whether the platform's name for a key is the key a chord names: the same without case, and "Space" for " ". */
  def sameKey(name: String, key: String): Boolean =
    name.toLowerCase == key.toLowerCase || (name == " " && key.toLowerCase == "space")
}

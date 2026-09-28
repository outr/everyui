package everyui.keymap

import everyui.event.KeyEvent
import reactify.*

/** Commands and the chords that run them — the keyboard shortcuts of an app, a screen or a part of one.
  *
  * `Keymap.global` is always in force; each screen has its own (`Screen.keymap`), in force only while it is the one
  * showing, and asked first. A key that runs a command is taken before the focused component or the focus layer hears
  * of it, and kept from the platform (a browser's own Ctrl+W, say, where the browser lets a page have it).
  *
  * {{{
  *   Keymap.global.add("tab.close", "Close tab", Chord("W", primary = true)) { tabs.closeCurrent() }
  *   keymap.add("file.new", "New file", Chord.parse("Primary+Shift+N")) { newFile() }   // in a screen
  *   Keymap.global.rebind("tab.close", Chord("F4", ctrl = true))
  *   Keymap.listed.map(c => c.label + "  " + c.chords().map(_.label).mkString(", "))   // a shortcut help screen
  * }}} */
class Keymap {
  val commands: Var[List[Command]] = Var(Nil)

  /** A command, added: `run` runs whenever one of `chords` is pressed while this keymap is in force. An id already
    * here is replaced. */
  def add(id: String, label: String, chords: Chord*)(run: => Unit): Command =
    add(new Command(id, label, chords.toList, () => run))

  def add(command: Command): Command = {
    commands @= commands().filterNot(_.id == command.id) :+ command
    command
  }

  def remove(id: String): Unit = commands @= commands().filterNot(_.id == id)

  /** The command added as `id`. */
  def apply(id: String): Option[Command] = commands().find(_.id == id)

  /** `id` run by `chords` from now on, in place of what ran it before. */
  def rebind(id: String, chords: Chord*): Unit = apply(id).foreach(_.chords @= chords.toList)

  /** The command `e` runs here, if any. */
  def find(e: KeyEvent): Option[Command] = commands().find(_.runsOn(e))
}

object Keymap {
  /** The app's own shortcuts, in force on every screen. */
  lazy val global: Keymap = new Keymap
  /** The showing screen's keymap, as the screen manager says. */
  val shown: Var[() => Option[Keymap]] = Var(() => None)

  /** The keymaps in force, the most particular first: the showing screen's, then the global one. */
  def active: List[Keymap] = shown()().toList :+ global

  /** Every command in force, for a list of shortcuts: the showing screen's, then the global ones it does not hide. */
  def listed: List[Command] = {
    val all = active.flatMap(_.commands())
    all.filter(c => all.find(_.id == c.id).exists(_ eq c))
  }

  /** The command `e` runs, the showing screen's before the global ones. */
  def find(e: KeyEvent): Option[Command] = {
    var found: Option[Command] = None
    var left = active
    while (found.isEmpty && left.nonEmpty) {
      found = left.head.find(e)
      left = left.tail
    }
    found
  }

  /** Run the command `e` is a chord of, if there is one: whether one ran, and so took the key. */
  def run(e: KeyEvent): Boolean = find(e) match {
    case Some(command) =>
      command.run()
      true
    case None => false
  }
}

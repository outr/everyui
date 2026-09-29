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
  *   Keymap.embed("editor", Chord("W", primary = true))   // inside the editor embed, only this chord stays the app's
  *   Keymap.global.add("palette", "Command palette", Chord.doubleTap("Shift")) { palette.open() }   // Shift, Shift
  * }}}
  *
  * A double-tap of a modifier (`Chord.doubleTap`) runs on its second release, even inside an embed. */
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

  /** The chords each embed leaves to the app, by the embed's name (`embed`). */
  val embeds: Var[Map[String, List[Chord]]] = Var(Map.empty)

  /** While the keys are embed `name`'s (`Platform.keyEmbed`: an editor in the page), `chords` still run the app's
    * commands; every other key is the embed's, untouched, Escape included (`Focus.handBack` gives one back). Said
    * again, it replaces what the embed left before. */
  def embed(name: String, chords: Chord*): Unit = embeds @= embeds() + (name -> chords.toList)

  /** Whether `c` may take `e` while the keys are embed `embed`'s ("" for none): outside an embed any may; inside one,
    * a command made to win there (`Command.inEmbeds`), or one on a chord the embed leaves to the app. */
  def allowed(c: Command, e: KeyEvent, embed: String): Boolean =
    embed.isEmpty || c.inEmbeds() || embeds().getOrElse(embed, Nil).exists(_.matches(e))

  /** The command `e` runs, the showing screen's before the global ones; inside embed `embed`, only one it allows. */
  def find(e: KeyEvent, embed: String = ""): Option[Command] = {
    var found: Option[Command] = None
    var left = active
    while (found.isEmpty && left.nonEmpty) {
      found = left.head.commands().find(c => c.runsOn(e) && allowed(c, e, embed))
      left = left.tail
    }
    found
  }

  /** How long a double-tap (`Chord.doubleTap`) may take, from the first press to the second release, in milliseconds. */
  val doubleTapWindow: Var[Double] = Var(400.0)
  /** The keys so far, as a double-tap goes. */
  lazy val taps: DoubleTap = new DoubleTap

  /** The command a double-tap of `modifier` ("Control", "Shift", "Alt", "Meta") runs, the showing screen's before the
    * global ones. A double-tap runs inside embeds too: a modifier on its own types nothing, so an editor loses nothing
    * to it. */
  def findTap(modifier: String): Option[Command] = {
    var found: Option[Command] = None
    var left = active
    while (found.isEmpty && left.nonEmpty) {
      found = left.head.commands().find(_.tappedBy(modifier))
      left = left.tail
    }
    found
  }

  /** Hear `e` for the double-taps, pressed or released and whether it is a key held down repeating, and run the
    * command a double-tap it completes runs: whether one ran, and so took the key. */
  def tap(e: KeyEvent, pressed: Boolean, repeat: Boolean): Boolean = {
    val m = taps.key(e, pressed, repeat)
    m.nonEmpty && (findTap(m) match {
      case Some(command) =>
        command.run()
        true
      case None => false
    })
  }

  /** Whether `e` is Alt on its own while a double-tap of Alt runs a command, which the platform must not have: a
    * browser, or the system, puts the focus in its menu bar on a lone Alt. */
  def keepsAlt(e: KeyEvent): Boolean = e.name == "Alt" && findTap("Alt").nonEmpty

  /** Run the command `e` is a chord of, if there is one (inside embed `embed`, one it allows): whether one ran, and
    * so took the key. */
  def run(e: KeyEvent, embed: String = ""): Boolean = find(e, embed) match {
    case Some(command) =>
      command.run()
      true
    case None => false
  }
}

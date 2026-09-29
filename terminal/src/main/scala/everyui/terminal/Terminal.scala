package everyui.terminal

import everyui.component.types.Semantics
import everyui.component.{Embed, EmbedSite, TextView}
import everyui.focus.Focus
import everyui.keymap.{Chord, Keymap}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A terminal in a box the app lays out: xterm.js, which draws what a program writes (ANSI and VT escapes, colours,
  * the cursor, the alternate screen of a full-screen program) and turns the keys into what a terminal sends. It knows
  * nothing of where the bytes come from: the app writes a shell's output in and sends its input on, over whatever
  * carries them (a socket to a pty, a local process).
  *
  * {{{
  *   val term = new Terminal(TerminalOptions(fontSize = 14.0))
  *   term.size.width := panel.size.width()
  *   term.size.height := panel.size.height()
  *   term.onInput.attach(data => socket.send(data))             // keys and pastes, as the bytes a terminal sends
  *   term.onResize.attach(s => socket.resize(s.cols, s.rows))  // the pty told its window size
  *   socket.onBytes(bytes => term.writeBytes(bytes))           // the shell's output, UTF-8 split anywhere
  *   term.exit @= Some(fileTree)                               // where a remote's Back takes the focus
  * }}}
  *
  * The terminal is made the first time the box is on the screen with a size, lays its grid out to the box as it
  * changes (`dimensions`, `onResize`), follows the palette (`theme`) and `fontSize`, and keeps `scrollback` lines to
  * scroll back to with the wheel or a finger. What is written before it is made waits for it.
  *
  * **Keys.** While it has the focus every key is the shell's: Ctrl+C is `\u0003`, the arrows their escape sequences,
  * Tab and Escape too, so the focus never leaves it by a key of the terminal's own. The app's shortcuts still run
  * inside it exactly where they do inside a Monaco editor: a command made to win in embeds (`Command.inEmbeds`), a
  * chord left to the app for terminals (`Terminal.keep`, which is `Keymap.embed("terminal", ...)`), a double-tap of a
  * modifier, and a remote's Back (to `exit`). Those keys never reach the shell. Copying is Ctrl+Shift+C (Cmd+C on a
  * Mac), through the platform's clipboard; pasting is Ctrl+Shift+V (Cmd+V), the engine's own paste, sent bracketed
  * where the program asked for it; `copy()` and `paste(text)` do the same for a menu.
  *
  * It runs in the app's own page on the web, and in a web view of its own on Android and the desktop (Embed), from
  * the files of `Terminal.site`. Where no web engine runs it (a Roku, an Apple TV, a desktop without javafx-web) a
  * line saying so shows in its place, and nothing is heard. */
class Terminal(val options: TerminalOptions = TerminalOptions())
  extends Embed(Terminal.embedName, Terminal.site, TextView(Terminal.unavailable, 13.0, Theme.muted())) {
  /** What the terminal sends, as a program on the other end reads it: typed keys (Enter is `\r`), escape sequences for
    * the arrows and function keys, pastes, and the replies to a program's queries. Text: encode it as UTF-8 to send. */
  val onInput: Channel[String] = Channel[String]
  /** What the terminal sends as raw bytes rather than text: the old mouse reports a program can ask for, whose
    * positions past 95 are not characters. */
  val onBinary: Channel[Array[Byte]] = Channel[Array[Byte]]
  /** The grid, each time it changes: tell the pty (`TIOCSWINSZ`). */
  val onResize: Channel[TerminalSize] = Channel[TerminalSize]
  /** The bell (`\u0007`). */
  val onBell: Channel[Unit] = Channel[Unit]
  /** A web address in the text, clicked (with `links` on): opened in the platform's browser too while `openLinks`. */
  val onLink: Channel[String] = Channel[String]

  /** The grid the terminal has room for, once it is made. */
  val dimensions: Var[Option[TerminalSize]] = own(Var(None))
  /** The title the program running in it set (`ESC ] 0 ;`), empty until it sets one. */
  val title: Var[String] = own(Var(""))
  /** The text selected in it, if any. */
  val selection: Var[Option[String]] = own(Var(None))
  /** Whether the terminal has been made and is drawing. */
  val ready: Var[Boolean] = own(Var(false))
  /** The size of its text, in points. */
  val fontSize: Var[Double] = own(Var(options.fontSize))
  /** Its colours: the palette's, following it as it changes, until the app sets its own. */
  val theme: Var[TerminalTheme] = own(Var(TerminalTheme.of(Theme.palette())))
  /** Whether a clicked link is also opened in the platform's browser (`Platform.openUrl`). */
  var openLinks: Boolean = true

  private val waiting = new StringBuilder
  private var made = false
  private var flushing = false
  private var disposed = false
  private val utf8 = new Utf8Stream

  if (live) {
    backgroundColor := theme().background
    semantics @= Semantics("region", label = "Terminal")
    hear(text => TerminalWire.parse(text).foreach { case (name, from, payload) => if (from == id) receive(name, payload) })
    guestFocus = on => if (made) {
      if (on) say("keep", TerminalWire.chords(Terminal.kept()))
      say(if (on) "focus" else "blur")
    }
    onSize((_, _) => if (made) say("fit") else make())
    fontSize.attach(points => if (made) say("options", TerminalWire.obj("fontSize" -> TerminalWire.num(points))))
    theme.attach(t => if (made) say("options", TerminalWire.obj("theme" -> TerminalWire.theme(t))))
    follow(Keymap.embeds, (_: Map[String, List[Chord]]) => if (made) say("keep", TerminalWire.chords(Terminal.kept())))
  }

  /** `data` written to the terminal as a program's output: text, with its escape sequences. Writes close together go
    * as one. */
  def write(data: String): Unit = if (live && !disposed && data.nonEmpty) {
    waiting.append(data)
    if (made && !flushing) {
      flushing = true
      Platform.after(0.0, () => flush())
    }
  }

  /** `bytes` of UTF-8 written to the terminal, as a socket delivers them: a character split between two calls is
    * written whole with the second. */
  def writeBytes(bytes: Array[Byte]): Unit = write(utf8.decode(bytes))

  /** `text` pasted, as though from the clipboard: sent on through `onInput`, bracketed where the program asked. */
  def paste(text: String): Unit = say("paste", text)
  /** The selection onto the platform's clipboard, if there is one. */
  def copy(): Unit = say("copy")
  def selectAll(): Unit = say("selectAll")
  def clearSelection(): Unit = say("clearSelection")
  /** The scrollback emptied, keeping the line the cursor is on. */
  def clear(): Unit = say("clear")
  /** Back to how it was made: the screen and the scrollback emptied, every mode the program set undone. */
  def reset(): Unit = say("reset")
  def scrollToBottom(): Unit = say("scrollToBottom")
  /** Scrolled `lines` down (up, for fewer than none). */
  def scrollLines(lines: Int): Unit = say("scrollLines", lines.toString)

  // made once the box is on the screen: on the web the terminal measures its cells in the page
  private def make(): Unit = if (!made && !disposed) {
    if (!Focus.showing(this)) Platform.after(0.05, () => make())
    else {
      made = true
      import TerminalWire.{bool, chords, message, num, obj, str}
      send(message("create", id, obj(
        "fontFamily" -> str(options.fontFamily), "fontSize" -> num(fontSize()), "lineHeight" -> num(options.lineHeight),
        "scrollback" -> num(options.scrollback.toDouble), "cursorStyle" -> str(options.cursor.name),
        "cursorBlink" -> bool(options.cursorBlink), "optionIsMeta" -> bool(options.optionIsMeta),
        "screenReader" -> bool(options.screenReader), "links" -> bool(options.links),
        "theme" -> TerminalWire.theme(this.theme()), "keep" -> chords(Terminal.kept()))))
      flush()
      if (focused()) say("focus")
    }
  }

  // what was written since the last flush, as one message; anything else said goes after it
  private def flush(): Unit = {
    flushing = false
    if (waiting.nonEmpty && !disposed) {
      val data = waiting.toString
      waiting.clear()
      send(TerminalWire.message("write", id, data))
    }
  }

  private def say(name: String, payload: String = ""): Unit = if (made && !disposed) {
    flush()
    send(TerminalWire.message(name, id, payload))
  }

  private def receive(name: String, payload: String): Unit =
    if (name == "input") onInput @= payload
    else if (name == "binary") onBinary @= payload.toCharArray.map(_.toByte)
    else if (name == "resize" || name == "ready") TerminalWire.size(payload).foreach { s =>
      if (name == "ready") ready @= true
      if (!dimensions().contains(s)) {
        dimensions @= Some(s)
        onResize @= s
      }
    }
    else if (name == "title") title @= payload
    else if (name == "bell") onBell @= (())
    else if (name == "selection") selection @= (if (payload.isEmpty) None else Some(payload))
    else if (name == "copy") Platform.copyText(payload)
    else if (name == "link") {
      onLink @= payload
      if (openLinks) Platform.openUrl(payload)
    }
    else if (name == "error") Platform.log("[terminal] " + payload)

  override def dispose(): Unit = {
    if (!disposed) {
      if (made) say("dispose")
      disposed = true
    }
    super.dispose()
  }

  override def kind: String = "Terminal"
}

object Terminal {
  /** The version of xterm.js bundled (project/TerminalAssets.scala). */
  val version: String = "6.0.0"

  /** The embed's name: what `Keymap.embed` and `Platform.keyEmbed` call a terminal. */
  val embedName: String = "terminal"

  /** Where the terminal's files are: served next to the app's page on the web (`everyui-terminal/<version>/`, relative
    * to it, or a whole address), read from the classpath on the desktop, and from the APK's assets on Android. */
  var site: EmbedSite = EmbedSite(embedName, "everyui-terminal/" + version, "host.html",
    List("xterm.js", "addon-fit.js", "addon-web-links.js", "bridge/terminal_bridge.js"))

  /** What shows where no web engine can run a terminal. */
  var unavailable: String = "A terminal needs a web engine, which this device does not have."

  /** `chords` kept for the app's commands while the keys are a terminal's, beside those kept already: they run the
    * app's command and never reach the shell. */
  def keep(chords: Chord*): Unit = {
    val kept = Keymap.embeds().getOrElse(embedName, Nil)
    Keymap.embed(embedName, (kept ++ chords.filterNot(kept.contains))*)
  }

  /** The chords the app takes while the keys are a terminal's, as this platform presses them: those left to it
    * (`Keymap.embed`) and those of every command in force made to win in embeds. */
  private[terminal] def kept(): List[(String, Boolean, Boolean, Boolean, Boolean)] = {
    val command = Platform.commandKey()
    val chords = Keymap.embeds().getOrElse(embedName, Nil) ++
      Keymap.active.flatMap(_.commands()).filter(c => c.enabled() && c.inEmbeds()).flatMap(_.chords())
    chords.filterNot(_.doubleTap)
      .map(c => (c.key, c.ctrl || (c.primary && !command), c.meta || (c.primary && command), c.alt, c.shift)).distinct
  }
}

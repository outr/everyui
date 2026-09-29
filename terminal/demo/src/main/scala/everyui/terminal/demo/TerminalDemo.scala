package everyui.terminal.demo

import everyui.component.{Button, Container, TextView}
import everyui.layout.FlowLayout
import everyui.keymap.{Chord, Keymap}
import everyui.render.Platform
import everyui.terminal.{Terminal, TerminalOptions}
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** everyui-terminal's demo (`demo=terminal`): a terminal over a pretend shell (DemoShell), with its text size, the
  * light and dark palettes, a colour table and a flood of lines for the scrollback a click away, and what it last sent
  * underneath. Primary+= and Primary+- size the text from inside the terminal too: chords left to the app
  * (`Terminal.keep`), which never reach the shell. `theme=light` starts light and `font=` sizes the text.
  *
  * Logs `terminal input <what was sent, escaped>`, `terminal resize <cols>x<rows>` and `terminal title <title>`, which
  * the browser checks read. */
object TerminalDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    Platform.setTitle("everyui-terminal")
    ui.backgroundColor := Theme.background()
    val term = new Terminal(TerminalOptions(fontSize = Platform.launchArg("font").toDoubleOption.getOrElse(13.0)))
    val shell = new DemoShell(term)
    val sent = Var("")

    term.onInput.attach { data =>
      Platform.log("terminal input " + escape(data))
      sent @= escape(data)
      shell.input(data)
    }
    term.onResize.attach(s => Platform.log("terminal resize " + s.cols.toString + "x" + s.rows.toString))
    term.title.attach(t => Platform.log("terminal title " + t))

    // the app's own shortcuts, kept while the keys are the terminal's
    Keymap.global.add("terminal.bigger", "Bigger text", Chord("=", primary = true))(resize(term, 1.0))
    Keymap.global.add("terminal.smaller", "Smaller text", Chord("-", primary = true))(resize(term, -1.0))
    Terminal.keep(Chord("=", primary = true), Chord("-", primary = true))

    def button(label: String)(action: => Unit): Button = new Button(label, () => {
      action
      term.focus()
    })
    val smaller = button("A-")(resize(term, -1.0))
    // the heading, centred on the buttons' line
    val heading = TextView("Terminal", 17.0)
    val title = new Container
    title.size.width := heading.size.width() + 8.0
    title.size.height := smaller.size.height()
    heading.position.top := (title.size.height() - heading.size.height()) / 2.0
    title.children += heading
    val bar = new Container
    bar.layout @= Some(FlowLayout(8.0, 8.0))
    bar.children ++= List(
      title,
      smaller,
      button("A+")(resize(term, 1.0)),
      button("Light / dark")(Theme.palette @= (if (Theme.palette() eq Palette.dark) Palette.light else Palette.dark)),
      button("Colours")(term.write(DemoShell.colours)),
      button("Flood")(shell.input("seq 2000\r")),
      button("Copy")(term.copy()),
      button("Clear")(term.clear()))
    val last = bar.children().last
    val status = TextView.bound(
      term.dimensions().map(s => s.cols.toString + " x " + s.rows.toString).getOrElse("-") + "  \u00b7  " +
        term.fontSize().toInt.toString + " pt  \u00b7  sent " + sent() +
        (if (term.title().nonEmpty) "  \u00b7  title " + term.title() else ""), 12.0, Theme.muted())
    status.font.family @= "Roboto Mono"

    val margin = 16.0
    bar.position.left @= margin
    bar.position.top @= 12.0
    bar.size.width := ui.size.width() - margin * 2.0
    bar.size.height := last.position.top() + last.size.height()
    term.position.left @= margin
    term.position.top := bar.position.top() + bar.size.height() + 12.0
    term.size.width := ui.size.width() - margin * 2.0
    term.size.height := ui.size.height() - term.position.top() - 40.0
    status.position.left @= margin
    status.position.top := term.position.top() + term.size.height() + 12.0
    ui.children ++= List(bar, term, status)
    shell.start()
    Platform.after(0.3, () => term.focus())
  }

  private def resize(term: Terminal, by: Double): Unit =
    term.fontSize @= math.max(8.0, math.min(32.0, term.fontSize() + by))

  /** What was sent, readable: control characters as `\u0003`, the rest as it is. */
  def escape(data: String): String = {
    val out = new StringBuilder
    var i = 0
    while (i < data.length) {
      val c = data.charAt(i)
      if (c < ' ' || c == '\u007f') {
        val hex = Integer.toHexString(c.toInt)
        out.append("\\u").append("0000".substring(hex.length)).append(hex)
      }
      else out.append(c)
      i += 1
    }
    out.toString
  }
}

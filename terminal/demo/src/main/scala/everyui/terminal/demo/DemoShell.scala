package everyui.terminal.demo

import everyui.terminal.Terminal

/** A pretend shell for the demo, answering in the terminal itself: it echoes what is typed, edits the line with
  * Backspace, answers Ctrl+C with `^C` and a fresh prompt, and runs a few commands that show what the terminal draws
  * (`help` lists them). Nothing runs anywhere: a real app sends `onInput` to a pty instead. */
class DemoShell(term: Terminal) {
  private val prompt: String = "\u001b[1;32meveryui\u001b[0m:\u001b[1;34m~\u001b[0m$ "
  private val line = new StringBuilder

  def start(): Unit = {
    term.write("\u001b[1meveryui-terminal\u001b[0m xterm.js " + Terminal.version + " in an EveryUI embed. " +
      "Type \u001b[33mhelp\u001b[0m.\r\n\r\n" + prompt)
  }

  /** What the terminal sent, as a shell's line discipline would take it. */
  def input(data: String): Unit =
    if (data.startsWith("\u001b")) ()   // a key's escape sequence (an arrow, F1): the demo's shell has no use for it
    else {
      var i = 0
      while (i < data.length) {
        val c = data.charAt(i)
        if (c == '\r') {
          term.write("\r\n")
          run(line.toString.trim)
          line.clear()
          term.write(prompt)
        }
        else if (c == '\u007f' || c == '\b') {
          if (line.nonEmpty) {
            line.deleteCharAt(line.length - 1)
            term.write("\b \b")
          }
        }
        else if (c == '\u0003') {
          line.clear()
          term.write("^C\r\n" + prompt)
        }
        else if (c == '\u000c') {
          term.write("\u001b[H\u001b[2J" + prompt + line.toString)
        }
        else if (c >= ' ') {
          line.append(c)
          term.write(c.toString)
        }
        i += 1
      }
    }

  private def run(command: String): Unit = {
    val words = command.split(' ').toList.filter(_.nonEmpty)
    words.headOption.getOrElse("") match {
      case "" => ()
      case "help" => term.write(
        "  colors      the 16 ANSI colours, the 256-colour cube and a true-colour ramp\r\n" +
        "  ls          a directory listing, coloured\r\n" +
        "  seq N       N numbered lines (the scrollback)\r\n" +
        "  title TEXT  the terminal's title\r\n" +
        "  echo TEXT   TEXT\r\n" +
        "  bell        the bell\r\n" +
        "  clear       the screen cleared (Ctrl+L too)\r\n" +
        "  https://everyui.com is a link\r\n")
      case "colors" => term.write(DemoShell.colours)
      case "ls" => term.write(
        "\u001b[1;34mapp\u001b[0m  \u001b[1;34mshared\u001b[0m  \u001b[1;34mterminal\u001b[0m  build.sbt  " +
        "\u001b[1;32mcheck.sh\u001b[0m  README.md  \u001b[1;36mlatest -> terminal\u001b[0m  \u001b[31mold.tar.gz\u001b[0m\r\n")
      case "seq" =>
        val n = words.lift(1).flatMap(_.toIntOption).getOrElse(100)
        val out = new StringBuilder
        var i = 1
        while (i <= n) {
          out.append("line ").append(i).append("\r\n")
          i += 1
        }
        term.write(out.toString)
      case "title" => term.write("\u001b]0;" + words.drop(1).mkString(" ") + "\u0007")
      case "echo" => term.write(words.drop(1).mkString(" ") + "\r\n")
      case "bell" => term.write("\u0007")
      case "clear" => term.write("\u001b[H\u001b[2J")
      case other => term.write("demo: command not found: " + other + "\r\n")
    }
  }
}

object DemoShell {
  /** The 16 colours, the 6x6x6 cube with the greys, and a true-colour ramp. */
  lazy val colours: String = {
    val out = new StringBuilder
    var i = 0
    while (i < 16) {
      out.append("\u001b[48;5;").append(i).append("m  ").append("\u001b[0m")
      if (i == 7) out.append("\r\n")
      i += 1
    }
    out.append("\r\n\r\n")
    var row = 0
    while (row < 12) {
      var col = 0
      while (col < 18) {
        val cube = 16 + (if (row < 6) row * 36 + col else (row - 6) * 36 + 18 + col)
        out.append("\u001b[48;5;").append(cube).append("m  ")
        col += 1
      }
      out.append("\u001b[0m\r\n")
      row += 1
    }
    var grey = 232
    while (grey < 256) {
      out.append("\u001b[48;5;").append(grey).append("m  ")
      grey += 1
    }
    out.append("\u001b[0m\r\n\r\n")
    var x = 0
    while (x < 72) {
      val r = 255 - x * 255 / 71
      val g = if (x < 36) x * 255 / 35 else (71 - x) * 255 / 35
      val b = x * 255 / 71
      out.append("\u001b[48;2;").append(r).append(';').append(g).append(';').append(b).append("m ")
      x += 1
    }
    out.append("\u001b[0m\r\n\u001b[1mbold\u001b[0m \u001b[3mitalic\u001b[0m \u001b[4munderline\u001b[0m " +
      "\u001b[7minverse\u001b[0m \u001b[9mstrike\u001b[0m \u001b[2mdim\u001b[0m\r\n")
    out.toString
  }
}

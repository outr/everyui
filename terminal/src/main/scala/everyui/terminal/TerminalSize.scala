package everyui.terminal

/** A terminal's grid: how many columns across and rows down it has room for, which is what a shell is told
  * (a pty's window size, `TIOCSWINSZ`). */
case class TerminalSize(cols: Int, rows: Int)

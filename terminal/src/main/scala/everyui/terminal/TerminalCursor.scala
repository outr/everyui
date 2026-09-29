package everyui.terminal

/** How the cursor is drawn: a block over the cell, a bar before it, or a line under it. */
enum TerminalCursor(val name: String) {
  case Block extends TerminalCursor("block")
  case Bar extends TerminalCursor("bar")
  case Underline extends TerminalCursor("underline")
}

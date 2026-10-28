package everyui.terminal

/** How a terminal is made. `fontSize` and the theme can change afterwards (`Terminal.fontSize`, `Terminal.theme`); the
  * rest is fixed for its life.
  *
  *   - `fontFamily` is a CSS font list: Roboto Mono is the app's own on the web (EveryUI's fonts); in a web view of its
  *     own (Android, the desktop) the platform's monospace face stands in.
  *   - `scrollback` is how many lines that have gone off the top are kept to scroll back to.
  *   - `optionIsMeta` makes Option (Alt) send Meta, as a shell's line editing wants, rather than the character the
  *     keyboard layout gives it.
  *   - `screenReader` keeps a live text of what is written for a screen reader to read, at some cost.
  *   - `links` lets a web address in the text be clicked (`Terminal.onLink`). */
case class TerminalOptions(fontSize: Double = 13.0,
                           fontFamily: String = TerminalOptions.monospace,
                           lineHeight: Double = 1.0,
                           scrollback: Int = 5000,
                           cursor: TerminalCursor = TerminalCursor.Block,
                           cursorBlink: Boolean = true,
                           optionIsMeta: Boolean = false,
                           screenReader: Boolean = false,
                           links: Boolean = true)

object TerminalOptions {
  /** EveryUI's monospace face, then each platform's own. */
  val monospace: String = "'Roboto Mono', 'SF Mono', Menlo, Consolas, 'DejaVu Sans Mono', 'Liberation Mono', " +
    "'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji', monospace"
}

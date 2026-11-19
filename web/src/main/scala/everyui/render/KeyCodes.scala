package everyui.render

/** A key's name as the web names it (`KeyboardEvent.key`), from its key code, for an engine that gives no `key`
  * (before Chromium 51: webOS 3's 38): the keys a remote and a keyboard send, a character by the US layout, Shift
  * taken. A TV's own keys (its Back, its coloured and transport keys) are its host's (`AppHost.keyName`), asked first.
  * Plain Scala, so the desktop's specs read it. */
object KeyCodes {
  private val named: Map[Int, String] = Map(
    8 -> "Backspace", 9 -> "Tab", 12 -> "Clear", 13 -> "Enter", 16 -> "Shift", 17 -> "Control", 18 -> "Alt",
    19 -> "Pause", 20 -> "CapsLock", 27 -> "Escape", 32 -> " ", 33 -> "PageUp", 34 -> "PageDown", 35 -> "End",
    36 -> "Home", 37 -> "ArrowLeft", 38 -> "ArrowUp", 39 -> "ArrowRight", 40 -> "ArrowDown", 45 -> "Insert",
    46 -> "Delete", 91 -> "Meta", 92 -> "Meta", 93 -> "ContextMenu", 144 -> "NumLock", 145 -> "ScrollLock",
    173 -> "AudioVolumeMute", 174 -> "AudioVolumeDown", 175 -> "AudioVolumeUp", 176 -> "MediaTrackNext",
    177 -> "MediaTrackPrevious", 178 -> "MediaStop", 179 -> "MediaPlayPause",
    106 -> "*", 107 -> "+", 109 -> "-", 110 -> ".", 111 -> "/")

  // the characters of the keys of punctuation, then with Shift
  private val marks: Map[Int, (String, String)] = Map(
    186 -> (";", ":"), 187 -> ("=", "+"), 188 -> (",", "<"), 189 -> ("-", "_"), 190 -> (".", ">"), 191 -> ("/", "?"),
    192 -> ("`", "~"), 219 -> ("[", "{"), 220 -> ("\\", "|"), 221 -> ("]", "}"), 222 -> ("'", "\""))
  private val shiftedDigits = ")!@#$%^&*("

  /** The name of the key `code`, Shift held or not; "Unidentified" for one not known. */
  def name(code: Int, shift: Boolean): String =
    if (code >= 65 && code <= 90) { val c = ('a' + (code - 65)).toChar; (if (shift) c.toUpper else c).toString }
    else if (code >= 48 && code <= 57)
      (if (shift) shiftedDigits.charAt(code - 48) else ('0' + (code - 48)).toChar).toString
    else if (code >= 96 && code <= 105) ('0' + (code - 96)).toChar.toString
    else if (code >= 112 && code <= 135) "F" + (code - 111).toString
    else marks.get(code).map { case (plain, shifted) => if (shift) shifted else plain }
      .orElse(named.get(code)).getOrElse("Unidentified")
}

package everyui.text

/** Which way a piece of text reads, by its letters: what a platform with no `dir="auto"` of its own (JavaFX) turns its
  * words and aligns its lines by, and what a component can ask of the words it shows. Digits, spaces, punctuation and
  * symbols have no direction of their own; the first letter that has one decides, as the Unicode bidirectional
  * algorithm's first paragraph rule has it.
  *
  * {{{
  *   Bidi.firstStrong("2 مرحبا")         // Some(true): right to left
  *   Bidi.firstStrong("#42: fix it")    // Some(false)
  *   Bidi.firstStrong("12:30")          // None
  * }}} */
object Bidi {
  /** Whether the first letter of `text` with a direction reads right to left (Hebrew, Arabic, Syriac, Thaana, N'Ko
    * and their kin), or left to right (Latin, Greek, Cyrillic, the Indic scripts, CJK and the rest); none where no
    * letter has one. */
  def firstStrong(text: String): Option[Boolean] = {
    var found: Option[Boolean] = None
    var i = 0
    while (found.isEmpty && i < text.length) {
      found = strong(text.charAt(i).toInt)
      i += 1
    }
    found
  }

  /** Whether any letter of `text` reads right to left: whether it needs the bidirectional algorithm at all. */
  def anyRightToLeft(text: String): Boolean = {
    var found = false
    var i = 0
    while (!found && i < text.length) {
      found = strong(text.charAt(i).toInt).getOrElse(false)
      i += 1
    }
    found
  }

  /** Whether words shown with `direction` (a `TextDirection`'s value: "", "auto", "ltr" or "rtl") run right to left,
    * where the app's own direction is `rtl`: "auto" by the first letter with a direction, or the app's where there is
    * none. */
  def rightToLeft(direction: String, text: String, rtl: Boolean): Boolean =
    if (direction == "rtl") true
    else if (direction == "ltr") false
    else if (direction == "auto") firstStrong(text).getOrElse(rtl)
    else rtl

  // the direction of one character (a UTF-16 half of one beyond the first 65536 where strings are UTF-16, else the
  // whole code point), if it has one
  private def strong(c: Int): Option[Boolean] =
    if ((c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A)) Some(false)
    else if (c < 0xAA) None
    else if (c < 0x590) {
      if (c == 0xAA || c == 0xB5 || c == 0xBA || (c >= 0xC0 && c <= 0x2B8 && c != 0xD7 && c != 0xF7) ||
        (c >= 0x2BB && c <= 0x2C1) || c == 0x2D0 || c == 0x2D1 || (c >= 0x2E0 && c <= 0x2E4) || c == 0x2EE ||
        (c >= 0x370 && c != 0x374 && c != 0x375 && c != 0x37E && c != 0x384 && c != 0x385 && c != 0x387)) Some(false)
      else None
    }
    else if (c < 0x900) {
      // Hebrew's points, Arabic's marks and digits, have none
      if ((c >= 0x591 && c <= 0x5C7) || (c >= 0x600 && c <= 0x61A && c != 0x608 && c != 0x60B && c != 0x60D) ||
        (c >= 0x64B && c <= 0x66C) || c == 0x670 || (c >= 0x6D6 && c <= 0x6ED && c != 0x6E5 && c != 0x6E6) ||
        (c >= 0x6F0 && c <= 0x6F9) || (c >= 0x816 && c <= 0x82D && c != 0x81A && c != 0x824 && c != 0x828) ||
        (c >= 0x8D3 && c <= 0x8FF)) None
      else Some(true)
    }
    else if (c < 0x2000) Some(false)
    else if (c == 0x200E) Some(false)
    else if (c == 0x200F) Some(true)
    else if (c < 0x2C00) None
    else if (c < 0xD800) {
      if ((c >= 0x2E00 && c <= 0x2E7F) || (c >= 0x3000 && c <= 0x303F)) None else Some(false)
    }
    // the first half of a character in the Phoenician to Old Turkic block, or the Mende to Arabic Mathematical one
    else if (c == 0xD802 || c == 0xD803 || c == 0xD83A || c == 0xD83B) Some(true)
    else if (c < 0xF900) None
    else if (c < 0xFB1D) Some(false)
    else if (c < 0xFE00) {
      if ((c >= 0xFD3E && c <= 0xFD4F) || c == 0xFDFD) None else Some(true)
    }
    else if (c >= 0xFE70 && c <= 0xFEFE) Some(true)
    else if ((c >= 0xFF21 && c <= 0xFF3A) || (c >= 0xFF41 && c <= 0xFF5A) || (c >= 0xFF66 && c <= 0xFFDC)) Some(false)
    else if (c < 0x10000) None
    // a whole code point beyond the first 65536, where strings hold them whole (a Roku)
    else if ((c >= 0x10800 && c <= 0x10FFF) || (c >= 0x1E800 && c <= 0x1EFFF)) Some(true)
    else if (c >= 0x1F000 && c <= 0x1FFFF) None
    else Some(false)
}

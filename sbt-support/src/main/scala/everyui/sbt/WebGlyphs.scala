package everyui.sbt

/** Which of a face's glyphs a web bundle ships (WebFace.glyphs). */
enum WebGlyphs {
  /** Every one, in slices by script (Latin, Latin Extended, Cyrillic, Greek, Vietnamese, the rest), each a file of
    * its own named by the characters it holds (`unicode-range`): a browser fetches only the slices the words on the
    * page need. For a face drawing words. */
  case Scripts
  /** Only the icons the app's scripts name: the characters in Unicode's private use areas found in main.js or
    * main.wasm (a `MaterialIcon` the app reaches is its codepoint there), in one file. For an icon face, drawn by
    * codepoint; its ligatures, kerning and the rest go. */
  case Reached
  /** Every one, in one file. */
  case All
}

package everyui.picker

import everyui.font.MaterialIcon
import everyui.keymap.Chord
import everyui.render.Platform
import reactify.*

/** An option of a picker's, on or off (`on`), that the app's query reads as it finds the matches: Match case, a
  * regular expression, whole words. It is a small toggle button beside the field, showing `label` (a few letters, "Aa",
  * or `icon` where it has one), with `tooltip` (`label` where it is empty) as its name and its tooltip, and `chord`
  * after it there. It shows only while one of `segments` is the chosen segment (any, where `segments` is empty);
  * turning it over, by a click, a tap, OK or `chord`, asks the query again.
  *
  * {{{
  *   val matchCase = Var(false)
  *   PickerToggle.matchCase(matchCase, Set("files"))
  *   PickerToggle("hidden", "H", showHidden, "Hidden files", Some(PickerToggle.mnemonic("H")))
  * }}} */
case class PickerToggle(key: String, label: String, on: Var[Boolean], tooltip: String = "",
                        chord: Option[Chord] = None, segments: Set[String] = Set.empty,
                        icon: MaterialIcon = MaterialIcon.Empty) {
  /** Its name: what a screen reader says it is. */
  def name: String = if (tooltip.isEmpty) label else tooltip

  /** Whether it shows while the segment keyed `segment` is chosen. */
  def shows(segment: String): Boolean = segments.isEmpty || segments.contains(segment)
}

object PickerToggle {
  /** Whether case matters: "Aa", Alt+C (Cmd+Option+C on a Mac). */
  def matchCase(on: Var[Boolean], segments: Set[String] = Set.empty): PickerToggle =
    PickerToggle("matchCase", "Aa", on, PickerWords.matchCase, Some(mnemonic("C")), segments)

  /** Whether the words are a regular expression: ".*", Alt+R (Cmd+Option+R on a Mac). */
  def regex(on: Var[Boolean], segments: Set[String] = Set.empty): PickerToggle =
    PickerToggle("regex", ".*", on, PickerWords.regex, Some(mnemonic("R")), segments)

  /** Whether only whole words match: "W", Alt+W (Cmd+Option+W on a Mac). */
  def wholeWord(on: Var[Boolean], segments: Set[String] = Set.empty): PickerToggle =
    PickerToggle("wholeWord", "W", on, PickerWords.wholeWord, Some(mnemonic("W")), segments)

  /** The chord an option's letter makes, as an IDE's find box has it: Alt and the letter, or Command, Option and the
    * letter where Option and a letter types a character (a Mac). */
  def mnemonic(letter: String): Chord =
    if (Platform.commandKey()) Chord(letter, meta = true, alt = true) else Chord(letter, alt = true)
}

package everyui.picker

import everyui.keymap.Chord
import reactify.*

/** A second, smaller field of a picker's, under its own, whose words the app's query reads as it finds the matches: a
  * file mask ("*.scala, !*Spec.scala"), say. It says `placeholder` while empty, which is its name too, and shows only
  * while one of `segments` is the chosen segment (any, where `segments` is empty). A change to its words asks the
  * query again once the typing pauses, as the picker's own field does; `chord`, where it has one, puts the focus in
  * it.
  *
  * {{{
  *   val mask = Var("")
  *   PickerField.fileMask(mask, Set("files"))
  * }}} */
case class PickerField(key: String, placeholder: String, text: Var[String], segments: Set[String] = Set.empty,
                       chord: Option[Chord] = None) {
  /** Whether it shows while the segment keyed `segment` is chosen. */
  def shows(segment: String): Boolean = segments.isEmpty || segments.contains(segment)
}

object PickerField {
  /** Which files to look in, by their names ("*.scala, !*Spec.scala"): Alt+F (Cmd+Option+F on a Mac) goes to it. */
  def fileMask(text: Var[String], segments: Set[String] = Set.empty): PickerField =
    PickerField("fileMask", PickerWords.fileMask, text, segments, Some(PickerToggle.mnemonic("F")))
}

package everyui.picker

import everyui.i18n.Words

/** What a picker's own options say, in the language in use (Words' eleven languages): an app gives its own wording
  * with `Words.add` under these keys ("picker.matchCase", …). */
object PickerWords {
  def matchCase: String = Words("picker.matchCase")
  def regex: String = Words("picker.regex")
  def wholeWord: String = Words("picker.wholeWord")
  def fileMask: String = Words("picker.fileMask")
}

package everyui.i18n

/** A language an app ships: its code as the app's translations name it (`en`, `pt`, `zh_Hant`), its name written in
  * itself (what a language picker shows, since its reader may read nothing else), its name in English, and whether it
  * is written right to left. */
case class Language(code: String, native: String, english: String, rtl: Boolean = false)

package everyui.i18n

/** A language an app ships: its code as the app's translations name it (`en`, `pt`, `zh_Hant`), its name written in
  * itself (what a language picker shows, since its reader may read nothing else), its name in English, and whether it
  * is written right to left. */
case class Language(code: String, native: String, english: String, rtl: Boolean = false)

object Language {
  /** Of `supported`, the one that best fits a BCP 47 `tag` (a device's, `zh-Hant-TW`): its language with its script
    * (`zh_Hant`), then with its region (`pt_BR`), then its language alone — with Taiwan, Hong Kong and Macau read as
    * Traditional Chinese — else `fallback`. */
  def best(tag: String, supported: List[Language], fallback: Language): Language = {
    val parts = tag.replace('_', '-').split("-").toList.filter(_.nonEmpty)
    val language = parts.headOption.getOrElse("").toLowerCase
    val rest = parts.drop(1)
    val script = rest.find(p => p.length == 4).orElse(
      if (language == "zh" && rest.exists(r => r == "TW" || r == "HK" || r == "MO")) Some("Hant") else None)
    val region = rest.find(p => p.length == 2).map(_.toUpperCase)
    script.flatMap(s => named(supported, language + "_" + s))
      .orElse(region.flatMap(r => named(supported, language + "_" + r)))
      .orElse(named(supported, language))
      .getOrElse(fallback)
  }

  private def named(supported: List[Language], code: String): Option[Language] = supported.find(_.code == code)
}

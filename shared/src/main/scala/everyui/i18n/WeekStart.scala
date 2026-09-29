package everyui.i18n

import everyui.render.Platform
import reactify.*

/** The day a week starts on, 1 for Monday to 7 for Sunday (as `DateText` numbers weekdays), by where the reader is:
  * CLDR's first day for their region. Sunday in the United States, Canada, Brazil, Japan and the rest of CLDR's list;
  * Saturday in much of the Middle East; Monday everywhere else.
  *
  * The region is the device's (`Platform.language`, `en-GB`) where it speaks the app's language, else the one the app's
  * language is most spoken in (`en` as the United States, `ar` as Egypt, `pt` as Brazil). `chosen` overrides it all: a
  * setting in the app, say.
  *
  * {{{
  *   WeekStart.of("en_GB")        // 1: Monday
  *   WeekStart.of("en")           // 7: Sunday, as in the United States
  *   WeekStart.chosen @= Some(1)  // every calendar starts its weeks on Monday
  * }}} */
object WeekStart {
  /** The app's own choice, over the reader's region. */
  val chosen: Var[Option[Int]] = Var(None)

  /** The first day for the language in use (`Strings.language`) where the device is. */
  def current(): Int = chosen().getOrElse {
    val code = Strings.language().code
    val device = Platform.language().replace('-', '_')
    val same = device.takeWhile(_ != '_').toLowerCase == code.takeWhile(_ != '_').toLowerCase
    of(if (same && region(device).nonEmpty && region(code).isEmpty) device else code)
  }

  /** The first day for a language code or tag (`en`, `en_GB`, `pt-BR`, `zh_Hant_TW`): its region's, else its
    * language's most likely region's. */
  def of(code: String): Int = {
    val normal = code.replace('-', '_')
    val language = normal.takeWhile(_ != '_').toLowerCase
    val place = region(normal).getOrElse(likely(language, normal.contains("Hant")))
    if (sunday.contains(place)) 7 else if (saturday.contains(place)) 6 else 1
  }

  // the two-letter region of a code, where it has one (`en_GB`, `zh_Hant_TW`)
  private def region(code: String): Option[String] =
    code.split("_").toList.drop(1).find(p => p.length == 2).map(_.toUpperCase)

  // where a language with no region is most spoken (CLDR's likely subtags), for the languages whose first day differs
  private def likely(language: String, traditional: Boolean): String =
    if (language == "en") "US"
    else if (language == "pt") "BR"
    else if (language == "ar") "EG"
    else if (language == "zh") (if (traditional) "TW" else "CN")
    else if (language == "ja") "JP"
    else if (language == "ko") "KR"
    else if (language == "he" || language == "iw") "IL"
    else if (language == "hi" || language == "bn" || language == "ta" || language == "te" || language == "mr") "IN"
    else if (language == "th") "TH"
    else if (language == "fa") "IR"
    else if (language == "am") "ET"
    else if (language == "id") "ID"
    else if (language == "fil" || language == "tl") "PH"
    else if (language == "ur") "PK"
    else ""

  // CLDR's regions whose week starts on Sunday, and on Saturday
  private lazy val sunday: Set[String] = Set("AG", "AS", "BD", "BR", "BS", "BT", "BW", "BZ", "CA", "CN", "CO", "DM",
    "DO", "ET", "GT", "GU", "HK", "HN", "ID", "IL", "IN", "JM", "JP", "KE", "KH", "KR", "LA", "MH", "MM", "MO", "MT",
    "MX", "MZ", "NI", "NP", "PA", "PE", "PH", "PK", "PR", "PT", "PY", "SA", "SG", "SV", "TH", "TT", "TW", "UM", "US",
    "VE", "VI", "WS", "YE", "ZA", "ZW")
  private lazy val saturday: Set[String] = Set("AE", "AF", "BH", "DJ", "DZ", "EG", "IQ", "IR", "JO", "KW", "LY", "OM",
    "QA", "SD", "SY")
}

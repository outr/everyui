package everyui.component

/** One track of what is playing: a language to hear it in, or a language to read it in. `id` is the platform's own
  * name for it (an index, a handle) and is only meaningful to the platform that gave it out; `kind` is "audio" or
  * "text"; `language` is the tag the file carries where it carries one ("en", "fr"), and `label` is what a person
  * should be shown. */
case class MediaTrack(id: String, kind: String, language: String = "", label: String = "") {
  /** What to call it in a list: what the file said, else the language, else its place. */
  def name: String =
    if (label.nonEmpty) label
    else if (language.nonEmpty) MediaTrack.languageName(language)
    else kind.capitalize + " " + id
}

object MediaTrack {
  val Audio: String = "audio"
  val Text: String = "text"

  private val named: Map[String, String] = Map(
    "en" -> "English", "eng" -> "English", "fr" -> "French", "fre" -> "French", "fra" -> "French",
    "es" -> "Spanish", "spa" -> "Spanish", "de" -> "German", "ger" -> "German", "deu" -> "German",
    "it" -> "Italian", "ita" -> "Italian", "pt" -> "Portuguese", "por" -> "Portuguese",
    "nl" -> "Dutch", "dut" -> "Dutch", "nld" -> "Dutch", "ja" -> "Japanese", "jpn" -> "Japanese",
    "ko" -> "Korean", "kor" -> "Korean", "zh" -> "Chinese", "chi" -> "Chinese", "zho" -> "Chinese",
    "ru" -> "Russian", "rus" -> "Russian", "pl" -> "Polish", "pol" -> "Polish",
    "sv" -> "Swedish", "swe" -> "Swedish", "da" -> "Danish", "dan" -> "Danish",
    "no" -> "Norwegian", "nor" -> "Norwegian", "fi" -> "Finnish", "fin" -> "Finnish",
    "cs" -> "Czech", "ces" -> "Czech", "cze" -> "Czech", "hu" -> "Hungarian", "hun" -> "Hungarian",
    "tr" -> "Turkish", "tur" -> "Turkish", "ar" -> "Arabic", "ara" -> "Arabic",
    "he" -> "Hebrew", "heb" -> "Hebrew", "hi" -> "Hindi", "hin" -> "Hindi",
    "th" -> "Thai", "tha" -> "Thai", "el" -> "Greek", "ell" -> "Greek", "gre" -> "Greek")

  /** The two-letter form of a language tag, where this knows one: a file may carry "eng" where the rest of the app
    * speaks in "en", and a preference saved from a track should be in the form everything else reads. */
  def languageTag(tag: String): String = {
    val key = tag.trim.toLowerCase.split("[-_]").headOption.getOrElse("")
    if (key.length == 2) key
    else named.get(key).flatMap(name => short.get(name)).getOrElse(key)
  }

  /** Each language this knows by name, back to its two-letter tag. */
  private val short: Map[String, String] = named.filter(_._1.length == 2).map(e => e._2 -> e._1)

  /** The language in words, where it is one this knows; else the tag it was given. */
  def languageName(tag: String): String = {
    val key = tag.trim.toLowerCase.split("[-_]").headOption.getOrElse("")
    named.getOrElse(key, tag)
  }
}

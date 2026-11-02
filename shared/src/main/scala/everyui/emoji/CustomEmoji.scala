package everyui.emoji

import reactify.*

/** An emoji of the app's own, a picture written by its name between colons (`:partyparrot:`), as a workspace adds its
  * own: in the picker under its `group`, completed as a composer's names are (`everyui.chat.Composer.emojiShortcodes`),
  * drawn as its picture where a message's words name it (`everyui.markdown.MarkdownView`) and on a reaction with it,
  * and read out and copied as its name. `url` is a site-absolute path or an address, as an image's is.
  *
  * {{{
  *   CustomEmoji.all @= Vector(CustomEmoji("partyparrot", "https://cdn.example.com/emoji/partyparrot.gif"),
  *     CustomEmoji("shipit", "/emoji/shipit.png", group = "Team"))
  * }}} */
case class CustomEmoji(name: String, url: String, group: String = CustomEmoji.defaultGroup) {
  /** How it is written: its name between colons. */
  def code: String = ":" + name + ":"

  /** Where its picture is had: an address, or the path resolved as the platform has it. */
  def source: String = if (url.contains("://")) url else everyui.render.Platform.resolvePath(url)
}

object CustomEmoji {
  /** The group one is in unless it is given another. */
  val defaultGroup: String = "Custom"

  /** The app's own emoji, every component that shows emoji following them: set as the workspace's come in, and again
    * as they change. A name is matched whatever its case; where two share one, the first counts. */
  val all: Var[Vector[CustomEmoji]] = Var(Vector.empty)

  /** Their groups, in the order their first emoji come. */
  def groups: Vector[String] = all().map(_.group).distinct

  /** The one named `name` (without its colons, in any case). */
  def named(name: String): Option[CustomEmoji] = {
    val wanted = name.toLowerCase
    all().find(_.name.toLowerCase == wanted)
  }

  /** The one `text` is, written by its name between colons (`:partyparrot:`): what a reaction or a choice holds. */
  def of(text: String): Option[CustomEmoji] =
    if (text.length < 3 || text.charAt(0) != ':' || text.charAt(text.length - 1) != ':') None
    else named(text.substring(1, text.length - 1))

  /** `text` cut into its stretches, each with the one it names where it is one of them: a name between colons standing
    * apart from the words around it, as `Emoji.convert` takes them (`12:30:45` is not one). */
  def split(text: String): List[(String, Option[CustomEmoji])] =
    if (all().isEmpty || text.indexOf(':') < 0) List((text, None))
    else {
      var out: List[(String, Option[CustomEmoji])] = Nil
      var from = 0
      var i = text.indexOf(':')
      while (i >= 0 && i < text.length) {
        val found = if (i > 0 && text.charAt(i - 1).isLetterOrDigit) None else at(text, i)
        found match {
          case Some((emoji, end)) =>
            if (i > from) out = (text.substring(from, i), None) :: out
            out = (text.substring(i, end), Some(emoji)) :: out
            from = end
            i = text.indexOf(':', end)
          case None => i = text.indexOf(':', i + 1)
        }
      }
      if (from < text.length) out = (text.substring(from), None) :: out
      out.reverse
    }

  // the one whose name between colons starts at `at`, and where it ends, where it stands apart
  private def at(text: String, at: Int): Option[(CustomEmoji, Int)] = {
    var j = at + 1
    while (j < text.length && Emoji.nameChar(text.charAt(j))) j += 1
    if (j >= text.length || j == at + 1 || text.charAt(j) != ':') None
    else if (j + 1 < text.length && text.charAt(j + 1).isLetterOrDigit) None
    else named(text.substring(at + 1, j)).map(e => (e, j + 1))
  }
}

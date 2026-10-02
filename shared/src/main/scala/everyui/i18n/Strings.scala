package everyui.i18n

import everyui.async.Task
import everyui.layout.LayoutPass
import everyui.render.Platform
import reactify.*

/** The app's words in the current language. A read (`Strings("signIn")`, or an accessor generated from the template
  * ARB by tools/Arb.scala) is reactive: text bound to it changes language when `use` loads another, on every backend,
  * with nothing rebuilt. A key the language lacks falls back to the template language, and a key neither has shows
  * as itself.
  *
  * Translations are ARB files among the app's assets, found by `source(code)` (`/l10n/app_<code>.arb` unless the app
  * says otherwise). `rtl` is whether the current language is written right to left (`Direction.rtl`): rows, text and
  * fields follow it. */
object Strings {
  val language: Var[Language] = Var(Language("en", "English", "English"))
  /** Whether the current language reads right to left (`Direction.rtl`, which layouts and the platform follow). */
  def rtl: Val[Boolean] = Direction.rtl
  var source: String => String = code => "/l10n/app_" + code + ".arb"

  private val table: Var[Map[String, String]] = Var(Map.empty)
  private var template: Map[String, String] = Map.empty

  /** Load the template language (the one every key exists in), then `current`: before building anything. Where they
    * are the same language its messages are loaded once. */
  def start(templateLanguage: Language, current: Language): Task[Unit] =
    load(templateLanguage.code).flatMap { messages =>
      template = messages
      if (current.code == templateLanguage.code) show(current, messages) else use(current)
    }

  /** Change language: load its messages, and every bound text follows. */
  def use(next: Language): Task[Unit] = load(next.code).flatMap(messages => show(next, messages))

  // every bound text changes at once: laid out in one pass after, and drawn with the language's own glyphs
  private def show(next: Language, messages: Map[String, String]): Task[Unit] =
    LayoutPass.batched(Task {
      Platform.setLanguage(next.code)
      table @= messages
      language @= next
      Direction.set(next.rtl)
    })

  /** The language chosen on this device before, of `supported`, if one was (`remember`). */
  def remembered(supported: List[Language]): Option[Language] = {
    val code = Platform.setting("everyui.language")
    supported.find(_.code == code)
  }

  /** Keep `language` as this device's choice. */
  def remember(language: Language): Unit = Platform.saveSetting("everyui.language", language.code)

  def apply(key: String): String = table().getOrElse(key, template.getOrElse(key, key))

  /** A message with its arguments filled in (`{name}`, plurals, selects). */
  def format(key: String, args: (String, String)*): String = Icu.format(apply(key), args.toMap)

  /** A language's messages, with `\\n` read as the line break it means: an ARB writes one that way, and Flutter's
    * generated strings turn it into a break, so a message written for both apps breaks its lines in both. */
  // parsed off the thread that draws, where there is another: an app's messages are thousands of lines, and parsing
  // them held up a television's first frame by a sixth of a second
  private def load(code: String): Task[Map[String, String]] =
    Task.async[String](done => Platform.readText(Platform.resolvePath(source(code)), done)).flatMap(text =>
      Task.off(
        try Platform.parseMessages(text).map { case (key, message) => key -> message.replace("\\n", "\n") }
        catch {
          // a table that will not parse is no table, rather than a start that never finishes
          case t: Throwable =>
            Platform.log("strings: " + code + " would not parse: " + t.toString)
            Map.empty[String, String]
        }))
}

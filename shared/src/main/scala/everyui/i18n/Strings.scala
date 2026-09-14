package everyui.i18n

import everyui.async.Task
import everyui.render.Platform
import reactify.*

/** The app's words in the current language. A read (`Strings("signIn")`, or an accessor generated from the template
  * ARB by tools/Arb.scala) is reactive: text bound to it changes language when `use` loads another, on every backend,
  * with nothing rebuilt. A key the language lacks falls back to the template language, and a key neither has shows
  * as itself.
  *
  * Translations are ARB files among the app's assets, found by `source(code)` (`/l10n/app_<code>.arb` unless the app
  * says otherwise). `rtl` is whether the current language is written right to left (`Direction.rtl`): rows, text and fields follow it. */
object Strings {
  val language: Var[Language] = Var(Language("en", "English", "English"))
  /** Whether the current language reads right to left (`Direction.rtl`, which layouts and the platform follow). */
  def rtl: Val[Boolean] = Direction.rtl
  var source: String => String = code => "/l10n/app_" + code + ".arb"

  private val table: Var[Map[String, String]] = Var(Map.empty)
  private var template: Map[String, String] = Map.empty

  /** Load the template language (the one every key exists in), then `current`: before building anything. */
  def start(templateLanguage: Language, current: Language): Task[Unit] =
    load(templateLanguage.code).flatMap { messages =>
      template = messages
      use(current)
    }

  /** Change language: load its messages, and every bound text follows. */
  def use(next: Language): Task[Unit] = load(next.code).map { messages =>
    table @= messages
    language @= next
    Direction.rtl @= next.rtl
  }

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

  private def load(code: String): Task[Map[String, String]] =
    Task.async[Map[String, String]](done => Platform.readText(Platform.resolvePath(source(code)), text => done(Arb.parse(text))))
}

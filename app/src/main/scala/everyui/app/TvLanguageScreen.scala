package everyui.app

import everyui.app.screen.{FormScreen, ScreenManager}
import everyui.component.*
import everyui.layout.FlowLayout
import everyui.net.*
import everyui.render.Platform
import reactify.*

/** The TV demo's language screen (TvDemo, `screen=language`), as an app's first screen is, over a picture: a logo,
  * centred, over eleven languages, each written in itself, as buttons 220 across in rows as many as fit across 700
  * (three), the rows centred under it. Half a second after it shows, drawn under the loading overlay, the overlay
  * goes (`TvDemo.ready`). A choice is logged ("[ui] tv: language es"). */
class TvLanguageScreen extends FormScreen {
  override def manager: ScreenManager = TvDemo
  override def heading: Val[String] = Val("")
  override def path: URLPath = path"/language"
  override protected def wanted: Double = 700.0
  override protected def framed: Boolean = false

  background @= Some("/images/header-gradient.png")

  private val buttons: List[Button] = TvLanguageScreen.languages.map { case (code, name) =>
    val button = new Button(name, () => Platform.log("tv: language " + code), ButtonStyle.Tonal, points = 24.0)
    button.minWidth @= TvLanguageScreen.tile
    button
  }

  override def defaultFocus(): Component = buttons.head

  override protected def header(): Component = Centered(width)(ImageView("/images/logo.png", 44.0))

  override protected def build(): Unit = {
    val across = math.max(1, ((width + 20.0) / (TvLanguageScreen.tile + 20.0)).toInt)
    val grid = new Container {
      layout @= Some(FlowLayout(20.0, 20.0))
      size.width @= math.min(width, across * TvLanguageScreen.tile + (across - 1) * 20.0)
      size.height := contentHeight()
      semantics @= types.Semantics(label = "languages")
      children ++= buttons
    }
    fields.children += Centered(width)(grid)
  }

  // a moment after it is up, drawn under the overlay, the overlay goes
  override protected def activate(): everyui.async.Task[Unit] =
    super.activate().map(_ => Platform.after(0.5, () => TvDemo.ready()))
}

object TvLanguageScreen {
  /** A language's button across. */
  val tile: Double = 220.0

  val languages: List[(String, String)] = List("en" -> "English", "es" -> "Español", "pt" -> "Português",
    "fr" -> "Français", "de" -> "Deutsch", "ar" -> "العربية", "tr" -> "Türkçe", "nl" -> "Nederlands",
    "it" -> "Italiano", "zh" -> "简体中文", "zh_Hant" -> "繁體中文")
}

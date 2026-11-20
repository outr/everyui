package everyui.app

import everyui.chat.Composer
import everyui.component.{Button, ButtonStyle, Column, Container, IconButton, TextView}
import everyui.font.MaterialIcon
import everyui.kit.{Toast, ToastPlacement}
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A chat-like screen on a phone: a header with a bell whose inbox is a long menu (`Menu.below`), a composer at the
  * foot that toasts keep clear of (`Toast.keepClear`), and the header's height as the toasts' `topInset`. Buttons show
  * a toast at the foot, at the head, and one with Undo, and drop a short menu. `place=top` puts every toast at the
  * head (`Toast.placement`); `open=inbox` (or `open=short`) drops a menu a second in, and `toast=bottom`, `top` or
  * `undo` shows a toast that stays a minute. Logs `bell menu up sheet=<whether>`, `bell menu dismissed`, `bell chose
  * <entry>` and `bell toast <where> at <top>`. */
object BellDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    if (Platform.launchArg("place") == "top") Toast.placement @= ToastPlacement.Top

    lazy val bell: IconButton = new IconButton(MaterialIcon.Notifications, "Inbox", () => inbox(bell, 40))
    val header = new Container
    header.size.width := ui.size.width()
    header.size.height @= 56.0
    header.backgroundColor := Theme.raised()
    val title = TextView("Team chat", 18.0)
    title.position.left @= 16.0
    title.position.top := (header.size.height() - title.size.height()) / 2.0
    bell.position.left := header.size.width() - bell.size.width() - 8.0
    bell.position.top := (header.size.height() - bell.size.height()) / 2.0
    header.children ++= List(title, bell)

    lazy val short: Button = new Button("Short menu", () => inbox(short, 3), ButtonStyle.Outlined)
    val buttons = Column(8.0, padding = 16.0)(
      TextView("Ada: the build is green again.", 14.0),
      TextView("Grace: shipping it after lunch.", 14.0),
      new Button("Toast at the foot", () => toast("bottom", 4.0), ButtonStyle.Outlined),
      new Button("Toast at the head", () => toast("top", 4.0), ButtonStyle.Outlined),
      new Button("Toast with Undo", () => toast("undo", 4.0), ButtonStyle.Outlined),
      short)
    buttons.position.top := header.size.height()

    val composer = new Composer(d => Platform.log("bell sent " + d.text))
    composer.size.width := ui.size.width() - 16.0
    composer.position.left @= 8.0
    composer.position.top := ui.size.height() - composer.size.height() - 8.0
    composer.placeholder @= "Message the team"

    ui.children ++= List(header, buttons, composer)
    Toast.topInset := header.size.height()
    Toast.keepClear(composer)
    Platform.log("bell ok")

    val open = Platform.launchArg("open")
    if (open.nonEmpty) Platform.after(1.0, () => if (open == "short") inbox(short, 3) else inbox(bell, 40))
    val shown = Platform.launchArg("toast")
    if (shown.nonEmpty) Platform.after(1.0, () => toast(shown, 60.0))
  }

  // `count` entries dropped from `anchor`: a bell's inbox, most of them with a line of detail
  private def inbox(anchor: everyui.component.Component, count: Int): Unit = {
    val items = (1 to count).toList.map { i =>
      MenuItem("Agent " + i.toString + " finished", () => Platform.log("bell chose " + i.toString), MaterialIcon.Mail,
        detail = "Its report is waiting " + i.toString + " min ago")
    }
    val menu = Menu.below(anchor, items, dismissed = () => Platform.log("bell menu dismissed"))
    Platform.after(0.5, () => Platform.log("bell menu up sheet=" + menu.isSheet.toString))
  }

  private def toast(kind: String, seconds: Double): Unit = {
    if (kind == "undo") Toast.show("Message archived", seconds, action = Some("Undo" -> (() => Platform.log("bell undo"))))
    else if (kind == "top") Toast.show("Saved to your notes", seconds, top = true)
    else Toast.show("Copied to the clipboard", seconds)
    Platform.after(0.5, () => Platform.log("bell toast " + kind + " at " +
      ui.children().lastOption.map(_.position.top().toString).getOrElse("-")))
  }
}

package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Component, Container, Row, ScrollContainer, TextView}
import everyui.clipboard.Clipboard
import everyui.file.{Files, SaveResult}
import everyui.PlatformKind
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** The platform's own services, one section each, with what each answered written under it and logged
  * (`[ui] platform save Saved notes.txt`): saving a file, as bytes or from a URL; the clipboard, copied to and read.
  * Each section says whether the platform has it. `theme=light` shows the light palette. */
class PlatformDemo {
  private val width = math.min(720.0, ui.size.width() - 32.0)

  private val saved: Var[String] = Var("Nothing saved yet")
  private val pasted: Var[String] = Var("Nothing read yet")

  private val column = Column(20.0, padding = 16.0)(
    TextView("The platform", 22.0),
    section("Save a file", Files.savable, saved,
      new Button("Save notes.txt", () => save(), ButtonStyle.Filled),
      new Button("Save a picture from its URL", () => saveUrl())),
    section("The clipboard", Clipboard.readable, pasted,
      new Button("Copy a line", () => {
        Clipboard.copy("Copied from EveryUI")
        tell(pasted, "copy", "Copied \"Copied from EveryUI\"")
      }),
      new Button("Read the clipboard", () =>
        Clipboard.read().map(t => tell(pasted, "read", t.map(w => "Read \"" + w + "\"").getOrElse("Nothing to read"))).start(),
        ButtonStyle.Filled))
  )

  private val scroll = new ScrollContainer {
    size.width := ui.size.width()
    size.height := ui.size.height()
  }
  scroll.content.children += column
  ui.children += scroll

  /** A section: its title, whether the platform has it, its buttons, and what it last answered. */
  private def section(title: String, supported: Boolean, answer: Var[String], buttons: Component*): Container =
    Column(8.0)(
      TextView(title, 16.0),
      TextView(if (supported) "Supported here" else "Not supported here", 12.0, Theme.muted()),
      Row(10.0, middle = true)(buttons*),
      TextView.paragraph(answer(), width, 13.0, Theme.muted())
    )

  private def tell(answer: Var[String], what: String, words: String): Unit = {
    answer @= words
    Platform.log("platform " + what + " " + words)
  }

  private def said(r: SaveResult): String = r match {
    case SaveResult.Saved(name) => "Saved " + name.getOrElse("(to the browser's downloads)")
    case SaveResult.Cancelled => "Cancelled"
    case SaveResult.SaveFailed(why) => "Not saved: " + why
  }

  private def save(): Unit = {
    val words = "Notes from EveryUI\nsaved at " + Platform.localDateTime(System.currentTimeMillis()) + "\n"
    Files.save(words.getBytes("UTF-8"), "notes.txt").map(r => tell(saved, "save", said(r))).start()
  }

  /** The page's own logo on the web; elsewhere a picture on the web (`url=` for another). */
  private def saveUrl(): Unit = {
    val asked = Platform.launchArg("url")
    val url = if (asked.nonEmpty) asked else if (Platform.kind == PlatformKind.Web) "images/logo.png"
      else "https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"
    Files.saveUrl(url, url.substring(url.lastIndexOf("/") + 1)).map(r => tell(saved, "saveUrl", said(r))).start()
  }
}

object PlatformDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    new PlatformDemo
  }
}

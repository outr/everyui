package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Container, ScrollContainer, TextView}
import everyui.file.Files
import everyui.layout.FlowLayout
import everyui.network.{FormPart, HttpCall, HttpFiles, Network}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import fabric.rw.*
import reactify.*

/** Files to a server (`everyui.network`): a multipart upload of a JSON field and a file of every byte value, or of one
  * the person picks. `api=<origin>` is the server (the page's own on the web), which answers `POST /demo/upload` with
  * what it made of the parts. Each answer is written under its section and logged (`[ui] transfer upload 200 …`).
  * `theme=light` shows the light palette. */
class TransferDemo {
  private val width = math.min(720.0, ui.size.width() - 32.0)

  private val uploaded: Var[String] = Var("Nothing uploaded yet")

  private val column = Column(20.0, padding = 16.0)(
    TextView("Files and a server", 22.0),
    section("Upload (multipart: a JSON field and a file)", uploaded, "every byte value, and a name with quotes")(
      new Button("Upload", () => upload(), ButtonStyle.Filled),
      new Button("Pick a file and upload it", () => pickAndUpload()))
  )

  private val scroll = new ScrollContainer {
    size.width := ui.size.width()
    size.height := ui.size.height()
  }
  scroll.content.children += column
  ui.children += scroll

  /** A JSON field and a file holding every byte value twice (CR, LF and "--" among them), named with quotes and an é. */
  private def upload(): Unit = {
    val bytes = Array.tabulate[Byte](512)(i => (i % 256).toByte)
    sent(HttpCall.multipart("/demo/upload", List(
      FormPart.text("meta", "{\"user\":\"demo\",\"note\":\"line one\\nline two\"}"),
      FormPart.file("file", "pic \"1\" é.bin", bytes, "application/octet-stream"))))
  }

  /** A file the person picks, uploaded as it was picked, with a JSON field. */
  private def pickAndUpload(): Unit = Files.pick(multiple = false).map(_.headOption match {
    case None => tell(uploaded, "upload", "Nothing picked")
    case Some(picked) =>
      HttpFiles.part("file", picked).map(part => sent(HttpCall.multipart("/demo/upload",
        List(FormPart.json("meta", Map("user" -> "demo")), part)))).start()
  }).start()

  private def sent(call: HttpCall): Unit =
    Network.http(call).map(r => tell(uploaded, "upload", r.status.toString + " " + r.body)).handleError { t =>
      tell(uploaded, "upload", "Failed: " + t.getMessage)
      rapid.Task.unit
    }.start()

  private def section(title: String, answer: Var[String], note: String)(buttons: Button*): Container = {
    val row = new Container {
      layout @= Some(FlowLayout(10.0, 10.0))
      size.width @= width
      children ++= buttons
    }
    Column(8.0)(TextView(title, 16.0), TextView(note, 12.0, Theme.muted()), row,
      TextView.paragraph(answer(), width, 13.0, Theme.muted()))
  }

  private def tell(answer: Var[String], what: String, words: String): Unit = {
    answer @= words
    Platform.log("transfer " + what + " " + words)
  }
}

object TransferDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val api = Platform.launchArg("api")
    if (api.nonEmpty) Network.origin = api
    new TransferDemo
  }
}

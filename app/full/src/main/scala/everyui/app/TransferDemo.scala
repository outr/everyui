package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Container, ScrollContainer, TextView}
import everyui.file.{Files, SaveResult}
import everyui.layout.FlowLayout
import everyui.network.{FormPart, HttpCall, HttpFiles, HttpResult, Network}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import fabric.rw.*
import reactify.*

/** Files to and from a server (`everyui.network`): an export a POST answers with, saved where the person chooses
  * (`HttpFiles.save`), named by the server or given a name first; its bytes read (`binary`); and a multipart upload of
  * a JSON field and a file of every byte value, or one picked. `api=<origin>` is the server (the page's own on the web), which answers
  * `POST /demo/export` (`{"kind":"png"|"zip"}`) with the file and `POST /demo/upload` with what it made of the parts.
  * Each answer is written under its section and logged (`[ui] transfer export-png Saved logo.png`). `theme=light`
  * shows the light palette. */
class TransferDemo {
  private val width = math.min(720.0, ui.size.width() - 32.0)

  private val exported: Var[String] = Var("Nothing exported yet")
  private val read: Var[String] = Var("Nothing read yet")
  private val uploaded: Var[String] = Var("Nothing uploaded yet")

  private val column = Column(20.0, padding = 16.0)(
    TextView("Files and a server", 22.0),
    section("Export (a POST answered with a file)", exported,
      if (Files.savable) "saved where you choose" else "nowhere to save here")(
      new Button("Export the picture", () => exportAs("png", None), ButtonStyle.Filled),
      new Button("Export a zip as export.zip", () => exportAs("zip", Some("export.zip")))),
    section("Bytes", read, "the answer as bytes, with its type and name")(
      new Button("Read the picture's bytes", () => Network.http(exportCall("png").copy(binary = true)).map { r =>
        tell(read, "bytes", described(r))
      }.handleError { t =>
        tell(read, "bytes", "Failed: " + t.getMessage)
        rapid.Task.unit
      }.start(), ButtonStyle.Filled)),
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

  private def exportCall(kind: String): HttpCall =
    HttpCall("POST", "/demo/export", body = Some("{\"kind\":\"" + kind + "\",\"auth\":\"demo-token\"}"))

  private def exportAs(kind: String, name: Option[String]): Unit =
    HttpFiles.save(exportCall(kind), name).map(r => tell(exported, "export-" + kind, said(r))).start()

  /** A JSON field and a file holding every byte value twice (CR, LF and "--" among them), named with quotes and an é. */
  private def upload(): Unit = {
    val bytes = Array.tabulate[Byte](512)(i => (i % 256).toByte)
    val call = HttpCall.multipart("/demo/upload", List(
      FormPart.text("meta", "{\"user\":\"demo\",\"note\":\"line one\\nline two\"}"),
      FormPart.file("file", "pic \"1\" é.bin", bytes, "application/octet-stream")))
    Network.http(call).map(r => tell(uploaded, "upload", r.status.toString + " " + r.body)).handleError { t =>
      tell(uploaded, "upload", "Failed: " + t.getMessage)
      rapid.Task.unit
    }.start()
  }

  /** A file the person picks, uploaded as it was picked, with a JSON field. */
  private def pickAndUpload(): Unit = Files.pick(multiple = false).map(_.headOption match {
    case None => tell(uploaded, "upload", "Nothing picked")
    case Some(picked) => HttpFiles.part("file", picked).flatMap { part =>
      Network.http(HttpCall.multipart("/demo/upload", List(FormPart.json("meta", Map("user" -> "demo")), part)))
    }.map(r => tell(uploaded, "upload", r.status.toString + " " + r.body)).handleError { t =>
      tell(uploaded, "upload", "Failed: " + t.getMessage)
      rapid.Task.unit
    }.start()
  }).start()

  private def described(r: HttpResult): String = {
    val bytes = r.bytes.getOrElse(Array.empty[Byte])
    "HTTP " + r.status.toString + ", " + bytes.length.toString + " bytes of " + r.mime.getOrElse("no type") + ", named " +
      r.fileName.getOrElse("nothing") + ", fnv " + fnv(bytes)
  }

  /** FNV-1a over the bytes, in hex: what the check compares with the server's file. */
  private def fnv(bytes: Array[Byte]): String = {
    var h = 0x811c9dc5L
    bytes.foreach { b =>
      h = ((h ^ (b & 0xff).toLong) * 0x01000193L) & 0xffffffffL
    }
    java.lang.Long.toHexString(h)
  }

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

  private def said(r: SaveResult): String = r match {
    case SaveResult.Saved(name) => "Saved " + name.getOrElse("(to the browser's downloads)")
    case SaveResult.Cancelled => "Cancelled"
    case SaveResult.SaveFailed(why) => "Not saved: " + why
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

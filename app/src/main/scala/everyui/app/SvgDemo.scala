package everyui.app

import everyui.chat.{Avatar, Participant}
import everyui.component.{Container, ImageView, TextView}
import everyui.image.Image
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** SVG pictures through `Image`: on each line an `Avatar` (40 px) and an `ImageView` (96 px tall) of a tile, a
  * rounded square with a white star under a group's translate and scale and a half-clear ring, from the app's own
  * files (images/svg-tile.svg, orange), from a server by its .svg (teal) and by its first bytes alone (red, an address
  * with no extension), and from a `data:` address (purple); and last a `data:` address that is no picture, its
  * avatar's initial standing in. The server is `svghost=` (http://10.0.2.2:8765 on Android, the machine running the
  * emulator; http://127.0.0.1:8765 elsewhere), serving tile.svg and tile-noext. `theme=light` for the light palette.
  * `--freeze` takes its frame two seconds in. Logs `svg ok` once built and `svg <line> <width>x<height>` as each
  * picture's size comes (0x0 for one that will not). */
object SvgDemo {
  private val data: String = "data:image/svg+xml;base64," +
    "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0OCIgaGVpZ2h0PSI0OCIgdmlld0JveD0iMCAw" +
    "IDQ4IDQ4Ij4KICA8cmVjdCB3aWR0aD0iNDgiIGhlaWdodD0iNDgiIHJ4PSIxMiIgZmlsbD0iIzdFNTdDMiIvPgogIDxnIHRyYW5z" +
    "Zm9ybT0idHJhbnNsYXRlKDEyIDEyKSBzY2FsZSgxLjUpIiBmaWxsPSIjZmZmIj4KICAgIDxwYXRoIGQ9Ik04IDBROSA3IDE2IDgg" +
    "OSA5IDggMTYgNyA5IDAgOCA3IDcgOCAwWiIvPgogIDwvZz4KICA8Y2lyY2xlIGN4PSIyNCIgY3k9IjI0IiByPSIxOCIgZmlsbD0i" +
    "bm9uZSIgc3Ryb2tlPSIjZmZmIiBzdHJva2Utb3BhY2l0eT0iMC41IiBzdHJva2Utd2lkdGg9IjIiLz4KPC9zdmc+Cg=="

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val asked = Platform.launchArg("svghost")
    val host = if (asked.nonEmpty) asked
      else if (Platform.kind == everyui.PlatformKind.Android) "http://10.0.2.2:8765" else "http://127.0.0.1:8765"
    val lines = List("asset" -> "images/svg-tile.svg", "http" -> (host + "/tile.svg"),
      "sniffed" -> (host + "/tile-noext"), "data" -> data, "broken" -> "data:image/png;base64,AAAA")
    val sheet = new Container
    var top = 24.0
    lines.foreach { case (name, source) =>
      val words = new TextView {
        content @= name
        font.size @= 14.0
        color := Theme.text()
      }
      words.position.left @= 24.0
      words.position.top @= top + 40.0
      val who = Participant("p-svg-" + name, name.substring(0, 1).toUpperCase + name.substring(1), Some(source))
      val face = new Avatar(who, 40.0)
      face.position.left @= 120.0
      face.position.top @= top + 28.0
      val picture = ImageView(source, 96.0)
      picture.position.left @= 184.0
      picture.position.top @= top
      sheet.children ++= List(words, face, picture)
      Image(source).map(i => Platform.log("svg " + name + " " + i.width.toInt + "x" + i.height.toInt)).startUnit()
      top += 112.0
    }
    sheet.size.width @= 320.0
    sheet.size.height @= top
    ui.children += sheet
    Platform.log("svg ok")
    // a frozen frame (`--freeze`) once the server's pictures have had time to come
    if (Platform.freezeSeconds() >= 0.0) Platform.after(2.0, () => Platform.frozen())
  }
}

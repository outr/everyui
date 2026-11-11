package everyui.app

import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.{Container, ImageView, TextView}
import everyui.image.Image
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** Pictures from everywhere a platform loads them, each in a box of its own with its name under it: a poster and an
  * SVG tile from a server (`picturehost=`, http://127.0.0.1:8766 unless named; it serves poster.jpg, big.jpg,
  * icon.png and tile.svg), a public one over HTTPS (`public=`), a 3840 x 2160 picture in a box 320 x 180 (decoded no
  * larger than it is drawn), the app's own SVG, an Avatar's picture from the server and one whose picture is not
  * there (its initial stays), the poster blurred, the white icon tinted, a 404, a host that is not there, and a box
  * whose picture is changed six times in a moment, the big one among them, ending on a red square written into its
  * address: the red square is what shows, never one asked for before it. Logs `pictures <name> <width>x<height>` as
  * each one's size comes (0x0 for one that will not), and `pictures ok` once built. `--freeze` takes its frame four
  * seconds in. */
object PicturesDemo {
  private val red: String = "data:image/png;base64," +
    "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFUlEQVQI12N8amnKgA0wMeAAg1MCAA35AWMdRXK4AAAAAElFTkSuQmCC"

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val asked = Platform.launchArg("picturehost")
    val host = if (asked.nonEmpty) asked else "http://127.0.0.1:8766"
    val public = if (Platform.launchArg("public").nonEmpty) Platform.launchArg("public")
      else "https://picsum.photos/id/1025/640/360"
    val sheet = new Container
    var at = 0
    // the 14 boxes as large as the screen has room for, at most their own size, in as many columns as that takes
    val boxes = 14
    def fits(c: Int): Double = math.min(1.0, math.min((Platform.screenWidth() - 12.0) / (c * 344.0 + 12.0),
      (Platform.screenHeight() - 12.0) / (((boxes + c - 1) / c) * 236.0 + 12.0)))
    val across = (1 to 4).maxBy(fits)
    val k = fits(across)

    // a box `w` by `h` at the next place on the sheet, its name under it
    def place(name: String, w: Double, h: Double, c: everyui.component.Component): Unit = {
      val left = (12.0 + (at % across) * 344.0) * k
      val top = (12.0 + (at / across) * 236.0) * k
      c.position.left @= left
      c.position.top @= top
      val words = new TextView {
        content @= name
        font.size @= math.max(9.0, 14.0 * k)
        color := Theme.text()
      }
      words.position.left @= left
      words.position.top @= top + (h + 6.0) * k
      sheet.children ++= List(c, words)
      at += 1
    }
    def picture(name: String, source: String, w: Double, h: Double): ImageView = {
      val view = new ImageView {
        sizedByPicture @= false
        size.width @= w * k
        size.height @= h * k
        backgroundColor @= Color.fromHex("#80808033")
      }
      Image(source).map { i =>
        Platform.log("pictures " + name + " " + i.width.toInt + "x" + i.height.toInt)
        view.image @= i
      }.startUnit()
      place(name, w, h, view)
      view
    }

    picture("poster", host + "/poster.jpg", 120.0, 180.0)
    picture("https", public, 320.0, 180.0)
    picture("big", host + "/big.jpg", 320.0, 180.0)
    picture("svg", host + "/tile.svg", 180.0, 180.0)
    picture("svg asset", "images/svg-tile.svg", 96.0, 96.0)
    val face = new Avatar(Participant("p-pictures-face", "Face", Some(host + "/poster.jpg")), 96.0 * k)
    place("avatar", 96.0, 96.0, face)
    val nobody = new Avatar(Participant("p-pictures-none", "Nobody", Some(host + "/missing-face.png")), 96.0 * k)
    place("avatar 404", 96.0, 96.0, nobody)
    val blurred = picture("blur 6", host + "/poster.jpg", 120.0, 180.0)
    blurred.blur @= 6.0
    val tinted = picture("tint", host + "/icon.png", 96.0, 96.0)
    tinted.tint @= Some(Color.fromHex("#e53935"))
    picture("404", host + "/missing.png", 120.0, 180.0)
    picture("no host", "http://nowhere.invalid/x.png", 120.0, 180.0)

    // changed again and again before any could come: only the last is drawn
    val changing = new ImageView {
      sizedByPicture @= false
      size.width @= 180.0 * k
      size.height @= 180.0 * k
      backgroundColor @= Color.fromHex("#80808033")
    }
    place("last asked", 180.0, 180.0, changing)
    List(host + "/big.jpg", host + "/poster.jpg", public, host + "/tile.svg", host + "/big.jpg?again", red)
      .foreach(s => changing.image @= new Image(s, 0.0, 0.0))

    sheet.size.width @= (12.0 + across * 344.0) * k
    sheet.size.height @= (12.0 + ((at + across - 1) / across) * 236.0) * k
    ui.children += sheet
    Platform.log("pictures ok")
    if (Platform.freezeSeconds() >= 0.0) Platform.after(4.0, () => Platform.frozen())
  }
}

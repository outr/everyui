package everyui.app

import everyui.component.{AudioPlayer, Container, ImageView, TextView}
import everyui.image.Image
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** What a Roku channel fetches rather than ships, once its build has split it (the example's `roku/emitRoku` with
  * `-Deveryui.rokuAssets=<base>`: RokuChannel.remote): a picture, words in Roboto Mono (a face the package leaves out,
  * drawn in its stand-in until it arrives, then drawn and measured again), Chinese words (the CJK face, fetched as they
  * are drawn), a translation into French read as the app would read it, and a cheer played as an effect a second in and
  * again at four. Each is logged ("[ui] remote demo: …"), the picture's size and the French words as they arrive. Every
  * other platform, and a channel not split, has every file of its own, and shows the same. */
object RemoteDemo {
  /** The Chinese words: the example's build cuts the CJK face to them (build.sbt's `remoteDemoWords`, the same). */
  val chinese: String = "远程资源：图片、声音和文字"

  def run(): Unit = {
    // in a face the app ships, as an app's words are: the system font has no CJK, and words in it are not drawn in runs
    Theme.family @= "Open Sans"
    val column = new Container
    column.layout @= Some(VerticalLayout(16.0))
    column.position.left @= 40.0
    column.position.top @= 30.0
    ui.children += column

    val picture = new ImageView {
      sizedByPicture @= false
      size.width @= 160.0
      size.height @= 160.0
    }
    Image(Platform.resolvePath("/images/logo.png")).map { i =>
      Platform.log("remote demo: picture " + i.width.toInt + "x" + i.height.toInt)
      picture.image @= i
    }.startUnit()

    val mono = new TextView
    mono.font.family @= "Roboto Mono"
    mono.font.size @= 20.0
    mono.content @= "Roboto Mono, fetched: 0123456789"
    val cjk = new TextView
    cjk.font.size @= 24.0
    cjk.content @= chinese
    val french = new TextView
    french.font.size @= 18.0
    french.content @= "fr: …"
    column.children ++= List(picture, mono, cjk, french)

    Platform.readText(Platform.resolvePath("/l10n/app_fr.arb"), text => {
      val lines = text.split('\n').count(_.contains("\":"))
      french.content @= "fr: " + lines + " entries"
      Platform.log("remote demo: app_fr.arb, " + text.length + " characters, " + lines + " entries")
    })

    val cheer = new AudioPlayer
    cheer.effect @= true
    cheer.events(e => Platform.log("remote demo: cheer " + e))
    column.children += cheer
    val source = Platform.resolvePath("/sounds/demo-cheer.wav")
    Platform.after(1.0, () => cheer.source @= source)
    Platform.after(4.0, () => {
      cheer.source @= ""
      cheer.source @= source
    })
    Platform.after(6.0, () => Platform.log("remote demo: mono " + mono.measured.width().toInt + " wide, cjk " +
      cjk.measured.width().toInt + " wide"))
  }
}

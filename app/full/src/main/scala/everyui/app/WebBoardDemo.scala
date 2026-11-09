package everyui.app

import everyui.Color
import everyui.Shadow
import everyui.component.{Container, TextView, WebView}
import everyui.motion.{Keyframe, Track}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** A web view playing a page over a busy board, as a game shows a trailer over its own: a board of thirty tiles with
  * a countdown ticking each second, and a page 420 by 360 in a rounded panel at its middle, shown `at` seconds after
  * the launch (3) and taken apart `for` seconds later (30; 0 keeps it). `pulse=1` sets some of the tiles pulsing for
  * as long as the board shows (still otherwise, but for the countdown). `page=<url>` is the page (a muted, looping
  * `<video>` of a test pattern served to the device is the one to measure; an animated canvas by default), `web=0`
  * leaves it out (the board alone), `tint=1` dims and warms the screen as a night setting does (`Platform.tint`), and
  * `prewarm=1` warms the engine as the board opens (`WebView.prewarm`). Logs "webboard shown in <ms>" (the main
  * thread's time to make it) and "webboard removed" as the page comes and goes, and each ten seconds the frames
  * Android drew and how many of them were slow ("webboard frames: …"). */
object WebBoardDemo {
  private val slow = 32.0
  private val lock = new Object
  private var frames = 0
  private var slowFrames = 0
  private var worst = 0.0
  private var worstBuild = 0.0

  /** An animated canvas, so the page draws every frame where no video is given. */
  private val canvas: String = "data:text/html," + java.net.URLEncoder.encode(
    "<html><body style='margin:0;background:#123'><canvas id=c width=420 height=360></canvas><script>" +
      "var c=document.getElementById('c').getContext('2d'),t=0;" +
      "function f(){t++;c.fillStyle='hsl('+(t%360)+',60%,40%)';" +
      "c.fillRect(0,0,420,360);c.fillStyle='#fff';c.font='48px sans-serif';c.fillText(t,40,180);" +
      "requestAnimationFrame(f)}f()</script></body></html>", "UTF-8").replace("+", "%20")

  def run(): Unit = {
    ui.backgroundColor @= Color.fromHex("101828FF")
    if (Platform.launchArg("prewarm") == "1") WebView.prewarm()
    if (Platform.launchArg("tint") == "1") Platform.tint(0.8, 0.72, 0.5)
    val count = new TextView {
      content @= "30"
      font.size @= 40.0
      color @= Color.White
      position.left @= 24.0
      position.top @= 16.0
    }
    ui.children += count
    val pulse = Platform.launchArg("pulse") == "1"
    val across = 6
    val down = 5
    for (i <- 0 until across * down) {
      val tile = new Container {
        size.width := (ui.size.width() - 48.0) / across - 12.0
        size.height := (ui.size.height() - 96.0) / down - 12.0
        position.left := 24.0 + (i % across) * (ui.size.width() - 48.0) / across
        position.top := 80.0 + (i / across) * (ui.size.height() - 96.0) / down
        radius @= 12.0
        backgroundColor @= Color.fromHex(if (i % 3 == 0) "2B3A67FF" else if (i % 3 == 1) "3D2B67FF" else "1F4F4AFF")
        shadow @= Some(Shadow(0.0, 4.0, 12.0, Color.fromHex("00000099")))
      }
      tile.children += new TextView {
        content @= "Answer " + (i + 1).toString
        font.size @= 18.0
        color @= Color.White
        position.left @= 12.0
        position.top @= 10.0
      }
      if (pulse && i % 7 == 0) tile.motion @= List(Track(Track.Scale, 1.6,
        List(Keyframe(0.0, 1.0, 1.0), Keyframe(0.5, 1.05, 1.05), Keyframe(1.0, 1.0, 1.0)), delay = i * 0.1))
      ui.children += tile
    }
    var left = 30
    def tick(): Unit = Platform.after(1.0, () => {
      left = if (left <= 0) 30 else left - 1
      count.content @= left.toString
      tick()
    })
    tick()
    if (Platform.launchArg("web") != "0") {
      val at = Option(Platform.launchArg("at")).filter(_.nonEmpty).map(_.toDouble).getOrElse(3.0)
      val shown = Option(Platform.launchArg("for")).filter(_.nonEmpty).map(_.toDouble).getOrElse(30.0)
      val page = Option(Platform.launchArg("page")).filter(_.nonEmpty).getOrElse(canvas)
      Platform.after(at, () => {
        val began = System.nanoTime()
        val web = new WebView("WebBoard")
        web.size.width @= 420.0
        web.size.height @= 360.0
        val panel = new Container {
          size.width @= 420.0
          size.height @= 360.0
          radius @= 14.0
          // raised above the tiles, whose shadows raise them above whatever has none
          shadow @= Some(Shadow(0.0, 8.0, 32.0, Color.fromHex("000000CC")))
          position.left := (ui.size.width() - 420.0) / 2.0
          position.top := (ui.size.height() - 360.0) / 2.0
          children += web
        }
        web.source @= page
        ui.children += panel
        Platform.log("webboard shown in " + ((System.nanoTime() - began) / 1000000L).toString + " ms")
        if (shown > 0.0) Platform.after(shown, () => {
          ui.children -= panel
          panel.dispose()
          Platform.log("webboard removed")
        })
      })
    }
    Platform.frameTimings((ms: Double, build: Double) => lock.synchronized {
      frames += 1
      if (ms > slow) slowFrames += 1
      if (ms > worst) {
        worst = ms
        worstBuild = build
      }
    })
    def report(): Unit = Platform.after(10.0, () => {
      lock.synchronized {
        Platform.log("webboard frames: " + slowFrames.toString + "/" + frames.toString + " slow, worst " +
          math.round(worst).toString + " ms (build " + math.round(worstBuild).toString + " ms)")
        frames = 0
        slowFrames = 0
        worst = 0.0
        worstBuild = 0.0
      }
      report()
    })
    report()
    Platform.log("webboard ok")
  }
}

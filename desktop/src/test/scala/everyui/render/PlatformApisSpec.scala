package everyui.render

import everyui.FxSpec
import everyui.app.UrlTarget
import everyui.component.WebView
import everyui.date.TimeZones
import everyui.file.{FileTypes, Files}
import everyui.perf.Marks

/** What an app asks of the platform rather than of a browser, as the desktop answers it: a window's own settings, its
  * focus and its closing, the time zone, a source made of bytes, marks for a check, and what it has none of (a second
  * window, a reload, a page to ask). */
class PlatformApisSpec extends FxSpec {
  "The desktop" should {
    "keep a window's settings for the run, and forget one set empty" in {
      Platform.windowSetting("layout") shouldBe ""
      Platform.saveWindowSetting("layout", "split")
      Platform.windowSetting("layout") shouldBe "split"
      Platform.saveWindowSetting("layout", "")
      Platform.windowSetting("layout") shouldBe ""
    }
    "replace the path without a history to go forward through" in {
      val was = Platform.path()
      Platform.replacePath("/join")
      Platform.path() shouldBe "/join"
      Platform.historyForward() shouldBe false
      Platform.replacePath(was)
    }
    "tell its window's focus once each way, and its closing" in {
      var told = List.empty[String]
      val stopFocus = Platform.onFocus(() => told = told :+ "focus")
      val stopBlur = Platform.onBlur(() => told = told :+ "blur")
      val stopClose = Platform.onClose(() => told = told :+ "close")
      Platform.windowFocus(false)
      Platform.windowFocus(false)
      Platform.windowFocus(true)
      Platform.closing()
      stopFocus()
      stopBlur()
      stopClose()
      Platform.windowFocus(false)
      Platform.windowFocus(true)
      Platform.closing()
      told shouldBe List("blur", "focus", "close")
    }
    "name its time zone as IANA does, and tell a change once as it comes back" in {
      Platform.timeZone() shouldBe java.util.TimeZone.getDefault.getID
      val was = java.util.TimeZone.getDefault
      var changes = 0
      val stop = TimeZones.onChange(() => changes += 1)
      try {
        TimeZones.check()
        changes shouldBe 0
        val other = if (was.getID == "Asia/Tokyo") "Europe/Oslo" else "Asia/Tokyo"
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(other))
        Platform.resumed()
        Platform.windowFocus(false)
        Platform.windowFocus(true)
        changes shouldBe 1
      }
      finally {
        stop()
        java.util.TimeZone.setDefault(was)
        TimeZones.check()
      }
    }
    "show bytes from a file of its own, gone once released" in {
      val source = Files.source("<p>hi</p>".getBytes("UTF-8"), "text/html; charset=utf-8")
      source should startWith("file:")
      source should endWith(".html")
      val file = new java.io.File(new java.net.URI(source))
      new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8") shouldBe "<p>hi</p>"
      Files.release(source)
      file.exists shouldBe false
      FileTypes.extensionOf("image/jpeg") shouldBe "jpg"
      FileTypes.extensionOf("application/x-unknown") shouldBe "bin"
    }
    "keep the last marks, oldest let go" in {
      Marks.clear()
      (1 to Marks.kept + 5).foreach(i => Marks.mark("m" + i.toString))
      Marks.recent.length shouldBe Marks.kept
      Marks.recent.head._1 shouldBe "m6"
      Marks.last("m" + (Marks.kept + 5).toString).nonEmpty shouldBe true
      Marks.last("m1") shouldBe None
      Marks.clear()
    }
    "have no second window, no reload and a network it takes for granted" in {
      Platform.openWindow("/w/3") shouldBe false
      Platform.reload() shouldBe false
      Platform.online() shouldBe true
      Platform.registerColorEmoji(everyui.font.Faces.OpenSansRegular) shouldBe false
    }
    "answer an ask with nothing where there is no page to ask" in {
      var answer: Option[Option[String]] = None
      fx(new WebView("bridge").ask("ping")(a => answer = Some(a)))
      answer shouldBe Some(None)
    }
  }

  "A window kept for later" should {
    "go once or be let go once, and nothing after" in {
      var told = List.empty[String]
      val target = new UrlTarget(url => told = told :+ url, () => told = told :+ "cancel")
      target.go("https://a.example.com")
      target.go("https://b.example.com")
      target.cancel()
      val other = new UrlTarget(url => told = told :+ url, () => told = told :+ "cancel")
      other.cancel()
      other.go("https://c.example.com")
      told shouldBe List("https://a.example.com", "cancel")
    }
  }
}

package everyui.chat

import everyui.component.{Component, Container, TextView}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** A tool call's card whose summary does not fit on a line (words wrap by default): as wide as the transcript lets it
  * be, its summary wrapped within it, as few lines as the words need and no room left under them, at a desktop's width
  * and a phone's; a summary that fits still a pill as wide as it is, on one line. */
class ActivityWrapSpec extends FxSpec {
  private val long = "update_page · pageId: f8lV, html: <main class=\"wrap\"><h1>Vellora</h1><p class=\"tag\">A Late " +
    "Cretaceous armored herbivore that browsed the floodplains, css: body{font-family:system-ui,sans-serif;" +
    "background:#101010} and more of the same for a while longer, " * 2

  private def shown[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    (1 to 6).foreach(_ => fx(()))
    try test finally fx {
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  private def line(v: ActivityView): Container = box(v).children().head.asInstanceOf[Container]
  private def summary(v: ActivityView): TextView = line(v).children()(1).asInstanceOf[TextView]
  private def oneLine(): Double = {
    val t = fx(new TextView {
      content @= "X"
      font.size @= 13.0
    })
    val h = fx(t.size.height())
    fx(t.dispose())
    h
  }

  "A tool call's card" should {
    for (width <- List(1000.0, 390.0)) {
      s"be as wide as it may be with a summary too long for a line, wrapped in as few lines as it needs ($width)" in {
        val v = fx(new ActivityView(ChatEntry.activity("long", long, ActivityStatus.Succeeded), width, () => None))
        shown(v) {
          val widest = Transcript.widest(width)
          fx(box(v).size.width()) shouldBe widest +- 1.0
          val s = summary(v)
          fx(s.elided()) shouldBe false
          fx(s.size.width()) should be > widest * 0.6
          val lines = fx(s.size.height()) / oneLine()
          // its words wrapped at the room, not a word to a line
          val whole = fx(new TextView {
            content @= long
            font.size @= 13.0
            textOverflow @= everyui.component.types.TextOverflow.Visible
          })
          val wanted = math.ceil(fx(whole.size.width()) / fx(s.size.width()))
          fx(whole.dispose())
          lines should be <= wanted + 1.5
          // no room left under them
          fx(box(v).size.height()) should be <= fx(s.size.height()) + 30.0
        }
      }
    }
    "be a pill as wide as its summary where it fits" in {
      val v = fx(new ActivityView(ChatEntry.activity("short", "Ran the tests", ActivityStatus.Succeeded), 1000.0, () => None))
      shown(v) {
        fx(box(v).size.width()) should be < 300.0
        fx(summary(v).size.height()) shouldBe oneLine() +- 0.5
      }
    }
  }
}

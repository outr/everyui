package everyui.list

import everyui.focus.Focus
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import reactify.*

/** A rich list row's meter (`RichLook.meter`): how full, held to 0..1, its colour at its thresholds, its words at its
  * end never cut and said with the row, and a progress bar to a screen reader; a section's header without its count
  * (`RichSection.showCount`); and its subtitle beside its title where there is room and under it where there is not,
  * never cut. */
class RichMeterSpec extends FxSpec {
  case class Quota(id: String, title: String, meter: Option[RichMeter] = None)

  private def look(q: Quota): RichLook = RichLook(q.title, meter = q.meter)

  private def row(list: RichList[Quota], index: Int): RichRow[Quota] =
    list.list.rowAt(index).get.asInstanceOf[RichRow[Quota]]

  private def onScreen[T](list: RichList[?], width: Double)(test: => T): T = {
    fx {
      ui.children.clear()
      list.size.width @= width
      ui.children += list
    }
    try test finally fx {
      ui.children.clear()
      list.dispose()
      Focus.focused @= None
    }
  }

  private def listOf(sections: RichSection[Quota]*): RichList[Quota] =
    fx(new RichList[Quota](Var(sections.toVector), _.id, look))

  "A meter" should {
    "be held to 0..1, and turn from the accent to the warning colour and the danger colour at its thresholds" in {
      RichMeter(1.4).filled shouldBe 1.0
      RichMeter(-0.2).filled shouldBe 0.0
      RichMeter(Double.NaN).filled shouldBe 0.0
      fx(RichMeter(0.5).tone) shouldBe fx(Theme.accent())
      fx(RichMeter(0.75).tone) shouldBe fx(Theme.warning())
      fx(RichMeter(0.89).tone) shouldBe fx(Theme.warning())
      fx(RichMeter(0.9).tone) shouldBe fx(Theme.danger())
      fx(RichMeter(1.4).tone) shouldBe fx(Theme.danger())
      fx(RichMeter(0.6, warnAt = 0.5, dangerAt = 0.6).tone) shouldBe fx(Theme.danger())
      fx(RichMeter(0.55, warnAt = 0.5, dangerAt = 0.6).tone) shouldBe fx(Theme.warning())
      RichMeter(0.72, "7.2 / 10 GB").said shouldBe "7.2 / 10 GB"
      RichMeter(0.724).said shouldBe "72%"
    }
    "draw its bar in the room its words leave, its words whole at the end, and be said with the row" in {
      val list = listOf(RichSection("plan", "Plan", Vector(Quota("disk", "Storage", Some(RichMeter(0.92,
        "9.2 / 10 GB"))))))
      onScreen(list, 360.0) {
        eventually(list.list.rowAt(1).nonEmpty) shouldBe true
        val r = row(list, 1)
        eventually(r.meterBar.visible() && r.meterWords.size.width() > 0.0) shouldBe true
        fx(r.meterBar.fraction()) shouldBe 0.92
        fx(r.meterBar.fill()) shouldBe fx(Theme.danger())
        fx(r.meterWords.content()) shouldBe "9.2 / 10 GB"
        fx(r.meterWords.elided()) shouldBe false
        // the bar, then its words, inside the row, on the line under the title
        val barEnd = fx(r.meterBar.position.left() + r.meterBar.size.width())
        barEnd should be <= fx(r.meterWords.position.left()) - RichRow.meterWordsGap + 0.5
        fx(r.meterWords.position.left() + r.meterWords.size.width()) should be <= 360.0
        fx(r.meterBar.position.top()) should be > fx(r.title.position.top() + r.title.size.height() - 0.5)
        fx(r.meterWords.position.top() + r.meterWords.size.height()) should be <= fx(r.size.height())
        fx(r.size.height()) should be > RichRow.shortHeight
        fx(r.meterBar.semantics().role) shouldBe "progressbar"
        fx(r.meterBar.semantics().range.map(_.text)) shouldBe Some("9.2 / 10 GB")
        fx(r.meterBar.semantics().range.map(_.now)) shouldBe Some(92.0)
        fx(r.semantics().label) shouldBe "Storage, 9.2 / 10 GB"
      }
    }
    "put its words under the bar where they leave it too little room, never cut" in {
      val list = listOf(RichSection("plan", "Plan", Vector(Quota("disk", "Storage", Some(RichMeter(0.4,
        "4.0 of 10.0 GB used this month"))))))
      onScreen(list, 150.0) {
        eventually(list.list.rowAt(1).nonEmpty) shouldBe true
        val r = row(list, 1)
        eventually(r.meterWords.size.width() > 0.0) shouldBe true
        fx(r.meterWords.elided()) shouldBe false
        fx(r.meterBar.size.width()) should be >= RichRow.meterLeast
        fx(r.meterWords.position.top()) should be >= fx(r.meterBar.position.top() + RichRow.meterThick)
        fx(r.meterWords.position.left() + r.meterWords.size.width()) should be <= 150.0
        fx(r.meterWords.position.top() + r.meterWords.size.height()) should be <= fx(r.size.height())
        fx(r.meterBar.fill()) shouldBe fx(Theme.accent())
      }
    }
  }

  "A section's header" should {
    "leave out its count where the section says, and say none" in {
      val list = listOf(RichSection("one", "Billing", Vector(Quota("a", "Card")), showCount = false),
        RichSection("two", "Members", Vector(Quota("b", "Ada"))))
      onScreen(list, 360.0) {
        eventually(list.list.rowAt(2).nonEmpty) shouldBe true
        fx(row(list, 0).heading.badge.visible()) shouldBe false
        fx(row(list, 0).semantics().label) shouldBe "Billing"
        fx(row(list, 2).heading.badge.visible()) shouldBe true
        fx(row(list, 2).semantics().label) shouldBe "Members, 1"
      }
    }
    "leave out its count in a block too" in {
      val list = listOf(RichSection("one", "Billing", Vector(Quota("a", "Card")), showCount = false))
      fx(list.style @= RichStyle.Blocks)
      onScreen(list, 360.0) {
        eventually(list.list.rowAt(0).nonEmpty) shouldBe true
        fx(row(list, 0).count.visible()) shouldBe false
      }
    }
    "show its subtitle beside its title where there is room, and under it, wrapped, where there is not" in {
      val wide = listOf(RichSection("seats", "Seats", Vector(Quota("a", "Ada")), subtitle = Some("3 of 5 used")))
      onScreen(wide, 400.0) {
        eventually(wide.list.rowAt(0).nonEmpty) shouldBe true
        val h = row(wide, 0)
        eventually(h.subtitleText.size.width() > 0.0) shouldBe true
        fx(h.subtitleText.elided()) shouldBe false
        fx(h.size.height()) shouldBe RichRow.headerHeight
        // on the title's line, after its words
        val titleEnd = fx(h.heading.position.left() + h.heading.textStart + h.heading.label.size.width())
        fx(h.subtitleText.position.left()) should be >= titleEnd
        fx(h.subtitleText.position.top() + h.subtitleText.size.height()) should be <= RichRow.headerHeight
        fx(h.semantics().label) shouldBe "Seats, 3 of 5 used, 1"
      }
      val narrow = listOf(RichSection("seats", "Seats", Vector(Quota("a", "Ada")),
        subtitle = Some("3 of 5 seats used, renewing on the first of next month")))
      onScreen(narrow, 160.0) {
        eventually(narrow.list.rowAt(0).nonEmpty) shouldBe true
        val h = row(narrow, 0)
        eventually(h.subtitleText.size.width() > 0.0 && h.size.height() > RichRow.headerHeight) shouldBe true
        fx(h.subtitleText.elided()) shouldBe false
        // under the title, from where its words start, inside the row
        val under = fx(h.heading.label.position.top() + h.heading.label.size.height())
        fx(h.subtitleText.position.top()) should be >= under
        fx(h.subtitleText.position.left()) shouldBe fx(h.heading.position.left() + h.heading.textStart)
        fx(h.subtitleText.position.left() + h.subtitleText.size.width()) should be <= 160.0
        fx(h.subtitleText.position.top() + h.subtitleText.size.height()) should be <= fx(h.size.height())
      }
    }
  }
}

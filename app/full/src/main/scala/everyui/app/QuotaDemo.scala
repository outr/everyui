package everyui.app

import everyui.font.MaterialIcon
import everyui.list.{RichList, RichLook, RichMeter, RichSection, RichStyle}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** An admin console's usage, as a `RichList`: each quota a row with a meter (`RichLook.meter`) whose words never cut
  * and whose colour turns at three quarters and nine tenths, under a section whose subtitle sits beside its title
  * where there is room and under it where not (`RichSection.subtitle`), and a one-line section with no count
  * (`showCount`). `width=<pixels>` sets the list's width (the window's, less a margin, by default), `theme=light` and
  * `style=blocks` as elsewhere. */
object QuotaDemo {
  private case class Quota(id: String, title: String, detail: Option[String], meter: Option[RichMeter],
                           icon: MaterialIcon)

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val usage = RichSection("usage", "Usage", Vector(
      Quota("disk", "Storage", None, Some(RichMeter(0.42, "4.2 / 10 GB")), MaterialIcon.Storage),
      Quota("seats", "Seats", Some("Team plan"), Some(RichMeter(0.8, "8 of 10")), MaterialIcon.Group),
      Quota("calls", "API calls", None, Some(RichMeter(0.96, "96,120 / 100,000 this month")), MaterialIcon.Api)),
      subtitle = Some("Renews on the 1st, 12 days from now"), showCount = false)
    val billing = RichSection("billing", "Billing", Vector(
      Quota("card", "Visa ending 4242", Some("Expires 08/28"), None, MaterialIcon.CreditCard)), showCount = false)
    val members = RichSection("members", "Members", Vector(
      Quota("ada", "Ada Lovelace", Some("Owner"), None, MaterialIcon.Person),
      Quota("grace", "Grace Hopper", Some("Admin"), None, MaterialIcon.Person)))
    val list = new RichList[Quota](Var(Vector(usage, billing, members)), _.id,
      q => RichLook(q.title, q.detail, icon = Some(q.icon), meter = q.meter))
    if (Platform.launchArg("style") == "blocks") list.style @= RichStyle.Blocks
    val asked = Option(Platform.launchArg("width")).filter(_.nonEmpty).map(_.toDouble)
    list.size.width := asked.getOrElse(math.max(120.0, ui.size.width() - 32.0))
    list.size.height := ui.size.height() - 32.0
    list.position.left @= 16.0
    list.position.top @= 16.0
    ui.children += list
    Platform.log("quota ok")
  }
}

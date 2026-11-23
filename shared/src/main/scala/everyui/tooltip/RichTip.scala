package everyui.tooltip

import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.layout.VerticalLayout
import everyui.menu.{MenuPicture, MenuPictureView}
import everyui.theme.Theme
import reactify.*

/** A tip laid out as a card: a picture (a logo, a letter on its colour) beside its `title`, then `lines` (an icon and
  * words each), `meters` (a label, words and a filled bar each), any component of the app's (`extra`: a chart from
  * `everyui.chart`, a preview) and a `footer` in small words, each part left out where it is not given. It is `width`
  * across, no wider than `Tooltip.maxWidth`, its words wrapping within it; a part wider than that is cut to it. For a
  * component's `tipView`:
  * {{{
  *   tile.tipView @= Some(_ => RichTip("Sigil", Some(MenuPicture.Letter("S", Color.fromHex("#7c4dff"))),
  *     lines = List(RichTipLine(MaterialIcon.Mail, "5 unread"), RichTipLine(MaterialIcon.Autorenew, "working")),
  *     meters = List(RichTipMeter("CPU", 0.68, words = "21.8 cores"), RichTipMeter("Memory", 0.47, words = "14.9 GB"))))
  * }}} */
class RichTip(title: String, picture: Option[MenuPicture] = None, lines: List[RichTipLine] = Nil,
              meters: List[RichTipMeter] = Nil, footer: Option[String] = None, extra: Option[Component] = None,
              width: Double = 280.0) extends Container {
  override def kind: String = "RichTip"

  private val across = math.min(width, Tooltip.maxWidth())

  size.width @= across
  layout @= Some(VerticalLayout(8.0))

  // words `across` wide at most, wrapping
  private def words(text: String, points: Double, colour: => everyui.Color, room: Double): TextView = {
    val view = TextView(text, points, colour)
    if (view.size.width() > room) {
      view.whiteSpace @= WhiteSpace.Normal
      view.size.width @= room
    }
    view
  }

  // a row of `lead` at the start and `rest` after it, the two centred on each other, in the reading direction
  private def row(lead: Component, rest: Component, gap: Double): Container = {
    val box = new Container
    box.size.width @= across
    box.size.height := math.max(lead.size.height(), rest.size.height())
    val rtl = Direction.rtl()
    lead.position.left := (if (rtl) across - lead.size.width() else 0.0)
    rest.position.left := (if (rtl) across - lead.size.width() - gap - rest.size.width() else lead.size.width() + gap)
    lead.position.middle := box.size.height() / 2.0
    rest.position.middle := box.size.height() / 2.0
    box.children ++= List(lead, rest)
    box
  }

  private val heading = {
    val name = words(title, 14.0, Theme.text(), across - picture.fold(0.0)(_ => 36.0))
    name.font.weight @= FontWeight.SemiBold
    picture match {
      case Some(p) => row(new MenuPictureView(p, 28.0, MaterialIcon.Image, Theme.muted()), name, 8.0)
      case None => name
    }
  }

  private def line(l: RichTipLine): Container = {
    val icon = new MaterialIconView(l.icon, 15.0)
    icon.color := l.tone.fold(Theme.muted())(_.text())
    row(icon, words(l.text, 12.5, l.tone.fold(Theme.text())(_.text()), across - 21.0), 6.0)
  }

  private def meter(m: RichTipMeter): Container = {
    val box = new Container
    box.size.width @= across
    val label = TextView(m.label, 12.0, Theme.muted())
    val amount = TextView(m.words, 12.0, Theme.text())
    val rtl = Direction.rtl()
    label.position.left := (if (rtl) across - label.size.width() else 0.0)
    amount.position.left := (if (rtl) 0.0 else across - amount.size.width())
    val track = new Container {
      size.width @= across
      size.height @= 6.0
      radius @= 3.0
      backgroundColor := Theme.border()
    }
    val fill = new Container {
      size.width @= across * math.max(0.0, math.min(1.0, m.fraction))
      size.height @= 6.0
      radius @= 3.0
      backgroundColor := m.tone.colour()
    }
    if (rtl) fill.position.left @= across - fill.size.width()
    track.children += fill
    track.position.top := math.max(label.size.height(), amount.size.height()) + 4.0
    box.size.height := track.position.top() + 6.0
    box.children ++= List(label, amount, track)
    box
  }

  children ++= (List(heading) ++ lines.map(line) ++ meters.map(meter) ++ extra.toList ++
    footer.map(f => words(f, 11.5, Theme.muted(), across)).toList)
}

package everyui.chat

import everyui.Color
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.font.MaterialIcon
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** The way back down a `Transcript` scrolled up: a small round button with a down arrow, which the transcript keeps in
  * its bottom end corner, clear of the middle where the newest entry's buttons are. While entries have arrived below,
  * a small badge on its top end corner says how many. It is read out, and named by its tooltip, as "Jump to latest",
  * with how many are new after it ("Jump to latest, 3 new"). */
class JumpButton(unseen: Val[Int], press: () => Unit) extends Container {
  /** What it is read out as, and its tooltip. */
  val label: Val[String] = own(Val(if (unseen() > 0) ChatWords.jumpToLatest + ", " + ChatWords.newCount(unseen())
    else ChatWords.jumpToLatest))

  private val arrow: MaterialIconView = new MaterialIconView(MaterialIcon.ArrowDownward, JumpButton.icon)
  arrow.position.left @= (JumpButton.diameter - JumpButton.icon) / 2.0
  arrow.position.top @= (JumpButton.diameter - JumpButton.icon) / 2.0
  arrow.color := (if (focusShown() || event.pointer.over()) Theme.accent() else Theme.text())

  private val count: TextView = new TextView {
    content := (if (unseen() > 99) "99+" else unseen().toString)
    font.size @= 10.5
    font.weight @= FontWeight.SemiBold
    color := Theme.onAccent()
  }
  /** How many have arrived below, on the corner: shown while there are any. */
  val badge: Container = new Container {
    size.width := math.max(JumpButton.badge, count.size.width() + 8.0)
    size.height @= JumpButton.badge
    radius @= JumpButton.badge / 2.0
    backgroundColor := Theme.accent()
    visible := unseen() > 0
    children += count
  }
  count.position.left := (badge.size.width() - count.size.width()) / 2.0
  count.position.top := (JumpButton.badge - count.size.height()) / 2.0
  // over the corner, a little out past it, mirrored right to left
  badge.position.left := ListRow.along(JumpButton.diameter - badge.size.width() + 4.0, badge.size.width(),
    JumpButton.diameter)
  badge.position.top @= -4.0
  clipsToBox @= Some(false)

  size.width @= JumpButton.diameter
  size.height @= JumpButton.diameter
  radius @= JumpButton.diameter / 2.0
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Theme.raised())
  borderWidth @= 1.0
  borderColor := Theme.border()
  shadow @= Some(everyui.Shadow(0.0, 2.0, 8.0, Color.Black.withAlpha(0.3)))
  focusable @= true
  cursor @= "pointer"
  tooltip := label()
  semantics := Semantics.button(label())
  tapSlop @= true
  children ++= List(arrow, badge)
  event.click.attach { e =>
    e.consume()
    press()
  }

  override def kind: String = "JumpButton"
}

object JumpButton {
  /** How big it is across, its arrow, and how tall the badge on its corner is. */
  val diameter: Double = 40.0
  val icon: Double = 20.0
  val badge: Double = 18.0
  /** How far it sits in from the transcript's end edge and its bottom. */
  val margin: Double = 12.0
}

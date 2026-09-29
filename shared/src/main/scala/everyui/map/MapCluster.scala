package everyui.map

import everyui.Color
import everyui.component.{Component, Container, TextView}
import everyui.component.types.FontWeight
import everyui.theme.Theme

/** How a map gathers markers that crowd each other (`MapView.cluster`): those that fall in one square of `cell`
  * pixels (squares fixed to the world, so a pan leaves them as they are) are drawn as one, made by `make` from how
  * many there are, once there are `least` of them, below zoom `until`. A click on one glides in to its markers where
  * `zoomsIn`, and `MapView.clicked` hears it as a `MapFeature.ClusterFeature` either way.
  *
  * {{{
  *   map.cluster @= Some(MapCluster())
  *   map.cluster @= Some(MapCluster(cell = 80.0, until = 14.0, make = n => new CountBadge(n)))
  * }}} */
case class MapCluster(cell: Double = 64.0, until: Double = 17.0, least: Int = 2,
                      make: Int => Component = MapCluster.bubble, zoomsIn: Boolean = true)

object MapCluster {
  /** A round badge in the accent with the count in it, larger as the count has more digits. */
  def bubble(count: Int): Component = {
    val words = if (count >= 1000) (count / 1000).toString + "k" else count.toString
    val side = if (count < 10) 28.0 else if (count < 100) 34.0 else 40.0
    val text = TextView(words, 12.0, Theme.onAccent())
    text.font.weight @= FontWeight.SemiBold
    text.position.left := (side - text.size.width()) / 2.0
    text.position.top := (side - text.size.height()) / 2.0
    new Container {
      size.width @= side
      size.height @= side
      radius @= side / 2.0
      backgroundColor := Theme.accent()
      borderWidth @= 2.0
      borderColor @= Color.White.withAlpha(0.85)
      children += text
    }
  }
}

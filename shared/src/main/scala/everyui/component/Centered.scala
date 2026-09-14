package everyui.component

/** `child` centred on a line `width` wide, as tall as it is. */
object Centered {
  def apply(width: Double)(child: Component): Container = {
    val line = new Container
    line.size.width @= width
    line.size.height := child.size.height()
    child.position.left := (width - child.size.width()) / 2.0
    line.children += child
    line
  }
}

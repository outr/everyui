package everyui.color

import everyui.Color
import everyui.component.Container

/** The checks drawn behind a colour that can be see-through, so how much shows through can be seen: squares of
  * `cell` pixels, two greys, over a box `width` by `height`. */
class ColorChecker(width: Double, height: Double, cell: Double = 6.0) extends Container {
  size.width @= width
  size.height @= height
  backgroundColor @= ColorChecker.light
  squares()

  private def squares(): Unit = {
    val across = math.ceil(width / cell).toInt
    val down = math.ceil(height / cell).toInt
    var row = 0
    while (row < down) {
      var column = row % 2
      while (column < across) {
        children += square(column, row)
        column += 2
      }
      row += 1
    }
  }

  private def square(column: Int, row: Int): Container = {
    val box = new Container
    box.position.left @= column * cell
    box.position.top @= row * cell
    box.size.width @= math.min(cell, width - column * cell)
    box.size.height @= math.min(cell, height - row * cell)
    box.backgroundColor @= ColorChecker.dark
    box
  }

  override def kind: String = "ColorChecker"
}

object ColorChecker {
  val light: Color = Color.fromHex("ffffff")
  val dark: Color = Color.fromHex("cccccc")
}

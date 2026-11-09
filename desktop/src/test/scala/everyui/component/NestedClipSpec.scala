package everyui.component

import everyui.font.MaterialIcon
import everyui.render.Host
import everyui.{Color, FxSpec, ui}
import javafx.scene.image.WritableImage

/** A container clips only where it draws a box, and at its own edge: what hangs out of a plain column in a rail that
  * draws one shows as far as the rail's edge, and is cut there, as an icon button's badge hangs past the column it is
  * in; a column that comes to draw a box later clips at its own edge from then on. */
class NestedClipSpec extends FxSpec {
  private val red = Color.fromHex("ff0000")

  /** A rail at 100,100, 100 by 200, drawing a box, holding a plain column at 20,20, 40 by 160, holding a red bar 70
    * wide (past the column's edge, within the rail) and one 150 wide (past the rail's). */
  private def rail(): (Container, Container) = fx {
    ui.children.clear()
    val r = new Container
    r.position.left @= 100.0
    r.position.top @= 100.0
    r.size.width @= 100.0
    r.size.height @= 200.0
    r.backgroundColor @= Color.fromHex("ffffff")
    val column = new Container
    column.position.left @= 20.0
    column.position.top @= 20.0
    column.size.width @= 40.0
    column.size.height @= 160.0
    def bar(width: Double, top: Double): Container = {
      val b = new Container
      b.size.width @= width
      b.size.height @= 20.0
      b.position.top @= top
      b.backgroundColor @= red
      b
    }
    column.children ++= List(bar(70.0, 10.0), bar(150.0, 50.0))
    r.children += column
    ui.children += r
    (r, column)
  }

  private def at(x: Int, y: Int): Int = fx {
    val image = Host.scene.snapshot(new WritableImage(Host.scene.getWidth.toInt, Host.scene.getHeight.toInt))
    image.getPixelReader.getArgb(x, y)
  }
  private def isRed(argb: Int): Boolean = ((argb >> 16) & 0xff) > 200 && ((argb >> 8) & 0xff) < 60

  "A plain column in a rail that clips" should {
    "let what it holds hang past its edge, cut only at the rail's" in {
      val (r, column) = rail()
      fx((r.clips, column.clips)) shouldBe (true, false)
      // the first bar, past the column's right edge (160) and within the rail's (200)
      isRed(at(140, 140)) shouldBe true
      isRed(at(180, 140)) shouldBe true
      // the second, cut at the rail's edge
      isRed(at(195, 180)) shouldBe true
      isRed(at(220, 180)) shouldBe false
    }
    "clip at its own edge once it draws a box of its own" in {
      val (_, column) = rail()
      isRed(at(180, 140)) shouldBe true
      fx(column.backgroundColor @= Color.fromHex("eeeeee"))
      fx(column.clips) shouldBe true
      isRed(at(150, 140)) shouldBe true
      isRed(at(180, 140)) shouldBe false
    }
    "leave an icon button's badge hanging past it, and the button and the column unclipped" in {
      val (r, column, b) = fx {
        ui.children.clear()
        val r = new Container
        r.position.left @= 100.0
        r.position.top @= 100.0
        r.size.width @= 80.0
        r.size.height @= 120.0
        r.backgroundColor @= Color.fromHex("ffffff")
        val b = new IconButton(MaterialIcon.Queue, "Queue")
        b.badge @= Some("3")
        b.badgeColour @= red
        val column = Column(4.0)(b)
        column.position.left @= 8.0
        column.position.top @= 8.0
        r.children += column
        ui.children += r
        (r, column, b)
      }
      fx((r.clips, column.clips, b.clips)) shouldBe (true, false, false)
      // the pill, 16 wide, from 22 along the button: past the column's edge (32 along it), within the rail
      val right = fx(column.absoluteLeft + column.size.width()).toInt
      fx(column.size.width()) shouldBe 32.0
      isRed(at(right + 4, fx(b.absoluteTop).toInt + 4)) shouldBe true
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}

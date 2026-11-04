package everyui.capture

import everyui.component.{Container, TextView}
import everyui.draw.{Drawing, Figure, Path}
import everyui.render.Host
import everyui.{Color, FxSpec, ui}
import javafx.scene.image.WritableImage

import java.io.ByteArrayInputStream

/** Pictures the desktop takes of itself: a component's PNG against the pixels the scene draws where it is, at a scale,
  * over the colour behind it; the window at its size; a drawing's figures straight to a PNG; and each PNG read back as
  * a file. */
class SnapshotSpec extends FxSpec {
  /** What a Task answered, once it has. */
  private def taken(start: (Option[SnapshotImage] => Unit) => Unit): Option[SnapshotImage] = {
    var result: Option[Option[SnapshotImage]] = None
    fx(start(r => result = Some(r)))
    eventually(result.nonEmpty) shouldBe true
    result.get
  }

  /** The PNG read back (by ImageIO, which the backend never uses), as ARGB rows. */
  private def pixels(image: SnapshotImage): Array[Int] = {
    val read = javax.imageio.ImageIO.read(new ByteArrayInputStream(image.png))
    read.getWidth shouldBe image.width
    read.getHeight shouldBe image.height
    read.getRGB(0, 0, image.width, image.height, null, 0, image.width)
  }

  /** The largest difference of a channel between two ARGB pixels. */
  private def apart(a: Int, b: Int): Int =
    List(16, 8, 0).map(s => math.abs(((a >> s) & 0xff) - ((b >> s) & 0xff))).max

  private def rgb(c: Color): Int = 0xff000000 | (c.redInt << 16) | (c.greenInt << 8) | c.blueInt

  "Snapshots" should {
    "be supported on the desktop" in {
      Snapshot.supported shouldBe true
      Snapshot.drawable shouldBe true
    }

    "take a component as the scene draws it, at a scale" in {
      val (panel, words) = fx {
        val words = TextView("Snapshot", 16.0)
        val panel = new Container {
          backgroundColor @= Color.fromHex("336699")
          size.width @= 160.0
          size.height @= 60.0
          children += words
        }
        words.position.left @= 12.0
        words.position.top @= 18.0
        panel.position.left @= 40.0
        panel.position.top @= 30.0
        ui.children += panel
        (panel, words)
      }
      try {
        val one = taken(done => Snapshot.component(panel).map(done).start()).get
        (one.width, one.height) shouldBe (160, 60)
        one.mime shouldBe "image/png"
        val shot = pixels(one)
        // the scene's own pixels where the panel is
        val scene = fx {
          val image = Host.scene.snapshot(new WritableImage(Host.scene.getWidth.toInt, Host.scene.getHeight.toInt))
          val reader = image.getPixelReader
          Array.tabulate(60 * 160)(i => reader.getArgb(40 + i % 160, 30 + i / 160))
        }
        val far = shot.indices.count(i => apart(shot(i), scene(i)) > 8)
        far shouldBe 0
        // the words are in it: pixels other than the ground
        shot.count(p => apart(p, rgb(Color.fromHex("336699"))) > 32) should be > 20

        val two = taken(done => Snapshot.component(panel, 2.0).map(done).start()).get
        (two.width, two.height) shouldBe (320, 120)
        val big = pixels(two)
        apart(big(5 * 320 + 5), rgb(Color.fromHex("336699"))) should be <= 2
      }
      finally fx(ui.children.clear())
    }

    "draw a component over the colour behind it" in {
      val (ground, dot) = fx {
        val dot = new Container {
          backgroundColor @= Color.fromHex("ff0000")
          radius @= 20.0
          size.width @= 40.0
          size.height @= 40.0
        }
        val ground = new Container {
          backgroundColor @= Color.fromHex("00ff00")
          size.width @= 100.0
          size.height @= 100.0
          children += dot
        }
        dot.position.left @= 30.0
        dot.position.top @= 30.0
        ui.children += ground
        (ground, dot)
      }
      try {
        val shot = pixels(taken(done => Snapshot.component(dot).map(done).start()).get)
        // a rounded corner shows the green round it, the middle the red
        apart(shot(0), rgb(Color.fromHex("00ff00"))) should be <= 2
        apart(shot(20 * 40 + 20), rgb(Color.fromHex("ff0000"))) should be <= 2
      }
      finally fx(ui.children.clear())
    }

    "take the window at the scene's size" in {
      val image = taken(done => Snapshot.window(0.5).map(done).start()).get
      (image.width, image.height) shouldBe ((Host.scene.getWidth / 2).toInt, (Host.scene.getHeight / 2).toInt)
      pixels(image).length shouldBe image.width * image.height
    }

    "answer None for a component of no size" in {
      val empty = fx(new Container)
      taken(done => Snapshot.component(empty).map(done).start()) shouldBe None
    }

    "draw a drawing's figures straight into a PNG, at a scale" in {
      val d = fx {
        val d = new Drawing
        d.size.width @= 40.0
        d.size.height @= 20.0
        d.figures @= List(Figure(Path.rect(0, 0, 20, 20), fill = Some(Color.fromHex("0000ff"))),
          Figure(Path.line(20, 10, 40, 10), stroke = Some(Color.fromHex("ff0000")), width = 2.0))
        d
      }
      val image = taken(done => Snapshot.drawing(d, 3.0, Some(Color.White)).map(done).start()).get
      (image.width, image.height) shouldBe (120, 60)
      val shot = pixels(image)
      apart(shot(30 * 120 + 30), rgb(Color.fromHex("0000ff"))) should be <= 2
      apart(shot(30 * 120 + 90), rgb(Color.fromHex("ff0000"))) should be <= 2
      apart(shot(5 * 120 + 90), rgb(Color.White)) should be <= 2
      // clear where no ground is asked for
      val clear = taken(done => Snapshot.figures(d.figures(), 40.0, 20.0).map(done).start()).get
      (pixels(clear)(5 * 40 + 35) >>> 24) shouldBe 0
    }

    "fit a picture's bytes into a PNG of a size, in proportion, centred and clear round it" in {
      // a PNG 40 by 20 of solid blue, made by ImageIO, which the backend never uses
      val blue = new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      val g = blue.createGraphics()
      g.setColor(new java.awt.Color(0, 0, 255))
      g.fillRect(0, 0, 40, 20)
      g.dispose()
      val out = new java.io.ByteArrayOutputStream()
      javax.imageio.ImageIO.write(blue, "png", out)
      val image = taken(done => Snapshot.fitted(out.toByteArray, "image/png", 64, 64).map(done).start()).get
      (image.width, image.height) shouldBe (64, 64)
      val shot = pixels(image)
      // 64 by 32, from 16 down
      apart(shot(32 * 64 + 32), rgb(Color.fromHex("0000ff"))) should be <= 2
      (shot(17 * 64 + 2) >>> 24) should be > 200
      (shot(8 * 64 + 32) >>> 24) shouldBe 0
      (shot(56 * 64 + 32) >>> 24) shouldBe 0
    }

    "fit nothing it cannot read: an SVG on the desktop, or no bytes" in {
      val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">" +
        "<rect width=\"10\" height=\"10\"/></svg>"
      taken(done => Snapshot.fitted(svg.getBytes("UTF-8"), "image/svg+xml", 64, 64).map(done).start()) shouldBe None
      taken(done => Snapshot.fitted(Array.emptyByteArray, "image/png", 64, 64).map(done).start()) shouldBe None
    }
  }
}

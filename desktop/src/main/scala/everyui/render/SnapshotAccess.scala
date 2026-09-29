package everyui.render

import everyui.capture.SnapshotImage
import everyui.draw.Figure
import javafx.geometry.Rectangle2D
import javafx.scene.{Node, SnapshotParameters}
import javafx.scene.image.{PixelFormat, WritableImage}
import javafx.scene.layout.{Pane, Region}
import javafx.scene.paint.{Color, Paint}
import javafx.scene.transform.Scale

/** Pictures of the app (`everyui.capture.Snapshot`): JavaFX draws a node, or the scene's root, into an image
  * (`Node.snapshot`) at the scale asked for, over the colour behind it; the image is written as a PNG on a thread of its
  * own (by hand, as `--screenshot` writes one, with no AWT) and answered on the FX thread. */
object SnapshotAccess {
  def supported(): Boolean = true

  def draws(): Boolean = true

  /** The node's own box, from its parent's point of view, over the nearest background round it. */
  def node(node: RenderNode, scale: Double, done: Option[SnapshotImage] => Unit): Unit = onFx { () =>
    val fx = node.fx
    val width = node.getWidth()
    val height = node.getHeight()
    if (width * scale < 0.5 || height * scale < 0.5) done(None)
    else {
      val at = fx.localToParent(0.0, 0.0)
      taken(fx, new Rectangle2D(at.getX * scale, at.getY * scale, width * scale, height * scale), scale, behind(fx), done)
    }
  }

  /** The scene's root at the scene's size, over its fill. */
  def window(scale: Double, done: Option[SnapshotImage] => Unit): Unit = onFx { () =>
    val scene = Host.scene
    if (scene == null || scene.getWidth * scale < 0.5 || scene.getHeight * scale < 0.5) done(None)
    else taken(scene.getRoot, new Rectangle2D(0.0, 0.0, scene.getWidth * scale, scene.getHeight * scale), scale,
      Option(scene.getFill).getOrElse(Color.WHITE), done)
  }

  /** The figures as a drawing draws them (an SVGPath each, rounded ends and joins), in a pane of their size that is in
    * no scene. */
  def figures(figures: List[Figure], width: Double, height: Double, scale: Double, background: Option[everyui.Color],
              done: Option[SnapshotImage] => Unit): Unit = onFx { () =>
    val pane = new Pane()
    pane.resize(width, height)
    val drawing = new RenderNode(pane)
    drawing.setFigures(figures)
    taken(pane, new Rectangle2D(0.0, 0.0, width * scale, height * scale), scale,
      background.map(RenderNode.paintOf).getOrElse(Color.TRANSPARENT), done)
  }

  /** A file of the app's own, gone when it quits. */
  def preview(png: Array[Byte]): String = {
    val file = java.nio.file.Files.createTempFile("everyui-snapshot", ".png")
    file.toFile.deleteOnExit()
    java.nio.file.Files.write(file, png)
    file.toUri.toString
  }

  private def taken(fx: Node, viewport: Rectangle2D, scale: Double, fill: Paint, done: Option[SnapshotImage] => Unit): Unit = {
    val params = new SnapshotParameters()
    params.setFill(fill)
    params.setTransform(new Scale(scale, scale))
    params.setViewport(viewport)
    val width = math.max(1, math.round(viewport.getWidth).toInt)
    val height = math.max(1, math.round(viewport.getHeight).toInt)
    val image = fx.snapshot(params, new WritableImage(width, height))
    val argb = new Array[Int](width * height)
    image.getPixelReader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance, argb, 0, width)
    Platform.offThread(() => {
      val png = encoded(width, height, argb)
      Platform.later(() => done(Some(SnapshotImage(png, width, height))))
    })
  }

  /** ARGB pixels as a PNG: each row unfiltered, RGBA. */
  def encoded(width: Int, height: Int, argb: Array[Int]): Array[Byte] = {
    val rows = new Array[Byte](height * (width * 4 + 1))
    var i = 0
    var y = 0
    while (y < height) {
      rows(i) = 0
      i += 1
      var x = 0
      while (x < width) {
        val p = argb(y * width + x)
        rows(i) = (p >> 16).toByte
        rows(i + 1) = (p >> 8).toByte
        rows(i + 2) = p.toByte
        rows(i + 3) = (p >>> 24).toByte
        i += 4
        x += 1
      }
      y += 1
    }
    Png.encode(width, height, rows)
  }

  /** The first solid colour a region round `fx` is filled with, else the scene's fill (white without one). */
  private def behind(fx: Node): Paint = {
    var at: Node = fx.getParent
    var found: Option[Paint] = None
    while (at != null && found.isEmpty) {
      at match {
        case r: Region if r.getBackground != null =>
          found = r.getBackground.getFills.toArray.toList.collectFirst {
            case f: javafx.scene.layout.BackgroundFill if f.getFill.isInstanceOf[Color] && f.getFill.asInstanceOf[Color].getOpacity >= 1.0 =>
              f.getFill
          }
        case _ => ()
      }
      at = at.getParent
    }
    found.orElse(Option(fx.getScene).flatMap(s => Option(s.getFill))).getOrElse(Color.WHITE)
  }

  private def onFx(f: () => Unit): Unit =
    if (javafx.application.Platform.isFxApplicationThread) f() else javafx.application.Platform.runLater(() => f())
}

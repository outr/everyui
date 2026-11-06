package everyui.tray

import everyui.Color

/** A tray icon's picture: `width` by `height` pixels, each ARGB (alpha in the top byte), row by row. */
case class TrayImage(width: Int, height: Int, argb: Array[Int]) {
  /** The pixels as StatusNotifierItem wants them: four bytes each, A R G B, in that order. */
  def bytes: Array[Byte] = {
    val out = new Array[Byte](argb.length * 4)
    var i = 0
    while (i < argb.length) {
      val p = argb(i)
      out(i * 4) = (p >>> 24).toByte
      out(i * 4 + 1) = (p >>> 16).toByte
      out(i * 4 + 2) = (p >>> 8).toByte
      out(i * 4 + 3) = p.toByte
      i += 1
    }
    out
  }

  /** With a dot of `colour` at its lower corner (a status: connected, away), a ring of nothing round it so it stands
    * clear of the picture under it. */
  def badged(colour: Color): TrayImage = {
    val out = argb.clone()
    val d = math.max(4.0, width * 0.42)
    val cx = width - d / 2.0 - 0.5
    val cy = height - d / 2.0 - 0.5
    val ring = math.max(1.0, width / 16.0)
    val fill = TrayImage.packed(colour)
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        // how much of the pixel the dot and its ring cover, from 4 by 4 samples
        var dot = 0
        var clear = 0
        var sy = 0
        while (sy < 4) {
          var sx = 0
          while (sx < 4) {
            val dx = x + (sx + 0.5) / 4.0 - 0.5 - cx
            val dy = y + (sy + 0.5) / 4.0 - 0.5 - cy
            val r = math.sqrt(dx * dx + dy * dy)
            if (r <= d / 2.0) dot += 1 else if (r <= d / 2.0 + ring) clear += 1
            sx += 1
          }
          sy += 1
        }
        val i = y * width + x
        if (dot + clear > 0) {
          val under = TrayImage.fade(out(i), 1.0 - (dot + clear) / 16.0)
          out(i) = TrayImage.over(TrayImage.fade(fill, dot / 16.0), under)
        }
        x += 1
      }
      y += 1
    }
    TrayImage(width, height, out)
  }
}

object TrayImage {
  /** A PNG's pixels, decoded by JavaFX (no AWT, which a native image leaves out). */
  def png(bytes: Array[Byte]): TrayImage = {
    val image = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes))
    val w = image.getWidth.toInt
    val h = image.getHeight.toInt
    val px = new Array[Int](w * h)
    image.getPixelReader.getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance, px, 0, w)
    TrayImage(w, h, px)
  }

  /** A disc of `colour`, `size` across, its edge smoothed: a plain icon. */
  def disc(colour: Color, size: Int = 64): TrayImage = {
    val out = new Array[Int](size * size)
    val r = size / 2.0
    val fill = packed(colour)
    var i = 0
    while (i < out.length) {
      val x = i % size
      val y = i / size
      // how much of the pixel the disc covers, from 4 by 4 samples
      var inside = 0
      var s = 0
      while (s < 16) {
        val dx = x + ((s % 4) + 0.5) / 4.0 - r
        val dy = y + ((s / 4) + 0.5) / 4.0 - r
        if (dx * dx + dy * dy <= (r - 0.5) * (r - 0.5)) inside += 1
        s += 1
      }
      out(i) = fade(fill, inside / 16.0)
      i += 1
    }
    TrayImage(size, size, out)
  }

  private[tray] def packed(c: Color): Int = {
    def ch(v: Double): Int = math.max(0, math.min(255, math.round(v * 255.0).toInt))
    (ch(c.alpha) << 24) | (ch(c.red) << 16) | (ch(c.green) << 8) | ch(c.blue)
  }

  // `p` with its alpha scaled by `by`
  private[tray] def fade(p: Int, by: Double): Int = {
    val a = math.round(((p >>> 24) & 0xff) * math.max(0.0, math.min(1.0, by))).toInt
    (a << 24) | (p & 0xffffff)
  }

  // `top` drawn over `under`, both unpremultiplied
  private[tray] def over(top: Int, under: Int): Int = {
    val ta = ((top >>> 24) & 0xff) / 255.0
    val ua = ((under >>> 24) & 0xff) / 255.0
    val a = ta + ua * (1.0 - ta)
    if (a <= 0.0) 0
    else {
      def mix(shift: Int): Int = {
        val t = (top >>> shift) & 0xff
        val u = (under >>> shift) & 0xff
        math.round((t * ta + u * ua * (1.0 - ta)) / a).toInt
      }
      (math.round(a * 255.0).toInt << 24) | (mix(16) << 16) | (mix(8) << 8) | mix(0)
    }
  }

}

package everyui.tray

import java.lang.foreign.{MemorySegment, ValueLayout}

/** A tray icon's picture as macOS's menu bar takes it: an NSImage with a bitmap of each size (`TrayImage.sizes`), all
  * the one size in points, so the screen's scale picks the pixels (the smallest size 1x, one twice as wide @2x). */
private[tray] object MacImage {
  /** How tall the menu bar's icon is drawn, in points, where the smallest size is taller than a menu bar. */
  val Height: Double = 18.0

  /** The picture's size in points: the smallest size's pixels, or that scaled to `Height` where it is taller than the
    * menu bar (22 points). */
  def points(i: TrayImage): (Double, Double) = {
    val first = i.sizes.minBy(_.width)
    if (first.height <= 22) (first.width.toDouble, first.height.toDouble)
    else (first.width * Height / first.height, Height)
  }

  /** One size's pixels as an NSBitmapImageRep holds them by default: four bytes each, R G B A, the colour
    * premultiplied by the alpha. */
  def rgba(i: TrayImage): Array[Byte] = {
    val out = new Array[Byte](i.argb.length * 4)
    var n = 0
    while (n < i.argb.length) {
      val p = i.argb(n)
      val a = (p >>> 24) & 0xff
      def pre(shift: Int): Byte = ((((p >>> shift) & 0xff) * a + 127) / 255).toByte
      out(n * 4) = pre(16)
      out(n * 4 + 1) = pre(8)
      out(n * 4 + 2) = pre(0)
      out(n * 4 + 3) = a.toByte
      n += 1
    }
    out
  }

  /** The picture drawn in one colour by its alpha, white or black, at every size, no longer a template: a template as
    * the menu bar would have coloured it, for a badge to go on (a template's colour is the menu bar's to choose). */
  def inked(i: TrayImage, white: Boolean): TrayImage = {
    val ink = if (white) 0xffffff else 0
    val all = i.sizes.map(s => s.copy(argb = s.argb.map(p => (p & 0xff000000) | ink), template = false))
    TrayImage.of(all.head, all.tail*)
  }

  /** An NSImage of the picture (+1: release it once it is set), on the main thread. */
  def ns(i: TrayImage): MemorySegment = {
    import ObjC.*
    val (w, h) = points(i)
    val image = withSize(id(cls("NSImage"), "alloc"), "initWithSize:", w, h)
    i.sizes.foreach { s =>
      val bytes = rgba(s)
      val rep = send(InitBitmap, id(cls("NSBitmapImageRep"), "alloc"),
        "initWithBitmapDataPlanes:pixelsWide:pixelsHigh:bitsPerSample:samplesPerPixel:hasAlpha:isPlanar:" +
          "colorSpaceName:bitmapFormat:bytesPerRow:bitsPerPixel:",
        NULL, s.width.toLong, s.height.toLong, 8L, 4L, true, false, str("NSDeviceRGBColorSpace"), 0L,
        s.width * 4L, 32L).asInstanceOf[MemorySegment]
      if (!isNil(rep)) {
        val data = id(rep, "bitmapData").reinterpret(bytes.length.toLong)
        MemorySegment.copy(bytes, 0, data, ValueLayout.JAVA_BYTE, 0L, bytes.length)
        withSize(rep, "setSize:", w, h)
        id(image, "addRepresentation:", rep)
        release(rep)
      }
    }
    withBool(image, "setTemplate:", i.template)
    image
  }
}

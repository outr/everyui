import java.awt.image.BufferedImage
import java.awt.{AlphaComposite, Color, RenderingHints}
import java.io.File
import javax.imageio.ImageIO
import scala.sys.process._

/** Every platform's icon from the logo (`work/logo/svg`): the mark on its own for favicons and window icons, the
  * mark on the brand dark for app icons, the lockup for TV banners, channel icons and splash screens.
  *
  * `sbt makeIcons` writes:
  *   - `assets/images/everyui-mark.png` — 256 px, transparent: the web favicon and the desktop window icon
  *   - `android/res/mipmap-*​/ic_launcher.png` 48–192 px and `android/res/drawable-xhdpi/banner.png` 320x180
  *   - `roku/images/icon-{fhd,hd,sd}.png` and `splash-{fhd,hd,sd}.png`
  *   - `swift/app/Assets.xcassets/…`: the iOS 1024 icon, the tvOS App Icon 400x240 @1x/@2x, App Store 1280x768,
  *     Top Shelf 1920x720 and 2320x720 (the catalog's `Contents.json` files are checked in; actool compiles it)
  *
  * Rendering is `rsvg-convert` (librsvg), composition is AWT.
  */
object Icons {
  private val Dark = new Color(10, 10, 12)

  /** An SVG rendered at `size` px square with a transparent background. */
  def svg(file: File, size: Int): BufferedImage = {
    val bytes = Seq("rsvg-convert", "-w", size.toString, "-h", size.toString, file.getAbsolutePath).!!(ProcessLogger(_ => ()))
    ImageIO.read(new java.io.ByteArrayInputStream(bytes.getBytes("ISO-8859-1")))
  }

  /** `rsvg-convert` output as bytes (the String round trip above loses nothing at ISO-8859-1, but this is clearer). */
  def render(file: File, size: Int): BufferedImage = {
    val out = new java.io.ByteArrayOutputStream()
    (Seq("rsvg-convert", "-w", size.toString, "-h", size.toString, file.getAbsolutePath) #> out).!
    ImageIO.read(new java.io.ByteArrayInputStream(out.toByteArray))
  }

  private def canvas(w: Int, h: Int, background: Option[Color], rounded: Double): BufferedImage = {
    val img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    background.foreach { c =>
      g.setColor(c)
      if (rounded > 0) g.fillRoundRect(0, 0, w, h, (math.min(w, h) * rounded * 2).toInt, (math.min(w, h) * rounded * 2).toInt)
      else g.fillRect(0, 0, w, h)
    }
    g.dispose()
    img
  }

  /** `art`, rendered `scale` of the shorter side tall, centred on a (w, h) canvas. */
  private def centred(art: File, w: Int, h: Int, scale: Double, background: Option[Color], rounded: Double = 0.0): BufferedImage = {
    val img = canvas(w, h, background, rounded)
    val a = render(art, (math.min(w, h) * scale).toInt)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setComposite(AlphaComposite.SrcOver)
    g.drawImage(a, (w - a.getWidth) / 2, (h - a.getHeight) / 2, null)
    g.dispose()
    img
  }

  private def opaque(img: BufferedImage): BufferedImage = {
    val rgb = new BufferedImage(img.getWidth, img.getHeight, BufferedImage.TYPE_INT_RGB)
    val g = rgb.createGraphics()
    g.setColor(Dark); g.fillRect(0, 0, img.getWidth, img.getHeight)
    g.drawImage(img, 0, 0, null); g.dispose()
    rgb
  }

  def write(root: File, rel: String, img: BufferedImage, log: String => Unit): Unit = {
    val f = new File(root, rel)
    f.getParentFile.mkdirs()
    ImageIO.write(img, "png", f)
    log(f"$rel%-52s ${img.getWidth}x${img.getHeight} ${f.length}%6d bytes")
  }

  def make(root: File, log: String => Unit): Unit = {
    val mark = new File(root, "work/logo/svg/everyui-mark.svg")
    val lockup = new File(root, "work/logo/svg/everyui-lockup.svg")
    require(mark.exists && lockup.exists, s"logo sources missing under ${root}/work/logo/svg")
    def markOnDark(w: Int, h: Int, scale: Double, rounded: Double = 0.0) = centred(mark, w, h, scale, Some(Dark), rounded)
    def lockupOnDark(w: Int, h: Int, scale: Double) = opaque(centred(lockup, w, h, scale, Some(Dark)))

    // shared: the mark alone
    write(root, "assets/images/everyui-mark.png", render(mark, 256), log)

    // Android: launcher icons per density (a full-bleed square the launcher masks), and the TV banner
    for ((density, px) <- Seq("mdpi" -> 48, "hdpi" -> 72, "xhdpi" -> 96, "xxhdpi" -> 144, "xxxhdpi" -> 192))
      write(root, s"android/res/mipmap-$density/ic_launcher.png", markOnDark(px, px, 0.74, rounded = 0.18), log)
    write(root, "android/res/drawable-xhdpi/banner.png", lockupOnDark(320, 180, 0.95), log)

    // Roku: channel icons (shown focused in the home grid) and splash screens
    for ((name, (w, h)) <- Seq("fhd" -> (540, 405), "hd" -> (336, 210), "sd" -> (248, 140)))
      write(root, s"roku/images/icon-$name.png", lockupOnDark(w, h, 0.95), log)
    for ((name, (w, h)) <- Seq("fhd" -> (1920, 1080), "hd" -> (1280, 720), "sd" -> (720, 480)))
      write(root, s"roku/images/splash-$name.png", lockupOnDark(w, h, 0.7), log)

    // Apple: the asset catalog's images
    val xc = "swift/app/Assets.xcassets"
    val tv = xc + "/App Icon & Top Shelf Image.brandassets"
    write(root, xc + "/AppIcon.appiconset/ios-1024.png", opaque(markOnDark(1024, 1024, 0.74)), log)
    write(root, tv + "/App Icon.imagestack/Back.imagestacklayer/Content.imageset/icon-400x240.png", lockupOnDark(400, 240, 0.95), log)
    write(root, tv + "/App Icon.imagestack/Back.imagestacklayer/Content.imageset/icon-800x480.png", lockupOnDark(800, 480, 0.95), log)
    write(root, tv + "/App Icon - App Store.imagestack/Back.imagestacklayer/Content.imageset/icon-1280x768.png", lockupOnDark(1280, 768, 0.95), log)
    write(root, tv + "/Top Shelf Image.imageset/top-shelf-1920x720.png", lockupOnDark(1920, 720, 0.9), log)
    write(root, tv + "/Top Shelf Image Wide.imageset/top-shelf-2320x720.png", lockupOnDark(2320, 720, 0.9), log)
  }
}

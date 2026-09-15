import sbt.*

/** A Roku channel from a compiled Scala 3 program: EveryUI's compiler over its TASTy (and any libraries compiled
  * through with it), then the pieces a channel ships beside the generated code — EveryUI's runtime, images, fonts,
  * other packaged files, and the channel's icons and splash screens.
  *
  * EveryUI's `roku/emitRoku` calls it, and so does an app's build that includes this file in its meta-build. */
object RokuChannel {
  /** Run the compiler (`compilerClasspath`, EveryUI's `compiler` project) over `classes`, resolving against
    * `classpath`, into a fresh `out`. `sourceRoot` is where gaps are reported against; `title` names the channel. */
  def emit(log: String => Unit, compilerClasspath: Seq[File], classes: File, classpath: Seq[File], libraries: Seq[File],
           out: File, sourceRoot: File, title: String): File = {
    val sep = java.io.File.pathSeparator
    IO.delete(out)
    IO.createDirectory(out)
    log(s"emitRoku: emitting a Roku channel into $out")
    val rc = sys.process.Process(
      Seq("java", "-cp", compilerClasspath.map(_.getAbsolutePath).mkString(sep), "everyui.compiler.Main") ++
        libraries.map(f => s"--library=${f.getAbsolutePath}") ++
        Seq(classes.getAbsolutePath, classpath.map(_.getAbsolutePath).mkString(sep), out.getAbsolutePath,
          sourceRoot.getAbsolutePath, title)
    ).!
    if (rc != 0) sys.error(s"everyui.compiler.Main exited $rc")
    out
  }

  /** Rounded corners for the Roku runtime, which has none of its own (a SceneGraph Rectangle is square): white 9-patch
    * rounded rectangles the runtime draws as Posters tinted to the colour, `images/rr/fill-<r>.9.png` filled and
    * `images/rr/ring-<r>-<w>.9.png` an edge `w` wide, for every whole radius up to `maxRadius`. */
  def corners(out: File, maxRadius: Int = 64, maxStroke: Int = 8): Unit = {
    val dir = out / "images" / "rr"
    IO.createDirectory(dir)
    (1 to maxRadius).foreach { r =>
      ninePatch(dir / s"fill-$r.9.png", r, 0)
      (1 to math.min(maxStroke, r)).foreach(w => ninePatch(dir / s"ring-$r-$w.9.png", r, w))
    }
  }

  /** A rounded rectangle of radius `r` (filled, or an edge `stroke` wide), with the 9-patch markers on its top row and
    * left column: the middle row and column stretch, the corners do not. */
  private def ninePatch(file: File, r: Int, stroke: Int): Unit = {
    import java.awt.geom.{Area, RoundRectangle2D}
    import java.awt.image.BufferedImage
    import java.awt.{Color, RenderingHints}
    val inner = 2 * r + 1
    val image = new BufferedImage(inner + 2, inner + 2, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.setColor(Color.WHITE)
    val shape = new Area(new RoundRectangle2D.Double(1.0, 1.0, inner, inner, 2.0 * r, 2.0 * r))
    if (stroke > 0) {
      val ir = math.max(0, r - stroke)
      shape.subtract(new Area(new RoundRectangle2D.Double(1.0 + stroke, 1.0 + stroke, inner - 2.0 * stroke,
        inner - 2.0 * stroke, 2.0 * ir, 2.0 * ir)))
    }
    g.fill(shape)
    g.dispose()
    image.setRGB(1 + r, 0, 0xFF000000)
    image.setRGB(0, 1 + r, 0xFF000000)
    javax.imageio.ImageIO.write(image, "png", file)
  }

  /** Only Spice's durable client (and the WebSocket types it is written against) out of the `spiceCore` jar, as TASTy
    * to compile through: the whole jar stays on the classpath for symbol resolution. */
  def spiceTasty(spiceCore: File, dir: File): File = {
    IO.delete(dir)
    val keep = Set("spice/http/WebSocket.tasty", "spice/http/WebSocketChannels.tasty", "spice/http/ConnectionStatus.tasty",
      "spice/http/BinaryData.tasty", "spice/http/ByteBufferData.tasty", "spice/http/CloseReason.tasty",
      "spice/http/BufferedWebSocketChannels.tasty")
    IO.unzip(spiceCore, dir, (name: String) => keep(name) || (name.startsWith("spice/http/durable/") && name.endsWith(".tasty")))
    dir
  }

  /** Complete an emitted channel in `out`: EveryUI's runtime (`everyui/roku/runtime`, with the network module's Task
    * nodes), the images and fonts in `images` and `fonts` (directories, later ones winning), each of `files` at its
    * path in the package, and the channel's icons and splash screens from `channelImages` (declared in the manifest),
    * if given. */
  def assemble(out: File, everyui: File, images: Seq[File], fonts: Seq[File], files: Seq[(String, File)],
               channelImages: Option[File]): File = {
    (everyui / "roku" / "runtime" * ("*.brs" | "*.xml")).get().foreach(f => IO.copyFile(f, out / "components" / f.getName))
    images.filter(_.isDirectory).foreach(d => IO.copyDirectory(d, out / "images"))
    fonts.foreach(d => (d * "*.ttf").get().foreach(f => IO.copyFile(f, out / "fonts" / f.getName)))
    files.foreach { case (path, f) => IO.copyFile(f, out / path) }
    corners(out)
    channelImages.filter(_.isDirectory).foreach { d =>
      IO.copyDirectory(d, out / "images")
      val manifest = out / "manifest"
      IO.write(manifest, IO.read(manifest) +
        """mm_icon_focus_fhd=pkg:/images/icon-fhd.png
          |mm_icon_focus_hd=pkg:/images/icon-hd.png
          |mm_icon_focus_sd=pkg:/images/icon-sd.png
          |splash_screen_fhd=pkg:/images/splash-fhd.png
          |splash_screen_hd=pkg:/images/splash-hd.png
          |splash_screen_sd=pkg:/images/splash-sd.png
          |""".stripMargin)
    }
    // The scene is generated; add the runtime to its <script> list so the ui* globals resolve in the component's scope.
    val scene = out / "components" / "AppScene.xml"
    val original = IO.read(scene)
    val patched = original.replace(
      """<script type="text/brightscript" uri="pkg:/components/App.brs" />""",
      """<script type="text/brightscript" uri="pkg:/components/UniversalRuntime.brs" />
  <script type="text/brightscript" uri="pkg:/components/App.brs" />""")
    if (patched == original) sys.error("emitRoku: AppScene.xml no longer has the App.brs script line to patch")
    IO.write(scene, patched)
    out
  }
}

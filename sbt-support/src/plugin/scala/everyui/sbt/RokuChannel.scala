package everyui.sbt

import sbt.*

/** A Roku channel from a compiled Scala 3 program: EveryUI's compiler over its TASTy (and any libraries compiled
  * through with it), then the pieces a channel ships beside the generated code — EveryUI's runtime, images, fonts,
  * other packaged files, and the channel's icons and splash screens.
  *
  * EveryUI's `roku/emitRoku` calls it, and so does an app's build, from the sbt-everyui plugin. */
object RokuChannel {
  /** Run the compiler (`compilerClasspath`: EveryUI's `compiler` project, or `everyuiCompiler` in an app) over
    * `classes`, and the `libraries` compiled through with it (classes directories or jars: `everyuiEmitLibraries`),
    * resolving against `classpath`, into a fresh `out`. `sourceRoot` is where gaps are reported against; `title` names the channel.
    *
    * Anything the compiler cannot translate (a library method it does not know, a parameter that overwrites an
    * inherited member) fails the build, each listed at its Scala source: emitted, it would compile, lint clean and do
    * nothing on the device. `allowGaps` (or `-Deveryui.allowGaps=true`) lets such a channel through, for a look at
    * one in progress. */
  def emit(log: String => Unit, compilerClasspath: Seq[File], classes: File, classpath: Seq[File], libraries: Seq[File],
           out: File, sourceRoot: File, title: String,
           allowGaps: Boolean = sys.props.get("everyui.allowGaps").contains("true")): File = {
    val sep = java.io.File.pathSeparator
    IO.delete(out)
    IO.createDirectory(out)
    log(s"emitRoku: emitting a Roku channel into $out")
    val rc = sys.process.Process(
      Seq("java", "-cp", compilerClasspath.map(_.getAbsolutePath).mkString(sep), "everyui.compiler.Main") ++
        (if (allowGaps) Nil else Seq("--strict")) ++
        libraries.map(f => s"--library=${f.getAbsolutePath}") ++
        Seq(classes.getAbsolutePath, classpath.map(_.getAbsolutePath).mkString(sep), out.getAbsolutePath,
          sourceRoot.getAbsolutePath, title)
    ).!
    // -Deveryui.debugMotion=true: the channel tells its motions from its first launch (Main reads a .txt at its root as a
    // launch parameter, which a sideload's own launch cannot be given)
    if (sys.props.get("everyui.debugMotion").contains("true")) IO.write(out / "everyui_debug_motion.txt", "1")
    if (rc == 3) sys.error("emitRoku: the Roku compiler could not translate everything (listed above); -Deveryui.allowGaps=true emits the channel anyway")
    if (rc != 0) sys.error(s"everyui.compiler.Main exited $rc")
    out
  }

  /** Faces for the scripts a Roku has no glyphs for: its system font has no Arabic or CJK, and a Label draws in one
    * font, so the runtime draws such text in runs, each in a face that has its glyphs (`uiRichText`). Noto Sans Arabic
    * whole, and Noto Sans CJK SC and TC cut down to the characters in `text` (an app's translations) and printable
    * ASCII, as `fonts/fallback-<arabic|cjk-sc|cjk-tc>[-bold]`, regular and bold. Needs `hb-subset` (HarfBuzz) and the
    * Noto fonts under `noto` (`noto/NotoSansArabic-*.ttf`, `noto-cjk/NotoSansCJK-*.ttc`). */
  def fallbackFonts(log: String => Unit, out: File, text: String, fonts: File = file("/usr/share/fonts")): Unit = {
    val dir = out / "fonts"
    IO.createDirectory(dir)
    Seq("Regular" -> "", "Bold" -> "-bold").foreach { case (weight, suffix) =>
      val arabic = fonts / "noto" / s"NotoSansArabic-$weight.ttf"
      if (!arabic.exists) sys.error(s"fallbackFonts: $arabic is missing (Noto Sans Arabic)")
      IO.copyFile(arabic, dir / s"fallback-arabic$suffix.ttf")
      val cjk = fonts / "noto-cjk" / s"NotoSansCJK-$weight.ttc"
      if (!cjk.exists) sys.error(s"fallbackFonts: $cjk is missing (Noto Sans CJK)")
      // and the symbols the runtime draws in this face where the box's own has none (uiScriptOf): suits, stars, the sun, a tick
      val symbols = "\u2600\u2601\u2602\u2603\u2605\u2606\u2660\u2661\u2662\u2663\u2664\u2665\u2666\u2667\u2668\u2669" +
        "\u266A\u266B\u266C\u266D\u266E\u266F\u2713"
      val characters = (text + (32 to 126).map(_.toChar).mkString + symbols).distinct
      val list = IO.createTemporaryDirectory / "characters.txt"
      IO.write(list, characters)
      // the collection's faces: 2 is Simplified Chinese, 3 Traditional
      Seq("sc" -> 2, "tc" -> 3).foreach { case (variant, face) =>
        val target = dir / s"fallback-cjk-$variant$suffix.otf"
        val rc = sys.process.Process(Seq("hb-subset", cjk.getAbsolutePath, s"--face-index=$face",
          s"--text-file=${list.getAbsolutePath}", "-o", target.getAbsolutePath)).!
        if (rc != 0) sys.error(s"fallbackFonts: hb-subset exited $rc for $target")
      }
    }
    log(s"fallbackFonts: Arabic, and CJK for ${text.distinct.length} characters, in $dir")
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
    circle(dir / "circle.png", 512)
  }

  /** A rounded rectangle of radius `r` (filled, or an edge `stroke` wide), with the 9-patch markers on its top row and
    * left column: the middle row and column stretch, the corners do not. */
  /** A white disc on clear, `size` across: the mask a round picture is cut with, which a MaskGroup stretches to any size
    * (the runtime's own masks, drawn a pixel at a time on the device, came out empty for a large round one). */
  private def circle(file: File, size: Int): Unit = {
    import java.awt.image.BufferedImage
    import java.awt.{Color, RenderingHints}
    val image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setColor(Color.WHITE)
    g.fill(new java.awt.geom.Ellipse2D.Double(0.0, 0.0, size.toDouble, size.toDouble))
    g.dispose()
    javax.imageio.ImageIO.write(image, "png", file)
  }

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
    // All FOUR markers. The top and left ones say which pixel may be stretched; the right and bottom ones say where
    // the content sits, and they are not optional here — left off, SceneGraph derives the insets itself and gets the
    // far corner wrong, which showed as a small wedge hanging off the bottom-right of every rounded badge on a Roku.
    image.setRGB(1 + r, 0, 0xFF000000)
    image.setRGB(0, 1 + r, 0xFF000000)
    image.setRGB(1 + r, inner + 1, 0xFF000000)
    image.setRGB(inner + 1, 1 + r, 0xFF000000)
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

  /** Complete an emitted channel in `out`: EveryUI's runtime (the `.brs` and `.xml` files in `runtime`, with the network
    * module's Task nodes: EveryUI's `roku/runtime`, or `everyuiRokuRuntime` out of the everyui-roku jar), the images and
    * fonts in `images` and `fonts` (directories, later ones winning), each of `files` at its path in the package, and
    * the channel's icons and splash screens from `channelImages` (declared in the manifest), if given. */
  def assemble(out: File, runtime: File, images: Seq[File], fonts: Seq[File], files: Seq[(String, File)],
               channelImages: Option[File]): File = {
    (runtime * ("*.brs" | "*.xml")).get().foreach(f => IO.copyFile(f, out / "components" / f.getName))
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

  /** Set the channel's version in its manifest from a release's `major.minor.build` (`2.0.0`): Roku keeps the three as
    * numbers of their own, and a store update has to be higher than what it replaces. `code` is the build's number as
    * the app's other platforms count them (Android's versionCode), kept as `app_version_code` for Platform.appVersion. */
  def version(out: File, release: String, code: Int = 0): File = {
    val parts = release.takeWhile(c => c.isDigit || c == '.').split('.').filter(_.nonEmpty).map(_.toInt) ++ Array(0, 0, 0)
    val manifest = out / "manifest"
    val keys = Map("major_version" -> parts(0), "minor_version" -> parts(1), "build_version" -> parts(2))
    val lines = IO.readLines(manifest).filterNot(_.startsWith("app_version_code=")).map { line =>
      keys.collectFirst { case (k, v) if line.startsWith(k + "=") => k + "=" + v }.getOrElse(line)
    } ++ (if (code > 0) List("app_version_code=" + code) else Nil)
    IO.writeLines(manifest, lines)
    manifest
  }

  /** The manifest's build number set on its own: a store upload has to be a higher version than the one before it, and
    * a release uploaded again (a fix to its package) keeps its major and minor. */
  def build(out: File, number: Int): File = {
    val manifest = out / "manifest"
    IO.writeLines(manifest, IO.readLines(manifest).map(l => if (l.startsWith("build_version=")) "build_version=" + number else l))
    manifest
  }

  /** A signed package of the channel in `out`, for the channel store, made the only way Roku makes one: on a device
    * keyed with the developer's signing key (its `genkey`, once, or a rekey from an earlier package of the same key).
    * The channel is sideloaded to `ip` (its developer password `devPassword`), packaged there with the key's password,
    * and the package downloaded to `to`. The device's dev channel is replaced by it, as any sideload replaces it. */
  def packageChannel(log: String => Unit, out: File, ip: String, devPassword: String, keyPassword: String,
                     name: String, to: File): File = {
    import scala.sys.process.*
    val work = IO.createTemporaryDirectory
    val zip = work / "channel.zip"
    IO.zip(Path.allSubpaths(out).filterNot(_._1.isDirectory), zip, None)
    def post(fields: Seq[String]): String =
      (Seq("curl", "-s", "--digest", "-u", s"rokudev:$devPassword") ++ fields.flatMap(f => Seq("-F", f)) :+
        s"http://$ip/plugin_install").!!
    val installed = post(Seq("mysubmit=Install", s"archive=@$zip"))
    if (!installed.contains("Install Success") && !installed.contains("Identical"))
      sys.error("packageChannel: the Roku would not install the channel (a compile error?)")
    log(s"packageChannel: installed on $ip, packaging")
    val packaged = Seq("curl", "-s", "--digest", "-u", s"rokudev:$devPassword", "-F", "mysubmit=Package",
      "-F", s"app_name=$name", "-F", s"passwd=$keyPassword", "-F", s"pkg_time=${System.currentTimeMillis}",
      s"http://$ip/plugin_package").!!
    val link = "pkgs//?[A-Za-z0-9_.-]+\\.pkg".r.findFirstIn(packaged)
      .getOrElse(sys.error("packageChannel: no package came back (is the device keyed, and the key's password right?)"))
    IO.createDirectory(to.getParentFile)
    val rc = Seq("curl", "-s", "--digest", "-u", s"rokudev:$devPassword", "-o", to.getAbsolutePath,
      s"http://$ip/" + link.replace("//", "/")).!
    if (rc != 0 || !to.exists || to.length == 0) sys.error("packageChannel: the package would not download")
    log(s"packageChannel: $to (${to.length / 1024} KB)")
    to
  }
}

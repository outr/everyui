package everyui.sbt

import sbt.*

/** An Apple app (tvOS, iOS) from a compiled Scala 3 program: EveryUI's compiler over its TASTy writes it as Swift
  * (`App.swift`), and the files it is built with are put beside it — EveryUI's Swift runtime and launcher, the
  * Info.plist, the app's icons, images and fonts. The build itself is Xcode's toolchain's (swiftc and actool), on a
  * Mac: EveryUI's `tools/tvos-run.sh` is one.
  *
  * EveryUI's `tvos/emitSwift` calls it, and so does an app's build, from the sbt-everyui plugin. */
object AppleApp {
  /** Run the compiler (`compilerClasspath`: EveryUI's `compiler` project, or `everyuiCompiler` in an app) over
    * `classes`, and the `libraries` compiled through with it (`everyuiEmitLibraries`), resolving against `classpath`,
    * into a fresh `out`: the program as `App.swift`, `App2.swift` and on (the module's files, built together).
    * `sourceRoot` is where gaps are reported against.
    *
    * Anything the compiler cannot translate fails the build, each listed at its Scala source, as RokuChannel.emit's
    * does: a construct it has no Swift for, or a function the emitted Swift calls that the Swift runtime does not
    * define, which swiftc would otherwise refuse on the Mac. `allowGaps` (or `-Deveryui.allowGaps=true`) lets such an
    * app through, for a look at one in progress. */
  def emit(log: String => Unit, compilerClasspath: Seq[File], classes: File, classpath: Seq[File], libraries: Seq[File],
           out: File, sourceRoot: File,
           allowGaps: Boolean = sys.props.get("everyui.allowGaps").contains("true")): File = {
    val sep = java.io.File.pathSeparator
    IO.delete(out)
    IO.createDirectory(out)
    log(s"emitSwift: emitting Swift into $out")
    val rc = sys.process.Process(
      Seq("java", "-cp", compilerClasspath.map(_.getAbsolutePath).mkString(sep), "everyui.compiler.Main", "--target=swift") ++
        (if (allowGaps) Nil else Seq("--strict")) ++
        libraries.map(f => s"--library=${f.getAbsolutePath}") ++
        Seq(classes.getAbsolutePath, classpath.map(_.getAbsolutePath).mkString(sep), out.getAbsolutePath,
          sourceRoot.getAbsolutePath, "App")
    ).!
    if (rc == 3)
      sys.error("emitSwift: the compiler could not translate everything (listed above); " +
        "-Deveryui.allowGaps=true emits it anyway")
    if (rc != 0) sys.error(s"everyui.compiler.Main exited $rc")
    out
  }

  /** Complete an emitted app in `out`: the Swift `runtime` files (`ScalaRuntime.swift`, `UniversalRuntime.swift` and
    * the launcher, `main.swift`: EveryUI's `swift/`, or `everyuiSwiftRuntime` out of the everyui-tvos jar), the
    * `infoPlist` as `Info.plist`, the app's icons as `Assets.xcassets` if given, and the images and fonts in `images` and
    * `fonts` (directories, later ones winning). */
  def assemble(out: File, runtime: Seq[File], infoPlist: File, assetCatalog: Option[File], images: Seq[File],
               fonts: Seq[File]): File = {
    runtime.foreach(f => IO.copyFile(f, out / f.getName))
    IO.copyFile(infoPlist, out / "Info.plist")
    assetCatalog.foreach(c => IO.copyDirectory(c, out / "Assets.xcassets"))
    images.filter(_.isDirectory).foreach(d => IO.copyDirectory(d, out / "images"))
    fonts.foreach(d => (d * "*.ttf").get().foreach(f => IO.copyFile(f, out / "fonts" / f.getName)))
    out
  }
}

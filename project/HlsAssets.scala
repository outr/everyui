import sbt.*
import sbt.io.IO

/** hls.js's files for everyui-web, made as Monaco's and xterm.js's are (MonacoAssets, TerminalAssets): the npm package
  * fetched once from the registry, checked against the integrity npm publishes for it, and unpacked.
  *
  * What comes out is everyui-web's resources under `everyui-hls/<version>/`: hls.js's minified UMD build, the worker
  * it transmuxes in, and its licence. sbt-everyui's `everyuiWebGuests` copies them out next to the page, where the
  * web's VideoView loads the script by `<script src>` the first time a source needs it (everyui.render.HlsScript), so
  * a Content-Security-Policy of `script-src 'self'` holds and nothing comes from a CDN.
  *
  * The script is wrapped, as xterm's are, so that it defines its global (`Hls`) even in a page where an AMD loader is
  * already about (Monaco's require.js in the same app). */
object HlsAssets {
  import MonacoAssets.Npm

  val hls: Npm = Npm("hls.js", "1.7.3",
    "sha512-MsPlx6yVW4Qv4C7mEVou4/gk/5cN2dVxOTnrNjPSmyH2f0Ln+/UkG10Ax1PE6OvrmTVPKgQYozTtBaPOuCzq7A==")

  /** Where the root sits in the resources. */
  def root: String = "everyui-hls/" + hls.version

  /** The resources, written under `out`: every file, for a resource generator. */
  def generate(log: String => Unit, out: File): Seq[File] = {
    val base = out / root
    // beside the resources rather than among them, so it is not served
    val stamp = out.getParentFile / (root.replace('/', '-') + ".staged")
    if (!stamp.exists || IO.read(stamp) != hls.integrity) {
      IO.delete(base)
      val tgz = MonacoAssets.fetch(log, hls)
      MonacoAssets.unpack(tgz, base, "package/dist/hls.min.js", "hls.min.js")
      MonacoAssets.unpack(tgz, base, "package/dist/hls.worker.js", "hls.worker.js")
      MonacoAssets.unpack(tgz, base, "package/LICENSE", "licenses/hls.js.txt")
      List("hls.min.js", "hls.worker.js").foreach(name => strip(base / name))
      wrap(base / "hls.min.js")
      IO.write(stamp, hls.integrity)
      log(s"HlsAssets: hls.js ${hls.version} staged in $base")
    }
    (base ** "*").get().filter(_.isFile)
  }

  // its source map is not shipped, so its comment goes
  private def strip(script: File): Unit =
    IO.write(script, IO.read(script).replaceAll("(?m)^//# sourceMappingURL=.*$", ""))

  /** A UMD script made to see no module system: `define`, `exports` and `module` undefined inside it, so it sets its
    * global. */
  private def wrap(script: File): Unit =
    IO.write(script, "(function (define, exports, module) {\n" + IO.read(script) + "\n}).call(this);\n")
}

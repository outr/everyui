import sbt.*
import sbt.io.IO

/** xterm.js's files for everyui-terminal, made as Monaco's are (MonacoAssets): each npm package fetched once from the
  * registry, checked against the integrity npm publishes for it, and unpacked.
  *
  * What comes out is the module's resources under `everyui-terminal/<version>/`: xterm.js, its stylesheet, the fit and
  * web-links addons, their licences, and the bridge (`terminal/assets`: the guest's side of the protocol, the page a
  * web view shows, its style).
  *
  * The scripts are xterm's UMD builds, each wrapped so that it defines its global (`Terminal`, `FitAddon`,
  * `WebLinksAddon`) even in a page where an AMD loader is already about (Monaco's require.js in the same app), rather
  * than registering as an anonymous AMD module there: no loader of its own, nothing inline and nothing evaluated from
  * text, so a strict Content-Security-Policy holds. */
object TerminalAssets {
  import MonacoAssets.Npm

  val xterm: Npm = Npm("@xterm/xterm", "6.0.0",
    "sha512-TQwDdQGtwwDt+2cgKDLn0IRaSxYu1tSUjgKarSDkUM0ZNiSRXFpjxEsvc/Zgc5kq5omJ+V0a8/kIM2WD3sMOYg==")
  val fit: Npm = Npm("@xterm/addon-fit", "0.11.0",
    "sha512-jYcgT6xtVYhnhgxh3QgYDnnNMYTcf8ElbxxFzX0IZo+vabQqSPAjC3c1wJrKB5E19VwQei89QCiZZP86DCPF7g==")
  val webLinks: Npm = Npm("@xterm/addon-web-links", "0.12.0",
    "sha512-4Smom3RPyVp7ZMYOYDoC/9eGJJJqYhnPLGGqJ6wOBfB8VxPViJNSKdgRYb8NpaM6YSelEKbA2SStD7lGyqaobw==")

  /** Where the root sits in the resources. */
  def root: String = "everyui-terminal/" + xterm.version

  /** The resources, written under `out`, from the bridge in `bridge`: every file, for a resource generator. */
  def generate(log: String => Unit, bridge: File, out: File): Seq[File] = {
    val base = out / root
    val stamp = base / ".staged"
    val want = List(xterm, fit, webLinks).map(_.integrity).mkString(" ")
    if (!stamp.exists || IO.read(stamp) != want) {
      IO.delete(base)
      val x = MonacoAssets.fetch(log, xterm)
      MonacoAssets.unpack(x, base, "package/lib/xterm.js", "xterm.js")
      MonacoAssets.unpack(x, base, "package/css/xterm.css", "xterm.css")
      MonacoAssets.unpack(x, base, "package/LICENSE", "licenses/xterm.txt")
      val f = MonacoAssets.fetch(log, fit)
      MonacoAssets.unpack(f, base, "package/lib/addon-fit.js", "addon-fit.js")
      MonacoAssets.unpack(f, base, "package/LICENSE", "licenses/addon-fit.txt")
      val w = MonacoAssets.fetch(log, webLinks)
      MonacoAssets.unpack(w, base, "package/lib/addon-web-links.js", "addon-web-links.js")
      MonacoAssets.unpack(w, base, "package/LICENSE", "licenses/addon-web-links.txt")
      List("xterm.js", "addon-fit.js", "addon-web-links.js").foreach(name => wrap(base / name))
      IO.write(stamp, want)
      log(s"TerminalAssets: xterm.js ${xterm.version}, addon-fit ${fit.version} and addon-web-links ${webLinks.version} staged in $base")
    }
    // the bridge is ours, and changes with the build: always copied
    IO.copyDirectory(bridge, base, overwrite = true, preserveLastModified = true)
    (base ** ("*" -- ".staged")).get().filter(_.isFile)
  }

  /** A UMD script made to see no module system: `define`, `exports` and `module` undefined inside it, so it sets its
    * global. Its source map is not shipped, so its comment goes. */
  private def wrap(script: File): Unit = {
    val text = IO.read(script).replaceAll("(?m)^//# sourceMappingURL=.*$", "")
    IO.write(script, "(function (define, exports, module) {\n" + text + "\n}).call(this);\n")
  }
}

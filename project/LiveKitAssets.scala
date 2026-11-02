import sbt.*
import sbt.io.IO

/** LiveKit's browser client for everyui-web, made as hls.js's is (HlsAssets): the npm package fetched once from the
  * registry, checked against the integrity npm publishes for it, and unpacked.
  *
  * What comes out is everyui-web's resources under `everyui-livekit/<version>/`: livekit-client's UMD build (the
  * whole client in one script, its dependencies inside it) and its licence. sbt-everyui's `everyuiWebGuests` copies
  * them out next to the page, where the web's call engine loads the script by `<script src>` the first time a call
  * connects (everyui.render.LiveKitScript), so a Content-Security-Policy of `script-src 'self'` holds and nothing
  * comes from a CDN.
  *
  * The script is wrapped, as hls.js's is, so that it defines its global (`LivekitClient`) even in a page where an AMD
  * loader is already about (Monaco's require.js in the same app). */
object LiveKitAssets {
  import MonacoAssets.Npm

  val livekit: Npm = Npm("livekit-client", "2.23.0",
    "sha512-j9Kg86nJjBjNFUD+ukp2jwMCGa0k+YD5gU//RzAM+ZY3pbG631QCte7fQjwqWMmtBzhDJL9nKYuOLMv/9IbAxg==")

  /** Where the root sits in the resources. */
  def root: String = "everyui-livekit/" + livekit.version

  /** The resources, written under `out`: every file, for a resource generator. */
  def generate(log: String => Unit, out: File): Seq[File] = {
    val base = out / root
    // beside the resources rather than among them, so it is not served
    val stamp = out.getParentFile / (root.replace('/', '-') + ".staged")
    if (!stamp.exists || IO.read(stamp) != livekit.integrity) {
      IO.delete(base)
      val tgz = MonacoAssets.fetch(log, livekit)
      MonacoAssets.unpack(tgz, base, "package/dist/livekit-client.umd.js", "livekit-client.umd.js")
      MonacoAssets.unpack(tgz, base, "package/LICENSE", "licenses/livekit-client.txt")
      val script = base / "livekit-client.umd.js"
      // its source map is not shipped, so its comment goes; and it is made to see no module system, so it sets its
      // global
      IO.write(script, "(function (define, exports, module) {\n" +
        IO.read(script).replaceAll("(?m)^//# sourceMappingURL=.*$", "") + "\n}).call(this);\n")
      IO.write(stamp, livekit.integrity)
      log(s"LiveKitAssets: livekit-client ${livekit.version} staged in $base")
    }
    (base ** "*").get().filter(_.isFile)
  }
}

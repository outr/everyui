import sbt.*
import sbt.io.IO

/** LiveKit's browser client for everyui-web, made as hls.js's is (HlsAssets): the npm package fetched once from the
  * registry, checked against the integrity npm publishes for it, and unpacked.
  *
  * What comes out is everyui-web's resources under `everyui-livekit/<version>/`: livekit-client's UMD build (the
  * whole client in one script, its dependencies inside it), its licence, the licences of the packages bundled inside it
  * (`licenses/livekit-client/`) and EveryUI's NOTICE (`licenses/NOTICE.txt`), which carries the NOTICE files of
  * livekit-client, @livekit/protocol and @livekit/mutex as their Apache licence asks. sbt-everyui's
  * `everyuiWebGuests` copies them out next to the page, where the web's call engine loads the script by `<script src>`
  * the first time a call connects (everyui.render.LiveKitScript), so a Content-Security-Policy of `script-src 'self'`
  * holds and nothing comes from a CDN.
  *
  * The script is wrapped, as hls.js's is, so that it defines its global (`LivekitClient`) even in a page where an AMD
  * loader is already about (Monaco's require.js in the same app). */
object LiveKitAssets {
  import MonacoAssets.Npm

  val livekit: Npm = Npm("livekit-client", "2.23.0",
    "sha512-j9Kg86nJjBjNFUD+ukp2jwMCGa0k+YD5gU//RzAM+ZY3pbG631QCte7fQjwqWMmtBzhDJL9nKYuOLMv/9IbAxg==")

  /** The packages the UMD build has inside it, each with the entry its licence is read from: livekit-client's
    * dependencies (but typed-emitter, which is types alone) and theirs that carry code, at the version livekit-client
    * pins or the least its range allows, as the build does not say which it took. @bufbuild/protobuf is Apache 2.0
    * (the text livekit-client's own licence is) and BSD-3-Clause for its varint code, whose notice is that file's
    * header: its package has no licence file. */
  val bundled: Seq[(Npm, String)] = Seq(
    Npm("@livekit/mutex", "1.1.1",
      "sha512-EsshAucklmpuUAfkABPxJNhzj9v2sG7JuzFDL4ML1oJQSV14sqrpTYnsaOudMAw9yOaW53NU3QQTlUQoRs4czw==") ->
      "package/LICENSE",
    Npm("@livekit/protocol", "1.52.1",
      "sha512-X8DxKlNbzk6Lh+v630+mjdFHSotdAFwIyzxSRSUql2DbTxCnlpa7JsvFGc6Na3mXiex2OFh0iiQtWwOBP0rIYA==") ->
      "package/LICENSE",
    Npm("@bufbuild/protobuf", "1.10.0",
      "sha512-QDdVFLoN93Zjg36NoQPZfsVH9tZew7wKDKyV5qRdj8ntT4wQCOradQjRaTdwMhWUYsgKsvCINKKm87FdEk96Ag==") ->
      "package/dist/esm/google/varint.js",
    Npm("events", "3.3.0",
      "sha512-mQw+2fkQbALzQ7V0MY0IqdnXNOeTtP4r0lN9z7AAawCXgqea7bDii20AYrIBrFd/Hx0M2Ocz6S111CaFkUcb0Q==") ->
      "package/LICENSE",
    Npm("jose", "6.1.0",
      "sha512-TTQJyoEoKcC1lscpVDCSsVgYzUDg/0Bt3WE//WiTPK6uOCQC2KZS4MpugbMWt/zyjkopgZoXhZuCi00gLudfUA==") ->
      "package/LICENSE.md",
    Npm("loglevel", "1.9.2",
      "sha512-HgMmCqIJSAKqo68l0rS2AanEWfkxaZ5wNiEFb5ggm08lDs9Xl2KxBlX3PTcaD2chBM1gXAYf491/M2Rv8Jwayg==") ->
      "package/LICENSE-MIT",
    Npm("machina", "7.0.0",
      "sha512-QZY8V9N7Opmc60JMGl+6UCg0rQW04Wb55U/0CpzTCzhmCLw4FnP1P/n/hv4HaTKts4kGRD7s46KLDvYy2rdxCw==") ->
      "package/LICENSE",
    Npm("sdp-transform", "2.15.0",
      "sha512-KrOH82c/W+GYQ0LHqtr3caRpM3ITglq3ljGUIb8LTki7ByacJZ9z+piSGiwZDsRyhQbYBOBJgr2k6X4BZXi3Kw==") ->
      "package/LICENSE",
    Npm("sdp", "3.2.0",
      "sha512-d7wDPgDV3DDiqulJjKiV2865wKsJ34YI+NDREbm+FySq6WuKOikwyNQcm+doLAZ1O6ltdO0SeKle2xMpN3Brgw==") ->
      "package/LICENSE",
    Npm("tslib", "2.8.1",
      "sha512-oJFu94HQb+KVduSUQL7wnpmqnfmLsOA/nAh6b6EH0wCEoK0/mPeXU6c3wKDV83MkOuHPRHtSXKKU99IBazS/2w==") ->
      "package/LICENSE.txt",
    Npm("webrtc-adapter", "9.0.6",
      "sha512-CHbl2ZQbxx164IgWRgzJno4hWtM4tFbRam1QfI3Yxhs3w/DvqluVxVWeXs3oL5/fbGkSNLKo0Ty5MgUWceNhog==") ->
      "package/LICENSE.md"
  )

  /** Where the root sits in the resources. */
  def root: String = "everyui-livekit/" + livekit.version

  /** The resources, written under `out`, with EveryUI's NOTICE (`notice`): every file, for a resource generator. */
  def generate(log: String => Unit, notice: File, out: File): Seq[File] = {
    val base = out / root
    // beside the resources rather than among them, so it is not served
    val stamp = out.getParentFile / (root.replace('/', '-') + ".staged")
    val want = (livekit +: bundled.map(_._1)).map(_.integrity).mkString(" ")
    if (!stamp.exists || IO.read(stamp) != want) {
      IO.delete(base)
      val tgz = MonacoAssets.fetch(log, livekit)
      MonacoAssets.unpack(tgz, base, "package/dist/livekit-client.umd.js", "livekit-client.umd.js")
      MonacoAssets.unpack(tgz, base, "package/LICENSE", "licenses/livekit-client.txt")
      bundled.foreach { case (npm, entry) =>
        val as = base / "licenses" / "livekit-client" / (npm.name.stripPrefix("@").replace('/', '-') + ".txt")
        MonacoAssets.unpack(MonacoAssets.fetch(log, npm), base, entry, IO.relativize(base, as).get)
        // a source file's licence is its header comment
        if (entry.endsWith(".js")) {
          IO.write(as, IO.readLines(as).takeWhile(_.startsWith("//")).map(_.stripPrefix("//").stripPrefix(" "))
            .mkString("", "\n", "\n"))
        }
      }
      val script = base / "livekit-client.umd.js"
      // its source map is not shipped, so its comment goes; and it is made to see no module system, so it sets its
      // global
      IO.write(script, "(function (define, exports, module) {\n" +
        IO.read(script).replaceAll("(?m)^//# sourceMappingURL=.*$", "") + "\n}).call(this);\n")
      IO.write(stamp, want)
      log(s"LiveKitAssets: livekit-client ${livekit.version} staged in $base")
    }
    // EveryUI's own, and changes with the build: always copied
    IO.copyFile(notice, base / "licenses" / "NOTICE.txt")
    (base ** "*").get().filter(_.isFile)
  }
}

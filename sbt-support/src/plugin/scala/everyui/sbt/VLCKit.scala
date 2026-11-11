package everyui.sbt

import sbt.*
import sbt.io.IO

import java.security.MessageDigest

/** VLCKit, which plays an Apple app's video and sound (everyui-tvos's `swift/runtime/MediaRuntime.swift`): VideoLAN's own
  * release, the one its CocoaPods specs name, pinned and checked against its SHA-256, downloaded once for the machine
  * into ~/.cache/everyui/vlckit/ and unpacked there once. TVVLCKit for the Apple TV and MobileVLCKit for an iPhone and an
  * iPad, each a dynamic framework in an xcframework (a device slice and a simulator one), with no bitcode in either.
  *
  * VLCKit is LGPL 2.1, so it is never compiled into the app: it is linked dynamically and embedded beside it, in the
  * bundle's Frameworks folder, where it can be replaced (CONSUMING.md, THIRD-PARTY.md). It is not on Maven, and the
  * everyui-tvos jar does not carry it. `stage` puts it beside an emitted app that plays anything, for the Mac build
  * to link and embed (EveryUI's tools/tvos-run.sh shows how, fetching on the Mac with tools/vlckit.sh, whose pins are
  * these: change both together). */
object VLCKit {
  /** The framework's name, its version, where it is and its SHA-256, for `ios` and `tvos`. */
  val pins: Map[String, (String, String, String, String)] = Map(
    "ios" -> ("MobileVLCKit", "3.7.5",
      "https://download.videolan.org/pub/cocoapods/prod/MobileVLCKit-3.7.5-a213fd11-6de05adc.tar.xz",
      "a76f7a6cfcde4209b56f943e6e18400659d8a7a93ff482f8b1d059468d11e6be"),
    "tvos" -> ("TVVLCKit", "3.7.4",
      "https://download.videolan.org/cocoapods/prod/TVVLCKit-3.0-20260930-0854.tar.xz",
      "07892cae8e59e95a23f1fcf11692f802eae106312aa5ad9782faa2556b2d65d5"))

  /** Where it is kept: one place for the machine, outside every build (as Aars.room). */
  val room: File = file(sys.props("user.home")) / ".cache" / "everyui" / "vlckit"

  /** Whether the emitted app in `out` plays anything (its App.swift makes a player): only then is VLCKit linked. */
  def plays(out: File): Boolean = {
    val app = out / "App.swift"
    app.isFile && IO.read(app).contains("uiMedia(")
  }

  /** The archive for `platform` (`ios` or `tvos`), downloaded once and checked every time it is used. */
  def fetch(log: String => Unit, platform: String): File = {
    val (name, version, url, sha) = pin(platform)
    val archive = room / s"$name-$version.tar.xz"
    if (!archive.isFile || sha256(archive) != sha) {
      IO.createDirectory(room)
      val part = room / s"$name-$version.tar.xz.part"
      log(s"VLCKit: downloading $name $version")
      IO.transfer(new java.net.URI(url).toURL.openStream(), part)
      val got = sha256(part)
      if (got != sha) {
        IO.delete(part)
        sys.error(s"VLCKit: $url is $got, not $sha")
      }
      IO.move(part, archive)
      IO.delete(room / s"$name-$version")
    }
    archive
  }

  /** The xcframework for `platform`, unpacked once beside its archive (by `tar`, which reads xz on a Mac and on
    * Linux), with VLCKit's licence (COPYING.txt) beside it. */
  def xcframework(log: String => Unit, platform: String): File = {
    val (name, version, _, _) = pin(platform)
    val archive = fetch(log, platform)
    val out = room / s"$name-$version"
    val framework = out / s"$name.xcframework"
    if (!framework.isDirectory) {
      val part = room / s"$name-$version.unpacking"
      IO.delete(Seq(out, part))
      IO.createDirectory(part)
      val rc = sys.process.Process(Seq("tar", "-xJf", archive.getAbsolutePath, "-C", part.getAbsolutePath)).!
      if (rc != 0) sys.error(s"VLCKit: tar could not unpack $archive (it needs xz)")
      IO.move(part / s"$name-binary", out)
      IO.delete(part)
    }
    framework
  }

  /** VLCKit put beside the emitted app in `out` where it plays anything, for each of `platforms`: `Frameworks/<name>
    * .xcframework` (its slices without their debug symbols) and `Frameworks/<name>-COPYING.txt`. The Mac build links
    * the slice it builds for (`-F <xcframework>/<slice> -framework <name>`, with `@executable_path/Frameworks` on the
    * run path) and copies that slice's framework into the bundle's Frameworks folder, signed with the app. Nothing is
    * put there for an app that plays nothing. */
  def stage(log: String => Unit, out: File, platforms: Seq[String] = Seq("ios", "tvos")): Seq[File] =
    if (!plays(out)) Nil
    else platforms.map { platform =>
      val (name, _, _, _) = pin(platform)
      val from = xcframework(log, platform)
      val to = out / "Frameworks" / s"$name.xcframework"
      IO.delete(to)
      IO.copyDirectory(from, to, overwrite = true, preserveLastModified = true)
      (to * "*" * "dSYMs").get().foreach(IO.delete)
      IO.copyFile(from.getParentFile / "COPYING.txt", out / "Frameworks" / s"$name-COPYING.txt")
      to
    }

  private def pin(platform: String): (String, String, String, String) =
    pins.getOrElse(platform, sys.error(s"VLCKit: no VLCKit for $platform (ios or tvos)"))

  private def sha256(f: File): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = new java.io.FileInputStream(f)
    try {
      val buffer = new Array[Byte](1 << 16)
      var n = in.read(buffer)
      while (n > 0) {
        digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    }
    finally in.close()
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }
}

import sbt.*
import sbt.io.IO

import java.io.{BufferedInputStream, InputStream}
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Monaco's files for everyui-monaco, made the same way every time rather than committed by hand: the editor's npm
  * package and the AMD loader it runs under (require.js, since Monaco's own min build stopped shipping one), each
  * fetched once from the npm registry, checked against the integrity npm publishes for it, and unpacked.
  *
  * What comes out is the module's resources under `everyui-monaco/<version>/`: Monaco's `min/vs` as `vs/`, require.js,
  * the licences under `licenses/` (Monaco's, its ThirdPartyNotices, and DOMPurify's, which its min build bundles and
  * the notices do not name; require.js's package has none, so its licence is kept in `monaco/assets/licenses`), and
  * the bridge (`monaco/assets`: the guest's side of the protocol, the page a web view shows, its style). The one
  * path serves every platform: classpath resources on the desktop, APK assets on Android, and files served next to
  * the page on the web. */
object MonacoAssets {
  /** A package from the npm registry, by the tarball's address and the `integrity` its registry entry lists. */
  case class Npm(name: String, version: String, integrity: String) {
    def url: String = s"https://registry.npmjs.org/$name/-/${name.split('/').last}-$version.tgz"
  }

  val monaco: Npm = Npm("monaco-editor", "0.57.0",
    "sha512-5BkI9KGoqrNvBGUe15/QlZq3OooZ8WLg1AxTpaqHRCP3HNpzPPZKE2EDz8M7c+VRmCeUw1Brp4cx/PWm3kI/5A==")
  val requirejs: Npm = Npm("requirejs", "2.3.8",
    "sha512-7/cTSLOdYkNBNJcDMWf+luFvMriVm7eYxp4BcFCsAX0wF421Vyce5SXP17c+Jd5otXKGNehIonFlyQXSowL6Mw==")
  /** DOMPurify, at the version Monaco's package pins: its min build has it inside, for its licence. */
  val dompurify: Npm = Npm("dompurify", "3.4.15",
    "sha512-EUBjM+B+lkDE41iE82DDSCfkoPGfXx8IxFxPMjNzm/Uk4xDet77rTN9wqlxlVg71kK7XGuUMv6wUxJUwwv+Xyw==")

  /** Where the root sits in the resources. */
  def root: String = "everyui-monaco/" + monaco.version

  /** Where downloads are kept: one place for the machine, outside every build (see Aars.room). */
  val room: File = file(sys.props("user.home")) / ".cache" / "everyui" / "npm"

  /** The resources, written under `out`, from the bridge in `bridge`: every file, for a resource generator. */
  def generate(log: String => Unit, bridge: File, out: File): Seq[File] = {
    val base = out / root
    val stamp = base / ".staged"
    val want = List(monaco, requirejs, dompurify).map(_.integrity).mkString(" ")
    if (!stamp.exists || IO.read(stamp) != want) {
      IO.delete(base)
      val m = fetch(log, monaco)
      unpack(m, base, "package/min/vs/", "vs/")
      unpack(m, base, "package/LICENSE", "licenses/monaco-editor.txt")
      unpack(m, base, "package/ThirdPartyNotices.txt", "licenses/monaco-editor-ThirdPartyNotices.txt")
      unpack(fetch(log, dompurify), base, "package/LICENSE", "licenses/dompurify.txt")
      unpack(fetch(log, requirejs), base, "package/require.js", "require.js")
      IO.write(stamp, want)
      log(s"MonacoAssets: Monaco ${monaco.version} and require.js ${requirejs.version} staged in $base")
    }
    // the bridge is ours, and changes with the build: always copied
    IO.copyDirectory(bridge, base, overwrite = true, preserveLastModified = true)
    (base ** ("*" -- ".staged")).get().filter(_.isFile)
  }

  /** The tarball, downloaded once and checked against its integrity every time it is used. */
  def fetch(log: String => Unit, npm: Npm): File = {
    val file = room / s"${npm.name.replace('/', '_')}-${npm.version}.tgz"
    if (!file.exists || integrity(file) != npm.integrity) {
      log(s"MonacoAssets: fetching ${npm.url}")
      IO.createDirectory(room)
      val part = room / (file.getName + ".part")
      val in = new java.net.URI(npm.url).toURL.openStream()
      try IO.transfer(in, part) finally in.close()
      val got = integrity(part)
      if (got != npm.integrity) {
        IO.delete(part)
        sys.error(s"MonacoAssets: ${npm.url} is not what npm published (integrity $got, expected ${npm.integrity})")
      }
      IO.move(part, file)
    }
    file
  }

  /** npm's integrity for a file: the SHA-512 of its bytes, in base64. */
  def integrity(file: File): String = {
    val digest = MessageDigest.getInstance("SHA-512")
    IO.readBytes(file).grouped(1 << 20).foreach(chunk => digest.update(chunk))
    "sha512-" + java.util.Base64.getEncoder.encodeToString(digest.digest())
  }

  /** The entries of a gzipped tarball under `from` (a directory ending in `/`, or one file), written under `to` as
    * `as` (a directory, or the file's own name). */
  def unpack(tgz: File, to: File, from: String, as: String): Unit = {
    val in = new BufferedInputStream(new GZIPInputStream(new java.io.FileInputStream(tgz)))
    try {
      val header = new Array[Byte](512)
      var longName: Option[String] = None
      var done = false
      while (!done) {
        if (!readFully(in, header)) done = true
        else if (header.forall(_ == 0)) done = true
        else {
          def field(offset: Int, length: Int): String =
            new String(header, offset, length, "UTF-8").takeWhile(_ != '\u0000')
          val size = java.lang.Long.parseLong(field(124, 12).trim match { case "" => "0"; case s => s }, 8)
          val kind = header(156).toChar
          val prefix = field(345, 155)
          val name = longName.getOrElse(if (prefix.isEmpty) field(0, 100) else prefix + "/" + field(0, 100))
          longName = None
          val body = new Array[Byte](size.toInt)
          readFully(in, body)
          val padding = (512 - (size % 512)) % 512
          in.skipNBytes(padding)
          if (kind == 'L') longName = Some(new String(body, "UTF-8").takeWhile(_ != '\u0000'))
          else if ((kind == '0' || kind == '\u0000') && (name == from || (from.endsWith("/") && name.startsWith(from)))) {
            val target = if (from.endsWith("/")) to / (as + name.substring(from.length)) else to / as
            IO.write(target, body)
          }
        }
      }
    }
    finally in.close()
  }

  private def readFully(in: InputStream, into: Array[Byte]): Boolean = {
    var read = 0
    while (read < into.length) {
      val n = in.read(into, read, into.length - read)
      if (n < 0) return false
      read += n
    }
    true
  }
}

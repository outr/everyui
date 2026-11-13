package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest

/** A Roku channel split into its package and a remote bundle, and held to its budget (RokuAssets, RokuRemote). */
class RokuAssetsSpec extends AnyWordSpec with Matchers {
  private def write(root: File, path: String, bytes: Array[Byte]): File = {
    val f = new File(root, path)
    f.getParentFile.mkdirs()
    Files.write(f.toPath, bytes)
    f
  }
  private def text(root: File, path: String, words: String): File = write(root, path, words.getBytes(UTF_8))
  private def read(f: File): String = new String(Files.readAllBytes(f.toPath), UTF_8)
  private def sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString
  // bytes that do not compress: a picture's, a font's
  private def noise(n: Int, seed: Long): Array[Byte] = {
    val bytes = new Array[Byte](n)
    new java.util.Random(seed).nextBytes(bytes)
    bytes
  }

  /** A channel as assemble leaves one: code, manifest, icons, corners, pictures, fonts, sounds, translations. */
  private def channel(): File = {
    val root = Files.createTempDirectory("roku-channel").toFile
    text(root, "manifest", "title=Test\n")
    text(root, "source/Main.brs", "sub Main()\nend sub\n")
    text(root, "components/App.brs", "' code\n" * 100)
    write(root, "images/icon-fhd.png", noise(300, 1))
    write(root, "images/splash-fhd.png", noise(300, 2))
    write(root, "images/rr/fill-4.9.png", noise(200, 3))
    write(root, "images/weather/sun.png", noise(5000, 4))
    write(root, "images/logo.png", noise(4000, 5))
    write(root, "images/copy.png", noise(4000, 5))
    write(root, "fonts/Roboto-Regular.ttf", noise(3000, 6))
    write(root, "fonts/Roboto-Medium.ttf", noise(3000, 7))
    write(root, "fonts/Roboto-Bold.ttf", noise(3000, 8))
    write(root, "fonts/Roboto-Light.ttf", noise(3000, 9))
    write(root, "fonts/MaterialIcons-Regular.ttf", noise(3000, 10))
    write(root, "fonts/fallback-cjk-sc.otf", noise(9000, 11))
    write(root, "sounds/fx_fail.wav", noise(6000, 12))
    text(root, "l10n/app_en.arb", "{\"a\": \"A\"}")
    text(root, "l10n/app_fr.arb", "{\"a\": \"Ah\"}")
    text(root, "l10n/app_zh_Hant.arb", "{\"a\": \"啊\"}")
    text(root, "freeze.txt", "2")
    root
  }

  private val settings = RokuRemote("https://cdn.example.com/roku/")

  "A glob" should {
    "take * within a directory, ** across them, and ? for one character" in {
      RokuAssets.glob("images/*", "images/a.png") shouldBe true
      RokuAssets.glob("images/*", "images/rr/a.png") shouldBe false
      RokuAssets.glob("images/**", "images/rr/a.png") shouldBe true
      RokuAssets.glob("images/**/a.png", "images/a.png") shouldBe true
      RokuAssets.glob("images/**/a.png", "images/x/y/a.png") shouldBe true
      RokuAssets.glob("fonts/Roboto-?old.ttf", "fonts/Roboto-Bold.ttf") shouldBe true
      RokuAssets.glob("fonts/*.ttf", "fonts/a.otf") shouldBe false
      RokuAssets.glob("l10n/app_(en).arb", "l10n/app_e.arb") shouldBe false
    }
  }

  "The split" should {
    "keep what a channel starts with, its corners, its core faces and its language, and send the rest" in {
      val root = channel()
      val remote = RokuAssets.files(root).filter(RokuAssets.remote(_, settings))
      remote shouldBe Seq("fonts/Roboto-Bold.ttf", "fonts/Roboto-Light.ttf", "fonts/fallback-cjk-sc.otf",
        "images/copy.png", "images/logo.png", "images/weather/sun.png", "l10n/app_fr.arb", "l10n/app_zh_Hant.arb",
        "sounds/fx_fail.wav")
    }
    "send nothing without a base" in {
      RokuAssets.files(channel()).filter(RokuAssets.remote(_, RokuRemote.none)) shouldBe empty
    }
    "follow the app's own globs, core faces and languages" in {
      val own = settings.copy(local = Seq("images/rr/**", "images/weather/**"), coreFonts = Seq("Roboto-Bold.ttf"),
        languages = Seq("en", "zh_Hant"), remote = Seq("images/**", "fonts/**", "l10n/**"))
      val remote = RokuAssets.files(channel()).filter(RokuAssets.remote(_, own))
      remote should contain allOf ("fonts/Roboto-Regular.ttf", "fonts/MaterialIcons-Regular.ttf", "l10n/app_fr.arb")
      remote should not contain "images/weather/sun.png"
      remote should not contain "fonts/Roboto-Bold.ttf"
      remote should not contain "l10n/app_zh_Hant.arb"
      remote should not contain "sounds/fx_fail.wav"
    }
    "tell a translation by its code at the end of its name" in {
      RokuAssets.language("l10n/app_en.arb", Seq("en")) shouldBe true
      RokuAssets.language("l10n/en.json", Seq("en")) shouldBe true
      RokuAssets.language("l10n/app_en_GB.arb", Seq("en")) shouldBe false
      RokuAssets.language("l10n/app_ten.arb", Seq("en")) shouldBe false
      RokuAssets.language("images/app_en.png", Seq("en")) shouldBe false
    }
    "draw a remote face in the core face of its family nearest its weight, else the plainest" in {
      val core = Seq("fonts/Roboto-Regular.ttf", "fonts/Roboto-Medium.ttf", "fonts/MaterialIcons-Regular.ttf")
      RokuAssets.standIn("fonts/Roboto-Bold.ttf", core) shouldBe "fonts/Roboto-Medium.ttf"
      RokuAssets.standIn("fonts/Roboto-Light.ttf", core) shouldBe "fonts/Roboto-Regular.ttf"
      RokuAssets.standIn("fonts/fallback-cjk-sc-bold.otf", core) shouldBe "fonts/Roboto-Regular.ttf"
      RokuAssets.standIn("fonts/RobotoMono-Regular.ttf", core) shouldBe "fonts/Roboto-Regular.ttf"
      RokuAssets.standIn("fonts/Roboto-Bold.ttf", Seq("fonts/MaterialIcons-Regular.ttf")) shouldBe ""
    }
  }

  "The bundle" should {
    "hold each remote file under its hash, and the channel its table" in {
      val root = channel()
      val sun = Files.readAllBytes(new File(root, "images/weather/sun.png").toPath)
      val out = Files.createTempDirectory("roku-bundle").toFile
      text(out, "stale.png", "from an earlier build")
      val moved = RokuAssets.bundle(_ => (), _ => (), root, settings, out, transcoder = None)
      moved.map(_.path) should contain ("images/weather/sun.png")
      new File(root, "images/weather").exists shouldBe false
      new File(root, "images/weather/sun.png").exists shouldBe false
      new File(root, "images/rr/fill-4.9.png").exists shouldBe true
      new File(root, "fonts/Roboto-Medium.ttf").exists shouldBe true
      new File(out, "stale.png").exists shouldBe false
      val hosted = new File(out, sha(sun) + ".png")
      hosted.exists shouldBe true
      Files.readAllBytes(hosted.toPath).toSeq shouldBe sun.toSeq
      // the same bytes under two paths are one file
      moved.find(_.path == "images/logo.png").map(_.hash) shouldBe moved.find(_.path == "images/copy.png").map(_.hash)
      out.listFiles.count(_.getName.endsWith(".png")) shouldBe 2
      val table = read(new File(root, RokuAssets.tableName))
      read(new File(out, RokuAssets.tableName)) shouldBe table
      table should include ("\"base\":\"https://cdn.example.com/roku\"")
      table should include ("\"http\":false")
      table should include ("\"images/weather/sun.png\":{\"h\":\"" + sha(sun) + "\",\"e\":\"png\",\"s\":5000}")
      table should include ("\"fonts/Roboto-Bold.ttf\":{\"h\":")
      table should include ("\"f\":\"fonts/Roboto-Medium.ttf\"")
      // the app's other faces at start, a script's face never
      table should include ("\"prefetch\":[\"fonts/Roboto-Bold.ttf\",\"fonts/Roboto-Light.ttf\"]")
    }
    "name a 9-patch .9.png, as SceneGraph knows one" in {
      RokuAssets.ext("images/rr/fill-4.9.png") shouldBe "9.png"
      RokuAssets.ext("images/A.PNG") shouldBe "png"
      RokuAssets.ext("fonts/fallback-cjk-sc.otf") shouldBe "otf"
      val root = channel()
      val out = Files.createTempDirectory("roku-bundle").toFile
      val moved = RokuAssets.bundle(_ => (), _ => (), root, settings.copy(local = Nil), out, transcoder = None)
      moved.find(_.path == "images/rr/fill-4.9.png").map(_.ext) shouldBe Some("9.png")
    }
    "give a WAV a compressed form for the player, beside the WAV a SoundEffect plays" in {
      val root = channel()
      val out = Files.createTempDirectory("roku-bundle").toFile
      var asked = List.empty[String]
      val fake: (File, File, String) => Boolean = (wav, to, format) => {
        asked = s"${wav.getName} $format" :: asked
        Files.write(to.toPath, "compressed".getBytes(UTF_8))
        true
      }
      val moved = RokuAssets.bundle(_ => (), _ => (), root, settings.copy(transcode = Some("m4a")), out, Some(fake))
      asked shouldBe List("fx_fail.wav m4a")
      val fx = moved.find(_.path == "sounds/fx_fail.wav").get
      fx.ext shouldBe "wav"
      fx.media.map(m => (m.hash, m.ext)) shouldBe Some((sha("compressed".getBytes(UTF_8)), "m4a"))
      new File(out, fx.name).exists shouldBe true
      new File(out, fx.media.get.name).exists shouldBe true
      read(new File(root, RokuAssets.tableName)) should include ("\"me\":\"m4a\"")
    }
    "send the WAVs as they are, with a warning, where there is no transcoder, unless one is needed" in {
      val warned = scala.collection.mutable.Buffer.empty[String]
      val moved = RokuAssets.bundle(_ => (), warned += _, channel(), settings,
        Files.createTempDirectory("roku-bundle").toFile, transcoder = None)
      moved.find(_.path == "sounds/fx_fail.wav").flatMap(_.media) shouldBe None
      warned.mkString should include ("no ffmpeg")
      an[IllegalStateException] should be thrownBy RokuAssets.bundle(_ => (), _ => (), channel(),
        settings.copy(needTranscoder = true), Files.createTempDirectory("roku-bundle").toFile, transcoder = None)
    }
    "refuse a base that is not https, but for a test that allows http" in {
      val out = Files.createTempDirectory("roku-bundle").toFile
      an[IllegalArgumentException] should be thrownBy
        RokuAssets.bundle(_ => (), _ => (), channel(), RokuRemote("http://cdn.example.com"), out, transcoder = None)
      val root = channel()
      RokuAssets.bundle(_ => (), _ => (), root, RokuRemote("http://localhost:8099", allowHttp = true), out,
        transcoder = None) should not be empty
      read(new File(root, RokuAssets.tableName)) should include ("\"http\":true")
    }
    "leave the channel as it was without a base" in {
      val root = channel()
      val before = RokuAssets.files(root)
      RokuAssets.bundle(_ => (), _ => (), root, RokuRemote.none, Files.createTempDirectory("roku-bundle").toFile,
        transcoder = None) shouldBe empty
      RokuAssets.files(root) shouldBe before
    }
  }

  "The budget" should {
    "pass a package within it and fail one over it, naming what is biggest" in {
      val root = channel()
      val (size, sizes) = RokuAssets.packageSize(root)
      size should be > sizes.map(_._2).sum
      RokuAssets.overBudget(root, size) shouldBe None
      val over = RokuAssets.overBudget(root, size - 1)
      over shouldBe defined
      over.get should include ("over its budget")
      // the directory with most in it first, then its biggest file
      over.get should include ("biggest parts fonts ")
      over.get should include ("its biggest files fonts/fallback-cjk-sc.otf")
    }
    "be met once the remote files have gone" in {
      val root = channel()
      val (before, _) = RokuAssets.packageSize(root)
      RokuAssets.bundle(_ => (), _ => (), root, settings, Files.createTempDirectory("roku-bundle").toFile,
        transcoder = None)
      val (after, _) = RokuAssets.packageSize(root)
      after should be < before / 2
      RokuAssets.overBudget(root, after) shouldBe None
    }
  }
}

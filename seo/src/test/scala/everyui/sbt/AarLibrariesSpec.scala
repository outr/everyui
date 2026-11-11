package everyui.sbt

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

/** An app's Android archives as sbt-everyui hands them to `AndroidApk.build` (AndroidArchive): every one among the
  * dependencies, EveryUI's and the app's own, once a module however it was reached and at the version kept, with its
  * classes, its rules and, where its classes read them, its resources; and what R8 is given, each archive's classes
  * and rules once, whatever copy of them the app's build has added, or the build stopped naming the archive. */
class AarLibrariesSpec extends AnyWordSpec with Matchers {
  private val work = Files.createDirectories(new File("target/aar-libraries-spec").getAbsoluteFile.toPath)

  private def zip(entries: Seq[(String, Array[Byte])]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val z = new ZipOutputStream(out)
    entries.foreach { case (name, data) =>
      val e = new ZipEntry(name)
      e.setTime(315532800000L)   // one time for every entry, so an archive written again is the same bytes
      z.putNextEntry(e)
      z.write(data)
      z.closeEntry()
    }
    z.close()
    out.toByteArray
  }

  private def fresh(label: String): Path = Files.createDirectories(work.resolve(s"$label-${System.nanoTime()}"))

  // a class's bytes: its name, and what it names (another class, an R class), as a class file would hold them
  private def cls(name: String, names: String*): Array[Byte] = ("CAFE" +: name +: names).mkString(";").getBytes("UTF-8")

  // the classes of a package, `version` in each so two versions differ, and their jar
  private def entries(pkg: String, version: String, readsR: Boolean): Seq[(String, Array[Byte])] = {
    val p = pkg.replace('.', '/')
    Seq(s"$p/A.class" -> cls(s"$p/A", version), s"$p/B.class" ->
      (if (readsR) cls(s"$p/B", version, s"$p/R$$bool") else cls(s"$p/B", version)))
  }

  private def classes(pkg: String, version: String, readsR: Boolean): Array[Byte] = zip(entries(pkg, version, readsR))

  // an archive of `pkg` at `version`: its classes, rules unless told not to, and resources
  private def archive(dir: Path, name: String, pkg: String, version: String, rules: Boolean = true,
                      readsR: Boolean = false): File = {
    val parts = Seq("AndroidManifest.xml" -> s"""<manifest package="$pkg"/>""".getBytes("UTF-8"),
      "classes.jar" -> classes(pkg, version, readsR)) ++
      (if (rules) Seq("proguard.txt" -> s"-keep class $pkg.** { *; }\n".getBytes("UTF-8")) else Nil) ++
      Seq("res/values/values.xml" -> "<resources><bool name=\"x\">true</bool></resources>".getBytes("UTF-8"))
    val file = dir.resolve(s"$name-$version.aar")
    Files.write(file, zip(parts))
    file.toFile
  }

  private def write(dir: Path, name: String, bytes: Array[Byte]): File = Files.write(dir.resolve(name), bytes).toFile

  private def classNames(jar: File): Set[String] = {
    val z = new ZipFile(jar)
    try z.entries().asScala.map(_.getName).toSet finally z.close()
  }

  // EveryUI's archives (Media3's, WorkManager's, whose classes read its R class) and the app's own (Play services'),
  // Media3's reached twice and Play services' basement at two versions, as two routes to it would resolve
  private def resolved(repo: Path): Seq[(String, String, String, File)] = {
    val common = archive(repo, "media3-common", "androidx.media3.common", "1.9.2")
    val exo = archive(repo, "media3-exoplayer", "androidx.media3.exoplayer", "1.9.2", rules = false)
    val work = archive(repo, "work-runtime", "androidx.work", "2.12.0", readsR = true)
    val auth = archive(repo, "play-services-auth", "com.google.android.gms.auth", "21.2.0")
    val older = archive(repo, "play-services-basement", "com.google.android.gms.common", "18.3.0")
    val newer = archive(repo, "play-services-basement", "com.google.android.gms.common", "18.9.0")
    Seq(("androidx.media3", "media3-common", "1.9.2", common), ("androidx.media3", "media3-exoplayer", "1.9.2", exo),
      ("androidx.work", "work-runtime", "2.12.0", work),
      ("com.google.android.gms", "play-services-auth", "21.2.0", auth),
      ("com.google.android.gms", "play-services-basement", "18.3.0", older),
      ("androidx.media3", "media3-common", "1.9.2", common),
      ("com.google.android.gms", "play-services-basement", "18.9.0", newer))
  }

  "The archives among the dependencies" should {
    "be each module once, at its latest version, EveryUI's and the app's alike" in {
      val repo = fresh("repo")
      val chosen = AndroidArchive.chosen(resolved(repo))
      chosen.map(_._1) shouldBe Seq("androidx.media3:media3-common", "androidx.media3:media3-exoplayer",
        "androidx.work:work-runtime", "com.google.android.gms:play-services-auth",
        "com.google.android.gms:play-services-basement")
      chosen.map(_._2.getName) should contain("play-services-basement-18.9.0.aar")
      chosen.map(_._2.getName) should not contain "play-services-basement-18.3.0.aar"
    }

    "each be opened with its classes, its rules where it ships any, and its resources where its classes read them" in {
      val repo = fresh("repo")
      val room = fresh("room").toFile
      val opened = AndroidArchive.chosen(resolved(repo)).map { case (m, aar) => AndroidArchive.open(m, aar, room) }
      opened.map(_.name) shouldBe Seq("media3-common-1.9.2", "media3-exoplayer-1.9.2", "work-runtime-2.12.0",
        "play-services-auth-21.2.0", "play-services-basement-18.9.0")
      opened.foreach { a =>
        a.classes.get.isFile shouldBe true
        a.classes.get.getParentFile.getParentFile shouldBe room
      }
      classNames(opened.head.classes.get) shouldBe
        Set("androidx/media3/common/A.class", "androidx/media3/common/B.class")
      opened.map(_.rules.isDefined) shouldBe Seq(true, false, true, true, true)
      new String(Files.readAllBytes(opened.head.rules.get.toPath), "UTF-8") should include("androidx.media3.common")
      opened.map(_.resources.map(_.packageName)) shouldBe Seq(None, None, Some("androidx.work"), None, None)
      opened(2).resources.get.res.toPath.resolve("values/values.xml").toFile.isFile shouldBe true
      // a second time, from the same entries, the same
      AndroidArchive.chosen(resolved(fresh("repo"))).map { case (m, aar) => AndroidArchive.open(m, aar, room) }
        .map(_.classes) shouldBe opened.map(_.classes)
    }
  }

  "What R8 is given" should {
    "hold each archive's classes once, whatever copies of them the program has, and its rules once" in {
      val repo = fresh("repo")
      val room = fresh("room").toFile
      val opened = AndroidArchive.chosen(resolved(repo)).map { case (m, aar) => AndroidArchive.open(m, aar, room) }
      val app = fresh("app")
      val own = write(app, "app.jar", zip(Seq("com/example/Main.class" -> cls("com/example/Main"))))
      // the classpath has the plugin's media3-common; the app opened it again elsewhere, and zipped its own copy of
      // the basement's classes, byte for byte but in another jar
      val common = opened.head.classes.get
      val again = write(app, "media3-common-1.9.2.jar", Files.readAllBytes(common.toPath))
      val rezipped = write(app, "basement-copy.jar",
        zip(entries("com.google.android.gms.common", "18.9.0", readsR = false).reverse))
      val dexed = AndroidArchive.dexed(Seq(own, common, again, rezipped), opened)
      dexed shouldBe Seq(own, common, rezipped) ++ opened.slice(1, 4).flatMap(_.classes)
      dexed.flatMap(f => classNames(f).toSeq).groupBy(identity).values.map(_.size).max shouldBe 1
      // without a copy in the program, every archive's classes are added
      AndroidArchive.dexed(Seq(own), opened) shouldBe own +: opened.flatMap(_.classes)
      // the rules: the app's own, then each archive's once, a copy of one the app passes among them left out
      val copied = write(app, "media3.pro", Files.readAllBytes(opened.head.rules.get.toPath))
      val appRules = write(app, "proguard-rules.pro", "-keep class com.example.Main { *; }\n".getBytes("UTF-8"))
      AndroidArchive.rules(Seq(appRules, copied), opened) shouldBe
        Seq(appRules, copied) ++ opened.drop(1).flatMap(_.rules)
    }

    "stop, naming the archive and the jar, where the program holds another version of its classes" in {
      val repo = fresh("repo")
      val room = fresh("room").toFile
      val opened = AndroidArchive.chosen(resolved(repo)).map { case (m, aar) => AndroidArchive.open(m, aar, room) }
      val app = fresh("app")
      val old = write(app, "media3-common-1.8.0.jar", classes("androidx.media3.common", "1.8.0", readsR = false))
      val wrong = the[IllegalStateException] thrownBy AndroidArchive.dexed(Seq(old), opened)
      wrong.getMessage should include("media3-common-1.8.0.jar holds 2 of the 2 classes of the Android archive " +
        "androidx.media3:media3-common (media3-common-1.9.2.aar), not byte for byte")
      wrong.getMessage should include("everyuiAndroidLibraries")
      // some of its classes, not all
      val part = write(app, "part.jar", zip(Seq("androidx/media3/exoplayer/A.class" -> cls("x"))))
      (the[IllegalStateException] thrownBy AndroidArchive.dexed(Seq(part), opened)).getMessage should include(
        "part.jar holds 1 of the 2 classes of the Android archive androidx.media3:media3-exoplayer")
    }
  }

  "A version" should {
    "be later by its numbers, then its text, a release after its pre-release" in {
      AndroidArchive.newer("1.10.0", "1.9.2") shouldBe true
      AndroidArchive.newer("1.9.2", "1.10.0") shouldBe false
      AndroidArchive.newer("18.9.0", "18.3.0") shouldBe true
      AndroidArchive.newer("1.0.0", "1.0.0-rc1") shouldBe true
      AndroidArchive.newer("1.0.0-rc1", "1.0.0") shouldBe false
      AndroidArchive.newer("1.0.0.1", "1.0.0") shouldBe true
      AndroidArchive.newer("1.0.0", "1.0.0") shouldBe false
      AndroidArchive.newer("1.0.0-rc2", "1.0.0-rc1") shouldBe true
    }
  }
}

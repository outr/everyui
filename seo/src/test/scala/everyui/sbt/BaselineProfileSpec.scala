package everyui.sbt

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** An APK's baseline profile (BaselineProfile, Profgen, ProfileRetrace, AarCache.profile): the rules of EveryUI, the
  * archives and the app merged, renamed by R8 with its mapping, compiled by profgen against the dex, stored
  * uncompressed and aligned where profileinstaller and ART read them; rules read from a device named as in the source
  * again through the mapping. */
class BaselineProfileSpec extends AnyWordSpec with Matchers {
  private val work = Files.createDirectories(new File("target/baseline-profile-spec").getAbsoluteFile.toPath)
  private def fresh(label: String): Path = Files.createDirectories(work.resolve(s"$label-${System.nanoTime()}"))
  private def write(p: Path, text: String): File = { Files.createDirectories(p.getParent); Files.writeString(p, text).toFile }

  private val sdk = new File(sys.env.getOrElse("ANDROID_HOME", sys.props("user.home") + "/opt/android-sdk"))
  private val buildTools = Option(new File(sdk, "build-tools").listFiles()).toSeq.flatten
    .filter(d => new File(d, "lib/d8.jar").isFile && new File(d, "zipalign").isFile)
    .sortWith((a, b) => AndroidArchive.newer(a.getName, b.getName)).headOption
  private val androidJar = Option(new File(sdk, "platforms").listFiles()).toSeq.flatten
    .map(new File(_, "android.jar")).filter(_.isFile).sortBy(_.getParentFile.getName).lastOption

  private def zip(file: File, entries: Seq[(String, Array[Byte])], stored: Set[String] = Set.empty): File = {
    val out = new ZipOutputStream(new FileOutputStream(file))
    try entries.foreach { case (name, bytes) =>
      if (stored(name)) {
        val f = Files.createTempFile(work, "entry", ".bin").toFile
        Files.write(f.toPath, bytes)
        BaselineProfile.store(out, name, f)
      } else {
        out.putNextEntry(new ZipEntry(name))
        out.write(bytes)
        out.closeEntry()
      }
    } finally out.close()
    file
  }

  private def aligned(in: File): File = {
    val out = new File(in.getPath + ".aligned.apk")
    sys.process.Process(Seq(new File(buildTools.get, "zipalign").toString, "-f", "-p", "4", in.toString, out.toString)).! shouldBe 0
    out
  }

  private val profiles = Seq(BaselineProfile.prof -> "pro\u0000010\u0000rest".getBytes(UTF_8),
    BaselineProfile.profm -> "prm\u0000002\u0000rest".getBytes(UTF_8), BaselineProfile.art -> "pro\u0000015\u0000rest".getBytes(UTF_8))

  "merge" should {
    "keep each rule once, in the order first given, without comments, blank lines or missing files" in {
      val dir = fresh("merge")
      val a = write(dir.resolve("a.txt"), "# EveryUI\nHSPLa/B;->c()V\n\n  La/B;  \n")
      val b = write(dir.resolve("b.txt"), "La/B;\nHSPLd/E;->f(I)I\n# the app\n")
      val out = dir.resolve("merged.txt").toFile
      BaselineProfile.merge(Seq(a, new File(dir.toFile, "missing.txt"), b), out) shouldBe 3
      Files.readString(out.toPath) shouldBe "HSPLa/B;->c()V\nLa/B;\nHSPLd/E;->f(I)I\n"
    }
  }

  "onClasspath" should {
    "find EveryUI's rules in a jar and in a class directory, and nothing elsewhere" in {
      val dir = fresh("classpath")
      val jar = zip(dir.resolve("everyui-android_3-0.1.25.jar").toFile, Seq(BaselineProfile.inJar -> "La/B;\n".getBytes(UTF_8)))
      val other = zip(dir.resolve("other.jar").toFile, Seq("x/Y.class" -> Array[Byte](1, 2)))
      val classes = dir.resolve("classes")
      write(classes.resolve(BaselineProfile.inJar), "Lc/D;\n")
      val found = BaselineProfile.onClasspath(Seq(jar, other, classes.toFile, dir.resolve("gone.jar").toFile), dir.resolve("out").toFile)
      found.map(f => Files.readString(f.toPath)) shouldBe Seq("La/B;\n", "Lc/D;\n")
    }
  }

  "AarCache.profile" should {
    "keep an archive's baseline-prof.txt beside its entry, and give None for one that ships none" in {
      val dir = fresh("aar")
      val room = dir.resolve("room").toFile
      val withRules = zip(dir.resolve("lib-1.0.aar").toFile, Seq("classes.jar" -> Array[Byte](1), "baseline-prof.txt" -> "HSPLx/Y;->z()V\n".getBytes(UTF_8)))
      val without = zip(dir.resolve("bare-1.0.aar").toFile, Seq("classes.jar" -> Array[Byte](2)))
      val kept = AarCache.profile(withRules, room)
      kept.map(f => Files.readString(f.toPath)) shouldBe Some("HSPLx/Y;->z()V\n")
      kept.get.getName shouldBe AarCache.entry(withRules, room).getName + ".baseline-prof.txt"
      AarCache.profile(withRules, room) shouldBe kept
      AarCache.profile(without, room) shouldBe None
      room.list().filter(_.startsWith(".")) shouldBe empty
    }
  }

  "ProfileRetrace" should {
    val mapping = """# compiler: R8
      |# pg_map_id: 1234abcd
      |everyui.render.Host -> a.b:
      |# {"id":"sourceFile","fileName":"Host.scala"}
      |    int width -> a
      |    1:4:void everyui.render.Text.measure(java.lang.String):10:13 -> c
      |    1:4:void layout(everyui.render.Host,int[]):20 -> c
      |    5:9:void layout(everyui.render.Host,int[]):21:25 -> c
      |    1:3:java.lang.String name$default(everyui.render.Host,int,java.lang.Object):30 -> d
      |      # {"id":"com.android.tools.r8.residualsignature","signature":"(La/b;)Ljava/lang/String;"}
      |    4:6:java.lang.String name$default(everyui.render.Host,int,java.lang.Object):31 -> d
      |    7:9:int com.google.Math.log2(int,java.math.RoundingMode):118:118 -> e
      |    7:9:int _BOUNDARY.log2(int):0 -> e
      |      # {"id":"com.android.tools.r8.synthesized"}
      |    void <init>() -> <init>
      |everyui.render.Host$$ExternalSyntheticLambda0 -> a.c:
      |# {"id":"com.android.tools.r8.synthesized"}
      |    void run() -> run
      |everyui.render.Text -> a.d:
      |""".stripMargin.linesIterator.toSeq
    val retrace = new ProfileRetrace(mapping)

    "name classes and methods as in the source, types in their signatures too" in {
      retrace("La/b;") shouldBe Some("Leveryui/render/Host;")
      retrace("HSPLa/b;->c(La/b;[I)V") shouldBe Some("HSPLeveryui/render/Host;->layout(Leveryui/render/Host;[I)V")
      retrace("HSLa/b;-><init>()V") shouldBe Some("HSLeveryui/render/Host;-><init>()V")
    }
    "take R8's noted signature where it changed a method's parameters" in {
      retrace("PLa/b;->d(La/b;)Ljava/lang/String;") shouldBe
        Some("PLeveryui/render/Host;->name$default(Leveryui/render/Host;ILjava/lang/Object;)Ljava/lang/String;")
    }
    "take what R8 folded a method into as its own, past a frame R8 made" in {
      retrace("HSPLa/b;->e(I)I") shouldBe Some("HSPLcom/google/Math;->log2(ILjava/math/RoundingMode;)I")
    }
    "leave out classes R8 made, and keep names it did not change" in {
      retrace("HSPLa/c;->run()V") shouldBe None
      retrace("La/c;") shouldBe None
      retrace("HSPLandroid/view/View;->draw(La/d;)V") shouldBe Some("HSPLandroid/view/View;->draw(Leveryui/render/Text;)V")
      retrace.all(Seq("La/b;", "", "La/c;", "La/b;")) shouldBe Seq("Leveryui/render/Host;")
    }
    "read a mapping kept gzipped" in {
      val f = fresh("gz").resolve("1234abcd-v1.txt.gz").toFile
      val out = new java.util.zip.GZIPOutputStream(new FileOutputStream(f))
      out.write(mapping.mkString("\n").getBytes(UTF_8))
      out.close()
      ProfileRetrace(f)("La/d;") shouldBe Some("Leveryui/render/Text;")
    }
  }

  "ProfileCollector" should {
    "find the mapping of an APK's build by the map id its dex carries, and name a rule's class" in {
      val dir = fresh("collector")
      val apk = zip(dir.resolve("app.apk").toFile, Seq("classes.dex" ->
        "dex\n035\u0000~~R8{\"min-api\":23,\"pg-map-id\":\"1234abcd\",\"r8-mode\":\"full\"}".getBytes(UTF_8)))
      Files.createDirectories(dir.resolve("mappings"))
      write(dir.resolve("mappings/99ff-v1.txt.gz"), "")
      val kept = write(dir.resolve("mappings/1234abcd-v7.txt.gz"), "")
      ProfileCollector.mapping(apk) shouldBe kept
      ProfileCollector.owner("HSPLeveryui/render/Host;->a()V") shouldBe "everyui/render/Host"
      ProfileCollector.owner("Lreactify/Var;") shouldBe "reactify/Var"
    }
  }

  "problem" should {
    "pass an APK holding all three profiles stored and aligned" in {
      assume(buildTools.nonEmpty, "the Android SDK's build-tools")
      val dir = fresh("good")
      val apk = aligned(zip(dir.resolve("app.apk").toFile, Seq("AndroidManifest.xml" -> Array.fill(37)(1.toByte)) ++
        profiles, profiles.map(_._1).toSet))
      BaselineProfile.problem(apk) shouldBe None
    }
    "fail one missing a profile, compressing one, or holding one of the wrong version" in {
      val dir = fresh("bad")
      BaselineProfile.problem(zip(dir.resolve("missing.apk").toFile, profiles.take(2), profiles.map(_._1).toSet)).get should
        include("has no " + BaselineProfile.art)
      BaselineProfile.problem(zip(dir.resolve("compressed.apk").toFile, profiles, Set(BaselineProfile.prof, BaselineProfile.art))).get should
        include(BaselineProfile.profm + " is compressed")
      val old = profiles.map { case (n, b) => if (n == BaselineProfile.art) n -> profiles.head._2 else n -> b }
      BaselineProfile.problem(zip(dir.resolve("version.apk").toFile, old, profiles.map(_._1).toSet)).get should
        include(BaselineProfile.art + " in")
    }
    "fail one whose profile is not on a 4-byte boundary" in {
      val dir = fresh("unaligned")
      // a name whose length puts the first stored entry's bytes at 30 + 21 = 51
      val apk = zip(dir.resolve("app.apk").toFile, Seq("a-name-of-21-letters!" -> Array[Byte](1)) ++ profiles,
        profiles.map(_._1).toSet + "a-name-of-21-letters!")
      BaselineProfile.problem(apk).get should include("4-byte boundary")
    }
  }

  "AndroidApk's profile, end to end" should {
    "have R8 rename the rules with its mapping and profgen compile them against the dex" in {
      assume(buildTools.nonEmpty && androidJar.nonEmpty, "the Android SDK's build-tools and a platform")
      val dir = fresh("e2e")
      val src = dir.resolve("src")
      write(src.resolve("demo/Main.java"),
        "package demo; public class Main { public static void main(String[] a) { System.out.println(new Worker().work(a.length)); } }")
      write(src.resolve("demo/Worker.java"),
        "package demo; public class Worker { public int work(int n) { int s = 0; for (int i = 0; i < n; i++) s += i * i; return s; } }")
      val classes = Files.createDirectories(dir.resolve("classes"))
      javax.tools.ToolProvider.getSystemJavaCompiler.run(null, null, null, "--release", "17", "-d", classes.toString,
        src.resolve("demo/Main.java").toString, src.resolve("demo/Worker.java").toString) shouldBe 0
      val jar = dir.resolve("program.jar").toFile
      zip(jar, Seq("demo/Main.class", "demo/Worker.class").map(n => n -> Files.readAllBytes(classes.resolve(n))))
      val rules = write(dir.resolve("rules.pro"), "-keep class demo.Main { public static void main(java.lang.String[]); }\n-dontoptimize\n")
      val profile = write(dir.resolve("baseline-prof.txt"), "HSPLdemo/Worker;->work(I)I\nLdemo/Worker;\n")
      val renamed = dir.resolve("baseline-prof-r8.txt").toFile
      val dex = Files.createDirectories(dir.resolve("dex"))
      val mapping = dir.resolve("mapping.txt").toFile
      sys.process.Process(Seq("java", "-cp", new File(buildTools.get, "lib/d8.jar").toString, "com.android.tools.r8.R8",
        "--release", "--min-api", "23", "--lib", androidJar.get.toString, "--pg-conf", rules.toString,
        "--art-profile", profile.toString, renamed.toString, "--pg-map-output", mapping.toString,
        "--output", dex.toString, jar.toString)).! shouldBe 0

      // the rules as renamed name the class and method by their names in the mapping
      val worker = Files.readAllLines(mapping.toPath).toArray.map(_.toString).find(_.startsWith("demo.Worker -> ")).get
        .stripPrefix("demo.Worker -> ").stripSuffix(":").replace('.', '/')
      worker should not be "demo/Worker"
      val lines = Files.readAllLines(renamed.toPath).toArray.map(_.toString).toSeq
      lines should contain(s"L$worker;")
      lines.exists(_.startsWith(s"HSPL$worker;->")) shouldBe true
      // and read back through the mapping, they are the rules given
      ProfileRetrace(mapping).all(lines).toSet shouldBe Set("HSPLdemo/Worker;->work(I)I", "Ldemo/Worker;")

      val prof = dir.resolve("out/baseline.prof").toFile
      val profm = dir.resolve("out/baseline.profm").toFile
      val art = dir.resolve("out/art.prof").toFile
      val classpath = Profgen.classpath(_ => ())
      Profgen.compile(classpath, renamed, BaselineProfile.dexOrder(dex.toFile.listFiles().filter(_.getName.endsWith(".dex")).toSeq),
        prof, profm, art) shouldBe empty
      // and with no rules, for the size of a profile that holds nothing
      val none = write(dir.resolve("none.txt"), "")
      val emptyProf = dir.resolve("out/empty.prof").toFile
      Profgen.compile(classpath, none, Seq(dex.resolve("classes.dex").toFile), emptyProf, dir.resolve("out/empty.profm").toFile,
        dir.resolve("out/empty-art.prof").toFile)
      prof.length should be > emptyProf.length

      // stored and aligned in an APK as AndroidApk.build stores them
      val apk = aligned(zip(dir.resolve("app.apk").toFile, Seq("classes.dex" -> Files.readAllBytes(dex.resolve("classes.dex"))) ++
        BaselineProfile.entries(prof, profm, art).map { case (n, f) => n -> Files.readAllBytes(f.toPath) },
        Set(BaselineProfile.prof, BaselineProfile.profm, BaselineProfile.art)))
      BaselineProfile.problem(apk) shouldBe None
      val z = new ZipFile(apk)
      try BaselineProfile.entries(prof, profm, art).foreach { case (n, _) => z.getEntry(n).getMethod shouldBe ZipEntry.STORED }
      finally z.close()
    }
  }
}

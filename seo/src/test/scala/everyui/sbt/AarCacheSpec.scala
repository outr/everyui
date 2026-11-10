package everyui.sbt

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

/** The machine's shared cache of opened Android archives (AarCache), filled by many threads and processes at once:
  * nobody reading it finds an entry missing a file or a file cut short, every entry is the archive's parts whole, an
  * entry once there is never written again, and nothing half-opened is left behind but what is an hour stale. */
class AarCacheSpec extends AnyWordSpec with Matchers {
  private val work = Files.createDirectories(new File("target/aar-cache-spec").getAbsoluteFile.toPath)
  private val random = new java.util.Random(7)

  private def bytes(n: Int): Array[Byte] = { val b = new Array[Byte](n); random.nextBytes(b); b }

  private def zip(entries: Seq[(String, Array[Byte])]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val z = new ZipOutputStream(out)
    entries.foreach { case (name, data) => z.putNextEntry(new ZipEntry(name)); z.write(data); z.closeEntry() }
    z.close()
    out.toByteArray
  }

  // an archive with a classes jar of `size` bytes and more, rules unless told not to, and resources
  private def archive(dir: Path, name: String, size: Int, rules: Boolean = true): File = {
    val classes = zip(Seq("a/A.class" -> bytes(size), "a/B.class" -> bytes(size / 3)))
    val parts = Seq("AndroidManifest.xml" -> s"""<manifest package="a.$name"/>""".getBytes("UTF-8"),
      "classes.jar" -> classes) ++
      (if (rules) Seq("proguard.txt" -> s"-keep class a.$name.** { *; }\n".getBytes("UTF-8")) else Nil) ++
      Seq("res/values/values.xml" -> "<resources/>".getBytes("UTF-8"), "res/drawable/icon.png" -> bytes(size / 2),
        "R.txt" -> "int bool x 0x0".getBytes("UTF-8"))
    val file = dir.resolve(s"$name.aar")
    Files.write(file, zip(parts))
    file.toFile
  }

  private def fresh(label: String): Path = {
    val dir = work.resolve(s"$label-${System.nanoTime()}")
    Files.createDirectories(dir)
  }

  private def entryOf(aar: File, name: String): Option[Array[Byte]] = {
    val z = new ZipFile(aar)
    try Option(z.getEntry(name)).map(e => z.getInputStream(e).readAllBytes()) finally z.close()
  }

  // every file under the room's entries, by path, with its inode, its time and its bytes
  private def snapshot(room: Path): Map[String, (AnyRef, Long, Seq[Byte])] = {
    val all = Files.walk(room)
    try all.iterator().asScala.filter(Files.isRegularFile(_)).map { p =>
      room.relativize(p).toString -> (Files.getAttribute(p, "unix:ino"), Files.getLastModifiedTime(p).toMillis,
        Files.readAllBytes(p).toSeq)
    }.toMap
    finally all.close()
  }

  // what each archive's entry must hold, checked whole
  private def complete(aar: File, room: File): Unit = {
    val entry = AarCache.entry(aar, room)
    entry.isDirectory shouldBe true
    Files.readAllBytes(AarCache.classes(aar, room).toPath) shouldBe entryOf(aar, "classes.jar").get
    entryOf(aar, "proguard.txt") match {
      case Some(rules) => Files.readAllBytes(AarCache.rules(aar, room).toPath) shouldBe rules
      case None => AarCache.rules(aar, room).exists shouldBe false
    }
    Files.readAllBytes(new File(AarCache.resources(aar, room), "drawable/icon.png").toPath) shouldBe
      entryOf(aar, "res/drawable/icon.png").get
    new File(AarCache.resources(aar, room), "values/values.xml").isFile shouldBe true
    entry.list().toSet shouldBe (Set(AarCache.nameOf(aar) + ".jar", "res") ++
      entryOf(aar, "proguard.txt").map(_ => AarCache.nameOf(aar) + ".pro"))
  }

  // reads the room over and over while `running`, noting any entry seen without its files whole
  private def watch(archives: Seq[File], room: File, running: AtomicBoolean,
                    faults: ConcurrentLinkedQueue[String]): Thread = {
    val expected = archives.map(a => a -> entryOf(a, "classes.jar").get.length).toMap
    val t = new Thread(() => {
      var reads = 0
      while (running.get || reads == 0) {
        archives.foreach { a =>
          if (AarCache.entry(a, room).isDirectory) {
            val jar = AarCache.classes(a, room)
            if (!jar.isFile) faults.add(s"${jar.getName} missing")
            else if (jar.length != expected(a)) faults.add(s"${jar.getName} is ${jar.length} bytes")
            if (!new File(AarCache.resources(a, room), "values/values.xml").isFile) faults.add(s"${a.getName} res")
          }
        }
        reads += 1
      }
    })
    t.start()
    t
  }

  private def classpathOf(classes: Class[?]*): String =
    classes.map(c => new File(c.getProtectionDomain.getCodeSource.getLocation.toURI).getPath).distinct
      .mkString(File.pathSeparator)

  // processes of AarCacheProbe opening `archives` into `room` from `start`, each with `threads` threads
  private def probes(n: Int, room: File, start: Long, threads: Int, archives: Seq[File]): Seq[Process] = {
    val java = ProcessHandle.current().info().command().orElse("java")
    val cp = classpathOf(AarCacheProbe.getClass, AarCache.getClass, classOf[Option[?]],
      classOf[scala.deriving.Mirror])
    (1 to n).map { _ =>
      new ProcessBuilder((Seq(java, "-cp", cp, "everyui.sbt.AarCacheProbe", room.getPath, start.toString,
        threads.toString) ++ archives.map(_.getPath)).asJava).redirectErrorStream(true).start()
    }
  }

  private def finish(p: Process): Int = {
    val out = new String(p.getInputStream.readAllBytes(), "UTF-8")
    withClue(out)(p.waitFor() shouldBe 0)
    "opened=(\\d+)".r.findFirstMatchIn(out).map(_.group(1).toInt).getOrElse(fail(out))
  }

  // opens `archives` into `room` from threads here and from `processes` other JVMs at once; how many opened each
  private def race(archives: Seq[File], room: File, threads: Int, processes: Int,
                   faults: ConcurrentLinkedQueue[String]): Int = {
    archives.foreach(AarCache.digest)
    val start = System.currentTimeMillis() + (if (processes > 0) 2500 else 50)
    val others = probes(processes, room, start, threads, archives)
    val running = new AtomicBoolean(true)
    val watcher = watch(archives, room, running, faults)
    val opened = new java.util.concurrent.atomic.AtomicInteger
    val ready = new CountDownLatch(1)
    val workers = (1 to threads).map { _ =>
      val t = new Thread(() => {
        ready.await()
        while (System.currentTimeMillis() < start) Thread.onSpinWait()
        archives.foreach(a => if (AarCache.open(a, room)) opened.incrementAndGet())
      })
      t.start()
      t
    }
    ready.countDown()
    workers.foreach(_.join())
    val total = opened.get + others.map(finish).sum
    running.set(false)
    watcher.join()
    total
  }

  private def leftovers(room: File): Seq[String] = room.list().toSeq.filter(_.startsWith("."))

  "Opening archives into the shared cache" should {
    "give every entry whole, once, to many threads at once, with no reader finding a file missing or cut short" in {
      val dir = fresh("threads")
      val archives = (1 to 6).map(i => archive(dir, s"lib$i-1.0.$i", 400000 + i * 50000, rules = i % 3 != 0))
      (1 to 5).foreach { round =>
        val room = dir.resolve(s"room$round").toFile
        val faults = new ConcurrentLinkedQueue[String]
        race(archives, room, 12, 0, faults) shouldBe archives.size
        faults.asScala.toList shouldBe Nil
        archives.foreach(complete(_, room))
        leftovers(room) shouldBe Nil
      }
    }
    "give every entry whole, once, to several processes and threads at once" in {
      val dir = fresh("processes")
      val archives = (1 to 6).map(i => archive(dir, s"lib$i-2.0.$i", 600000 + i * 50000, rules = i % 2 == 0))
      (1 to 3).foreach { round =>
        val room = dir.resolve(s"room$round").toFile
        val faults = new ConcurrentLinkedQueue[String]
        race(archives, room, 6, 3, faults) shouldBe archives.size
        faults.asScala.toList shouldBe Nil
        archives.foreach(complete(_, room))
        leftovers(room) shouldBe Nil
      }
    }
    "leave an entry that is there untouched, its files' inodes, times and bytes as they were" in {
      val dir = fresh("again")
      val archives = (1 to 4).map(i => archive(dir, s"lib$i-3.0.$i", 200000, rules = i != 2))
      val room = dir.resolve("room").toFile
      archives.map(AarCache.open(_, room)) shouldBe Seq.fill(4)(true)
      val before = snapshot(room.toPath)
      before.size shouldBe 4 * 3 + 3
      // an archive whose own time moves on (downloaded again, the same bytes) is the same entry
      archives.foreach(a => a.setLastModified(System.currentTimeMillis() + 60000))
      Thread.sleep(20)
      val faults = new ConcurrentLinkedQueue[String]
      race(archives, room, 4, 2, faults) shouldBe 0
      faults.asScala.toList shouldBe Nil
      snapshot(room.toPath) shouldBe before
      room.list().length shouldBe 4
    }
    "give an archive of the same name with other bytes an entry of its own, the first left as it was" in {
      val first = fresh("first")
      val second = fresh("second")
      val a = archive(first, "media-1.0", 100000)
      val b = archive(second, "media-1.0", 120000, rules = false)
      val room = fresh("room").toFile
      AarCache.open(a, room) shouldBe true
      val before = snapshot(room.toPath)
      AarCache.open(b, room) shouldBe true
      AarCache.entry(a, room) should not be AarCache.entry(b, room)
      complete(a, room)
      complete(b, room)
      snapshot(room.toPath).filter(_._1.startsWith(AarCache.entry(a, room).getName)) shouldBe before
    }
    "leave nothing behind where an archive cannot be opened" in {
      val dir = fresh("broken")
      val good = archive(dir, "good-1.0", 300000)
      val broken = dir.resolve("broken-1.0.aar").toFile
      // a zip cut off part way, as a download that stopped
      Files.write(broken.toPath, Files.readAllBytes(good.toPath).take(200000))
      val room = dir.resolve("room").toFile
      an[java.io.IOException] should be thrownBy AarCache.open(broken, room)
      room.list().toSeq shouldBe Nil
      AarCache.entry(broken, room).exists shouldBe false
    }
    "remove a half-opened copy an hour stale, never a fresh one, nor an entry however old" in {
      val dir = fresh("clean")
      val aar = archive(dir, "lib-4.0", 50000)
      val room = dir.resolve("room").toFile
      AarCache.open(aar, room) shouldBe true
      val old = System.currentTimeMillis() - AarCache.staleAfterMillis - 60000
      def copy(name: String, time: Long): File = {
        val d = new File(room, name)
        Files.createDirectories(d.toPath.resolve("res/values"))
        Files.write(d.toPath.resolve("res/values/values.xml"), "<resources/>".getBytes("UTF-8"))
        Files.walk(d.toPath).iterator().asScala.foreach(p => Files.setLastModifiedTime(p, FileTime.fromMillis(time)))
        d
      }
      val stale = copy(".lib-5.0-0123456789abcdef.tmp-1-ab", old)
      val earlier = copy("lib-6.0.res-opening-1a2b3c4d", old)
      val filling = copy(".lib-7.0-0123456789abcdef.tmp-2-cd", old)
      // one file written a moment ago: a build is filling it still
      Files.write(filling.toPath.resolve("res/values/more.xml"), "<resources/>".getBytes("UTF-8"))
      Files.walk(AarCache.entry(aar, room).toPath).iterator().asScala
        .foreach(p => Files.setLastModifiedTime(p, FileTime.fromMillis(old)))
      val kept = snapshot(AarCache.entry(aar, room).toPath)
      AarCache.clean(room)
      stale.exists shouldBe false
      earlier.exists shouldBe false
      filling.isDirectory shouldBe true
      snapshot(AarCache.entry(aar, room).toPath) shouldBe kept
      complete(aar, room)
    }
  }
}

package everyui.store

import everyui.FxSpec
import everyui.async.Task
import reactify.*

import java.util.concurrent.TimeUnit

/** A Var kept in the store: the kept value read at the start and applied, every later change written (once a turn),
  * the applied value not written back, a change made before the read answers left standing, a missing or unreadable
  * value leaving the default; then the same against the desktop's own files, across a new Var and a new process. */
class StoredSpec extends FxSpec {
  /** A binding whose read answers when the spec says, and the writes it made. */
  private class Harness[T](initial: T)(using codec: Codec[T]) {
    val v: Var[T] = Var(initial)
    var answer: Option[T] => Unit = _ => ()
    var written: List[T] = Nil
    var applied: Option[Option[T]] = None
    private val load = Task.async[Option[T]](done => answer = done)

    fx {
      Stored.bind[T](v, load, value => written = written :+ value).map(a => applied = Some(a)).start()
    }

    def answerWith(found: Option[T]): Unit = fx(answer(found))
  }

  private val space = "everyui-stored-spec"

  override def afterAll(): Unit = {
    fx(Store.clear(space).start())
    super.afterAll()
  }

  "A stored Var" should {
    "take the kept value when the read answers, and not write it back" in {
      val h = new Harness[String]("name")
      fx(h.v()) shouldBe "name"
      h.answerWith(Some("-size"))
      fx(h.v()) shouldBe "-size"
      fx(()) // a turn for anything posted
      h.written shouldBe Nil
      h.applied shouldBe Some(Some("-size"))
    }

    "write every change after, once a turn, with the value it ended on" in {
      val h = new Harness[Int](0)
      h.answerWith(Some(5))
      fx {
        h.v @= 6
        h.v @= 7
        h.v @= 8
      }
      fx(())
      h.written shouldBe List(8)
      fx(h.v @= 9)
      fx(())
      h.written shouldBe List(8, 9)
    }

    "keep a change made before the read answers, rather than the kept value" in {
      val h = new Harness[String]("name")
      fx(h.v @= "date")
      h.answerWith(Some("-size"))
      fx(h.v()) shouldBe "date"
      h.written shouldBe List("date")
      h.applied shouldBe Some(None)
    }

    "leave the default when nothing is kept" in {
      val h = new Harness[String]("name")
      h.answerWith(None)
      fx(h.v()) shouldBe "name"
      h.written shouldBe Nil
      fx(h.v @= "date")
      fx(())
      h.written shouldBe List("date")
    }

    "not write a change back to the value already kept" in {
      val h = new Harness[String]("name")
      h.answerWith(Some("size"))
      fx {
        h.v @= "date"
        h.v @= "size"
      }
      fx(())
      h.written shouldBe Nil
    }
  }

  "A stored Var on the desktop's own store" should {
    "come back in a new Var, and fall to the default once forgotten or cleared" in {
      fx(Store.clear(space).start())
      val first = fx(Stored(space, "sort", "name"))
      fx(first()) shouldBe "name"
      fx(first @= "-size")
      fx(())
      fx(Stored(space, "sort", "name")()) shouldBe "-size"

      fx(Store.delete(space, "sort").start())
      fx(Stored(space, "sort", "name")()) shouldBe "name"

      val count = fx(Stored(space, "count", 0))
      fx(count @= 3)
      fx(())
      fx(Stored(space, "count", 0)()) shouldBe 3
      fx(Store.clear(space).start())
      fx(Stored(space, "count", 0)()) shouldBe 0
    }

    "read a value it cannot decode as the default" in {
      fx(Store.put(space, "count", "three").start())
      fx(Stored(space, "count", 7)()) shouldBe 7
    }

    "keep an empty String by forgetting it, so it comes back as the default" in {
      fx(Store.put(space, "filter", "x").start())
      val filter = fx(Stored(space, "filter", ""))
      fx(filter()) shouldBe "x"
      fx(filter @= "")
      fx(())
      var kept: Option[Option[String]] = None
      fx(Store.get(space, "filter").map(k => kept = Some(k)).start())
      kept shouldBe Some(None)
    }

    "survive the process: one JVM writes, the next reads" in {
      // the test JVM loads the specs through a loader of its own, so its classpath is read off the loaders
      def classpath: String = {
        var urls: List[String] = Nil
        var loader = getClass.getClassLoader
        while (loader != null) {
          loader match {
            case u: java.net.URLClassLoader => urls = urls ++ u.getURLs.toList.map(url => new java.io.File(url.toURI).getPath)
            case _ => ()
          }
          loader = loader.getParent
        }
        (urls :+ System.getProperty("java.class.path")).mkString(java.io.File.pathSeparator)
      }
      def run(args: String*): String = {
        val java = System.getProperty("java.home") + "/bin/java"
        val command = Seq(java, "-cp", classpath, "everyui.store.StoredProbe") ++ args
        val process = new ProcessBuilder(command*).redirectErrorStream(true).start()
        val out = new String(process.getInputStream.readAllBytes(), "UTF-8")
        process.waitFor(60, TimeUnit.SECONDS) shouldBe true
        out.linesIterator.find(_.startsWith("stored:")).getOrElse(out)
      }
      run(space, "write", "-date") shouldBe "stored:-date"
      run(space, "read") shouldBe "stored:-date"
    }
  }
}

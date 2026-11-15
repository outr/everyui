package everyui.component

import everyui.{FxSpec, ui}

/** The count of components made and not yet disposed of (`Component.live`, `liveNow`, `liveByKind`), and the warning
  * for one taken out of its container and left neither disposed of nor put back (`Component.warnUndisposed`). */
class LiveCountSpec extends FxSpec {
  private var warned: List[String] = Nil
  private var forwarded: (String, String) => Unit = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      forwarded = everyui.scribe.forward
      everyui.scribe.forward = (level, message) => if (level == "warn" && message.startsWith("undisposed:"))
        warned = warned :+ message
    }
  }

  override def afterAll(): Unit = {
    fx {
      everyui.scribe.forward = forwarded
      Component.warnUndisposed @= false
      ui.children.clear()
    }
    super.afterAll()
  }

  private def column(n: Int): Container = {
    val c = new Container
    c.children ++= (1 to n).map(i => TextView("Line " + i.toString))
    c
  }

  // what is warned of once the turn (and the one after) is done
  private def warnings(f: => Unit): List[String] = {
    warned = Nil
    fx(f)
    fx(())
    fx(())
    warned
  }

  "The live count" should {
    "count each component made, and each disposed of" in {
      val before = fx(Component.liveNow)
      val c = fx(column(3))
      fx(Component.liveNow) shouldBe before + 4
      fx(c.dispose())
      fx(Component.liveNow) shouldBe before
      // disposed of again, it is not counted again
      fx(c.dispose())
      fx(Component.liveNow) shouldBe before
    }
    "settle `live` at the end of the turn" in {
      fx(Component.live.get)
      val (made, now) = fx((column(2), Component.liveNow))
      fx(())
      fx(Component.live()) shouldBe now
      fx(made.dispose())
      fx(())
      fx(Component.live()) shouldBe now - 3
    }
    "count by kind those made since kinds are counted" in {
      fx(Component.countKinds())
      val before = fx(Component.liveByKind())
      val c = fx(column(2))
      fx(())
      val after = fx(Component.liveByKind())
      after.getOrElse("TextView", 0) shouldBe before.getOrElse("TextView", 0) + 2
      after.getOrElse("Container", 0) shouldBe before.getOrElse("Container", 0) + 1
      fx(c.dispose())
      fx(Component.liveByKind().getOrElse("TextView", 0)) shouldBe before.getOrElse("TextView", 0)
    }
  }

  "The undisposed warning" should {
    "say nothing while it is off" in {
      val c = fx(column(1))
      warnings(c.children.clear()) shouldBe Nil
    }
    "tell of a child taken out and left, with its kind and where it was" in {
      fx(Component.warnUndisposed @= true)
      val c = fx(column(2))
      fx(ui.children += c)
      val gone = warnings(c.children -= c.children().head)
      gone.size shouldBe 1
      gone.head should include("TextView(Line 1) taken out of Container < ui")
      fx(ui.children -= c)
      fx(c.dispose())
    }
    "tell of each child left out of a list set anew, or cleared" in {
      val c = fx(column(3))
      warnings(c.children @= Vector(c.children().head)).size shouldBe 2
      warnings(c.children.clear()).size shouldBe 1
    }
    "say nothing of one disposed of, put in another container, held aside, or put back" in {
      val c = fx(column(4))
      val other = fx(new Container)
      val kids = fx(c.children())
      warnings {
        c.children -= kids(0)
        kids(0).dispose()
        c.children -= kids(1)
        other.children += kids(1)
        c.children -= Component.heldAside(kids(2))
        c.children -= kids(3)
        c.children += kids(3)
      } shouldBe Nil
      // nor of what a container disposed of takes apart with it
      warnings(other.dispose()) shouldBe Nil
    }
  }
}

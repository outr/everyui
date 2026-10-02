package everyui.kit

import everyui.chat.{Avatar, Participant}
import everyui.component.types.Semantics
import everyui.i18n.Direction
import everyui.list.{RichList, RichLook, RichRow, RichSection}
import everyui.motion.ReduceMotion
import everyui.render.{Host, Motion}
import everyui.task.Frames
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.stage.Stage
import reactify.*

/** The ring around somebody at work: made the first time an avatar is busy, turning while it is (on the platform's own
  * animation, asking the app for no frames) and stopped and hidden the moment it is not; standing still where less
  * motion is asked for; said to a pointer and a screen reader; on a rich list's busy rows only, as rows are reused while
  * the list scrolls; and around a tab's lead avatar. The window is shown (under Xvfb), since a scene in no shown window
  * runs no motion. */
class BusyRingSpec extends FxSpec {
  private var stage: Stage = null
  @volatile private var pulses = 0
  private val counter: Runnable = () => pulses += 1
  private val ada = Participant("p-ada", "Ada")

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      javafx.application.Platform.setImplicitExit(false)
      stage = new Stage()
      stage.setScene(Host.scene)
      stage.show()
      Host.scene.addPostLayoutPulseListener(counter)
    }
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Host.scene.removePostLayoutPulseListener(counter)
      stage.hide()
      stage.setScene(null)
    }
    super.afterAll()
  }

  private def pulsesIn(ms: Long): Int = {
    val before = pulses
    Thread.sleep(ms)
    pulses - before
  }

  /** Wait until no pulse has run for half a second, for up to five seconds. */
  private def settled(): Boolean = {
    val until = System.currentTimeMillis() + 5000L
    var still = pulsesIn(500L) == 0
    while (!still && System.currentTimeMillis() < until) still = pulsesIn(500L) == 0
    still
  }

  private def shown[C <: everyui.component.Component, T](c: C)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx(ui.children.clear())
  }

  // the ring's turning half, and how far it has turned
  private def spin(ring: BusyRing): everyui.component.Component = ring.childComponents.last
  private def turned(ring: BusyRing): Double = fx(spin(ring).node.fx.getRotate)

  "An avatar's busy ring" should {
    "be made only once it is busy, turn while it is, and stop and hide the moment it is not" in {
      val avatar = fx(new Avatar(ada, 32.0))
      shown(avatar) {
        val before = fx(Motion.running)
        fx(avatar.busyRing) shouldBe None
        fx(avatar.busy @= true)
        val ring = fx(avatar.busyRing).get
        fx(ring.visible()) shouldBe true
        fx(ring.turning()) shouldBe true
        fx(ring.size.width()) shouldBe 32.0 + 2.0 * ring.outset
        fx(ring.position.left()) shouldBe -ring.outset
        fx(Motion.running) shouldBe before + 1
        val first = turned(ring)
        Thread.sleep(200L)
        turned(ring) should not be first
        fx(avatar.busy @= false)
        fx(ring.visible()) shouldBe false
        fx(ring.turning()) shouldBe false
        fx(spin(ring).motion()) shouldBe Nil
        fx(Motion.running) shouldBe before
        // and the same ring again, not another
        fx(avatar.busy @= true)
        fx(avatar.busyRing) shouldBe Some(ring)
        fx(Motion.running) shouldBe before + 1
        fx(avatar.busy @= false)
      }
    }

    "stand still, whole, where less motion is asked for" in {
      val avatar = fx(new Avatar(ada, 32.0))
      shown(avatar) {
        val before = fx(Motion.running)
        fx(ReduceMotion.on @= true)
        try {
          fx(avatar.busy @= true)
          val ring = fx(avatar.busyRing).get
          fx(ring.visible()) shouldBe true
          fx(ring.turning()) shouldBe false
          fx(spin(ring).visible()) shouldBe false
          fx(Motion.running) shouldBe before
          // the whole ring lit, not faint under a turning half
          fx(ring.childComponents.head.borderColor()) shouldBe fx(Theme.accent())
          fx(ReduceMotion.on @= false)
          fx(ring.turning()) shouldBe true
          fx(ring.childComponents.head.borderColor()) shouldBe fx(Theme.accent().withAlpha(0.3))
          fx(Motion.running) shouldBe before + 1
          fx(ReduceMotion.on @= true)
          fx(Motion.running) shouldBe before
        }
        finally fx {
          ReduceMotion.on @= false
          avatar.busy @= false
        }
      }
    }

    "say what they are doing to a pointer and to a screen reader while busy, and nothing after" in {
      val avatar = fx(new Avatar(ada, 32.0))
      fx(avatar.tooltip()) shouldBe ""
      fx(avatar.semantics()) shouldBe Semantics.Plain
      fx {
        avatar.busyDetail @= "Running bash"
        avatar.busy @= true
      }
      fx(avatar.tooltip()) shouldBe "Running bash"
      fx(avatar.semantics()) shouldBe Semantics.image("Ada, busy, Running bash")
      fx(avatar.busyDetail @= "")
      fx(avatar.semantics()) shouldBe Semantics.image("Ada, busy")
      fx(avatar.busy @= false)
      fx(avatar.tooltip()) shouldBe ""
      fx(avatar.semantics()) shouldBe Semantics.Plain
    }

    "ask the app for no frames while it turns, and leave JavaFX idle once it stops" in {
      val avatar = fx(new Avatar(ada, 32.0))
      shown(avatar) {
        settled() shouldBe true
        var asked = List.empty[String]
        fx(avatar.busy @= true)
        Thread.sleep(150L)
        fx {
          Frames.watcher = (s: String) => asked = s :: asked
          Frames.watched = true
        }
        try {
          pulsesIn(400L) should be > 5
          fx(asked) shouldBe Nil
        }
        finally fx(Frames.watched = false)
        fx(avatar.busy @= false)
        settled() shouldBe true
      }
    }

    "leave JavaFX idle with a screen of avatars nobody is busy in" in {
      val faces = fx((0 until 20).map(i => new Avatar(Participant("p-" + i, "P" + i), 28.0)).toList)
      val box = fx(new everyui.component.Container { children ++= faces })
      shown(box) {
        settled() shouldBe true
        fx(faces.flatMap(_.busyRing)) shouldBe Nil
      }
    }
  }

  case class Chat(id: String, busy: Boolean, unread: Boolean = false)

  private def look(c: Chat): RichLook = RichLook(c.id, Some("snippet"), Some(Participant("p-" + c.id, c.id)),
    count = if (c.unread) 3 else 0, unread = c.unread, busy = c.busy, busyDetail = if (c.busy) "Running " + c.id else "")

  private def faceOf(row: RichRow[Chat]): Avatar = row.tallFace

  "A rich list's busy rows" should {
    "turn a ring on each busy row's avatar only, unread or not, and say it" in {
      val chats = Vector(Chat("a", busy = true, unread = true), Chat("b", busy = false), Chat("c", busy = true))
      val list = fx {
        val l = new RichList[Chat](Var(Vector(RichSection("all", "All", chats))), _.id, look, Var(Set.empty[String]))
        l.size.width @= 320.0
        l.size.height @= 400.0
        l
      }
      shown(list) {
        // each item's row, under the section's header (on the FX thread)
        def row(i: Int): RichRow[Chat] = list.list.rowAt(i).get.asInstanceOf[RichRow[Chat]]
        fx(faceOf(row(1)).busy()) shouldBe true
        fx(faceOf(row(1)).busyRing.exists(_.turning())) shouldBe true
        fx(faceOf(row(2)).busy()) shouldBe false
        fx(faceOf(row(2)).busyRing.exists(_.visible())) shouldBe false
        fx(faceOf(row(3)).busyRing.exists(_.turning())) shouldBe true
        // unread too: bold, its count shown, and the ring
        fx(row(1).title.font.weight()) shouldBe everyui.component.types.FontWeight.Bold
        fx(row(1).countText.content()) shouldBe "3"
        fx(faceOf(row(1)).tooltip()) shouldBe "Running a"
        fx(row(1).semantics().label) should endWith("unread, busy, Running a")
        fx(row(2).semantics().label) should not include "busy"
      }
    }

    "stop and start the ring as rows are reused down a long list, turning only those on the screen" in {
      val chats = Var((0 until 2000).toVector.map(i => Chat("c" + i, busy = i % 7 == 3)))
      val list = fx {
        val l = new RichList[Chat](Var(Vector(RichSection("all", "All", chats()))), _.id, look, Var(Set.empty[String]))
        l.size.width @= 320.0
        l.size.height @= 400.0
        l
      }
      val base = fx(Motion.running)
      shown(list) {
        def check(): Unit = fx {
          val rows = list.list.rows.collect { case r: RichRow[Chat] @unchecked => r }
          val placed = rows.filter(_.visible())
          placed.foreach { r =>
            val busy = r.item().flatMap(_.item).exists(_.busy)
            faceOf(r).busyRing.exists(_.turning()) shouldBe busy
            faceOf(r).busyRing.exists(_.visible()) shouldBe busy
          }
          // the rows put away turn nothing
          rows.filterNot(_.visible()).foreach(r => faceOf(r).busyRing.exists(_.turning()) shouldBe false)
          placed.count(r => r.item().flatMap(_.item).exists(_.busy)) should be > 0
        }
        check()
        (1 to 40).foreach { step =>
          fx(list.list.scrollTop(step * 1370.0))
          check()
        }
        fx(list.list.scrollTop(0.0))
        check()
        // what turns is what is busy on the screen, and nothing once nothing is
        val turning = fx(list.list.rows.collect { case r: RichRow[Chat] @unchecked => r }
          .count(r => r.visible() && r.item().flatMap(_.item).exists(_.busy)))
        fx(Motion.running) shouldBe base + turning
      }
      fx(Motion.running) shouldBe base
    }
  }

  "A tab's lead" should {
    "be an avatar in the ring's room that turns while the tab is busy, an icon, or nothing" in {
      val shownTab = Var("plan")
      val bar = fx(new TabBar(shownTab, 900.0, showOnFocus = false))
      val (plain, plan, file) = fx((bar.add("overview", "Overview"),
        bar.add("plan", "Plan", closable = true, lead = TabLead.Person(ada)),
        bar.add("main", "Main.scala", lead = TabLead.Icon(everyui.font.MaterialIcon.Description))))
      shown(bar) {
        fx(plain.leadView) shouldBe None
        fx(file.leadView.get.isInstanceOf[everyui.component.MaterialIconView]) shouldBe true
        val room = fx(plan.leadView.get)
        val face = fx(room.childComponents.head.asInstanceOf[Avatar])
        fx(room.size.width()) shouldBe TabItem.face + 2.0 * BusyRing.outset(TabItem.face)
        fx(face.position.left()) shouldBe BusyRing.outset(TabItem.face)
        val labelAt = fx(plan.childComponents.head.childComponents(1).position.left())
        fx(face.busyRing) shouldBe None
        val before = fx(Motion.running)
        fx {
          plan.busyDetail @= "Thinking"
          plan.busy @= true
        }
        fx(face.busyRing.exists(_.turning())) shouldBe true
        fx(face.tooltip()) shouldBe "Thinking"
        fx(Motion.running) shouldBe before + 1
        // the label stays where it was
        fx(plan.childComponents.head.childComponents(1).position.left()) shouldBe labelAt
        fx(plan.busy @= false)
        fx(face.busyRing.exists(_.visible())) shouldBe false
        fx(Motion.running) shouldBe before
        // and a tab with no avatar has no ring to turn
        fx(file.busy @= true)
        fx(Motion.running) shouldBe before
      }
    }

    "lead from the right, right to left" in {
      val shownTab = Var("plan")
      fx(Direction.set(true))
      try {
        val bar = fx(new TabBar(shownTab, 900.0, showOnFocus = false))
        val plan = fx(bar.add("plan", "Plan", lead = TabLead.Person(ada)))
        shown(bar) {
          val row = fx(plan.childComponents.head)
          val lead = fx(plan.leadView.get)
          val text = fx(row.childComponents(1))
          fx(lead.position.left()) should be > fx(text.position.left() + text.size.width())
        }
      }
      finally fx(Direction.set(false))
    }
  }
}

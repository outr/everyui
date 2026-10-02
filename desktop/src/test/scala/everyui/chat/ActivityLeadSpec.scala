package everyui.chat

import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Spinner
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A tool call's line led by an avatar or an icon, with its status on the lead's corner, and a body under its summary:
  * a bare line as it was; the ring while it runs and the badge once it has; the body shown, cut to its lines, whole
  * once opened (a click, Enter) though there are no details; each following its entry as it changes; read out;
  * and as rows are reused while a transcript scrolls, and right to left. */
class ActivityLeadSpec extends FxSpec {
  private val oracle = Participant("agent-oracle", "The Oracle")
  private val long = "Hello Oracle. Luma here. Can you help me create a new Persona? I would like one that " +
    "reviews pull requests the way our team does: it reads the diff, runs the tests, points out anything that breaks " +
    "the style guide in `CONTRIBUTING.md`, and writes its findings as **short** comments rather than one long essay."

  private def all(c: Component): Vector[Component] = c +: (c match {
    case k: Container => k.children().flatMap(all)
    case _ => Vector.empty
  })

  private def shown[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx {
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def view(a: ChatEntry.Activity, width: Double = 600.0, made: Option[Component] = None): ActivityView =
    fx(new ActivityView(a, width, () => made))

  // the box, the line in it, and the line's parts
  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  private def line(v: ActivityView): Container = box(v).children().head.asInstanceOf[Container]
  private def summary(v: ActivityView): TextView = line(v).children()(1).asInstanceOf[TextView]
  private def avatar(v: ActivityView): Avatar =
    v.leadView.toVector.flatMap(all).collectFirst { case a: Avatar => a }.get
  // the status badge on the lead's corner: what is read as the status
  private def badge(v: ActivityView): Container = v.leadView.toVector.flatMap(all).collectFirst {
    case c: Container if c.semantics().role == "img" && !c.isInstanceOf[Avatar] => c
  }.get
  private def badgeMark(v: ActivityView): MaterialIconView =
    badge(v).children().collectFirst { case m: MaterialIconView if !m.isInstanceOf[Spinner] => m }.get
  private def badgeSpinner(v: ActivityView): Option[Spinner] = badge(v).children().collectFirst { case s: Spinner => s }

  "A tool call's line" should {
    "with no lead and no body, be the line alone, as it was: the status mark, the summary, the chevron" in {
      val a = ChatEntry.activity("bare", "Ran the tests", ActivityStatus.Running)
      val v = view(a)
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        fx(box(v).children().size) shouldBe 2
        fx(line(v).children().map(_.size.width())).head shouldBe 16.0
        fx(line(v).children()(1).isInstanceOf[TextView]) shouldBe true
        fx(line(v).children()(2).size.width()) shouldBe 0.0
        fx(v.leadView) shouldBe None
        fx(v.bodyView) shouldBe None
        fx(box(v).size.height()) shouldBe fx(line(v).size.height() + 12.0)
        fx(v.size.height()) shouldBe fx(box(v).size.height() + 6.0)
        fx(box(v).size.width()) shouldBe fx(line(v).size.width() + 24.0)
        // the status mark follows it as before
        val spinner = fx(all(line(v).children().head).collectFirst { case s: Spinner => s }.get)
        fx(spinner.visible()) shouldBe true
        fx(a.status @= ActivityStatus.Failed)
        fx(spinner.visible()) shouldBe false
        fx(a.opens) shouldBe false
      }
    }

    "led by somebody, turn a ring around their face while it runs and show the status on its corner once it has" in {
      val a = ChatEntry.activity("msg", "Message to The Oracle", lead = ActivityLead.Person(oracle))
      val v = view(a)
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        fx(line(v).children().head eq v.leadView.get) shouldBe true
        fx(v.leadView.get.size.width()) shouldBe ActivityView.room
        val face = fx(avatar(v))
        fx(face.busy()) shouldBe true
        fx(face.busyRing.exists(_.visible())) shouldBe true
        fx(badge(v).visible()) shouldBe false
        // the summary in the middle of the face
        fx(summary(v).position.top() + summary(v).size.height() / 2.0) shouldBe (ActivityView.room / 2.0 +- 0.5)
        fx(a.status @= ActivityStatus.Succeeded)
        fx(face.busy()) shouldBe false
        fx(face.busyRing.exists(_.visible())) shouldBe false
        fx(badge(v).visible()) shouldBe true
        fx(badgeMark(v).icon()) shouldBe MaterialIcon.CheckCircle
        fx(badge(v).semantics().label) shouldBe ChatWords.succeeded
        fx(a.status @= ActivityStatus.Failed)
        fx(badgeMark(v).icon()) shouldBe MaterialIcon.Error
        fx(badge(v).semantics().label) shouldBe ChatWords.failed
        // on the face's bottom corner at the end edge
        fx(badge(v).position.left() + badge(v).size.width()) shouldBe ActivityView.room
        fx(badge(v).position.top() + badge(v).size.height()) shouldBe ActivityView.room
      }
    }

    "led by an icon, show it with a spinner on its corner while it runs, and the status once it has" in {
      val a = ChatEntry.activity("look", "Looked up “Creating Custom Persona”: nothing found",
        lead = ActivityLead.Icon(MaterialIcon.Search))
      val v = view(a)
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        val glyph = fx(v.leadView.get.asInstanceOf[Container].children().head.asInstanceOf[MaterialIconView])
        fx(glyph.icon()) shouldBe MaterialIcon.Search
        fx(v.leadView.toVector.flatMap(all).exists(_.isInstanceOf[Avatar])) shouldBe false
        fx(badge(v).visible()) shouldBe true
        fx(badgeSpinner(v).get.visible()) shouldBe true
        fx(badgeMark(v).visible()) shouldBe false
        fx(badge(v).semantics().label) shouldBe ChatWords.running
        fx(a.status @= ActivityStatus.Succeeded)
        fx(badgeSpinner(v).get.visible()) shouldBe false
        fx(badgeMark(v).visible()) shouldBe true
        fx(badgeMark(v).icon()) shouldBe MaterialIcon.CheckCircle
      }
    }

    "show a body under the summary, selectable, cut to its lines, and whole once opened" in {
      val a = ChatEntry.activity("msg", "Message to The Oracle", ActivityStatus.Succeeded,
        lead = ActivityLead.Person(oracle), body = long)
      val v = view(a)
      shown(v) {
        eventually(v.bodyShown > 0.0 && a.opens) shouldBe true
        val words = fx(v.bodyView.get)
        fx(words.selectable()) shouldBe true
        val one = fx(box(v).children()(1).asInstanceOf[Container].children().head.size.height())
        one should be > 10.0
        fx(words.size.height()) should be > one * 3.0 + 1.0
        fx(v.bodyShown) shouldBe (one * 3.0 +- 0.01)
        // it opens to be read whole, though it has no details, and closes again
        fx(line(v).children()(2).visible()) shouldBe true
        fx(a.expanded @= true)
        fx(v.bodyShown) shouldBe fx(words.size.height())
        fx(box(v).size.width()) shouldBe fx(Transcript.widest(600.0))
        fx(a.expanded @= false)
        fx(v.bodyShown) shouldBe (one * 3.0 +- 0.01)
        // under the summary, at its start, inside the box
        val clip = fx(box(v).children()(1))
        fx(clip.position.top()) shouldBe fx(6.0 + summary(v).position.top() + summary(v).size.height() + 2.0)
        fx(clip.position.left()) shouldBe fx(line(v).position.left() + summary(v).position.left())
        fx(box(v).size.height()) shouldBe fx(clip.position.top() + v.bodyShown + 6.0)
        // a short one is not cut and does not open
        val short = ChatEntry.activity("ls", "Ran a command", ActivityStatus.Succeeded,
          lead = ActivityLead.Icon(MaterialIcon.Terminal), body = "`ls -la backend/src`")
        val w = view(short)
        shown(w) {
          eventually(w.bodyShown > 0.0) shouldBe true
          fx(w.bodyShown) shouldBe fx(w.bodyView.get.size.height())
          fx(short.opens) shouldBe false
          fx(line(w).children()(2).visible()) shouldBe false
        }
        // the transcript's lines, where it says fewer
        val two = fx(new ActivityView(a, 600.0, () => None, lines = 2))
        shown(two) {
          eventually(two.bodyShown > 0.0) shouldBe true
          fx(two.bodyShown) shouldBe (one * 2.0 +- 0.01)
        }
      }
    }

    "follow its entry: a body arriving later, the status changing, a lead coming and going" in {
      val a = ChatEntry.activity("late", "Message to The Oracle")
      val v = view(a)
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        val bare = fx(v.size.height())
        fx(v.bodyView) shouldBe None
        fx(a.body @= "Are you there?")
        eventually(v.bodyShown > 0.0) shouldBe true
        fx(v.size.height()) should be > bare
        fx(box(v).children().size) shouldBe 3
        fx(a.body @= long)
        eventually(a.opens) shouldBe true
        // a lead taking the status mark's place
        fx(a.lead @= ActivityLead.Person(oracle))
        fx(avatar(v).busy()) shouldBe true
        fx(a.status @= ActivityStatus.Failed)
        fx(avatar(v).busy()) shouldBe false
        fx(badgeMark(v).icon()) shouldBe MaterialIcon.Error
        fx(a.lead @= ActivityLead.Icon(MaterialIcon.Terminal))
        fx(v.leadView.toVector.flatMap(all).exists(_.isInstanceOf[Avatar])) shouldBe false
        // and gone again, the line as it was
        fx(a.lead @= ActivityLead.None)
        fx(a.body @= "")
        fx(v.leadView) shouldBe None
        fx(line(v).children().head.size.width()) shouldBe 16.0
        fx(box(v).children().size) shouldBe 2
        eventually(v.size.height() == bare) shouldBe true
        fx(a.opens) shouldBe false
      }
    }

    "in a transcript, open with Enter where its body is cut short, though it has no details" in {
      val a = ChatEntry.activity("msg", "Message to The Oracle", ActivityStatus.Succeeded,
        lead = ActivityLead.Person(oracle), body = long)
      val short = ChatEntry.activity("ls", "Ran a command", ActivityStatus.Succeeded, body = "`ls`")
      val entries = Var(Vector[ChatEntry](a, short))
      val t = fx(new Transcript(entries))
      fx {
        t.dayMarkers @= false
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      shown(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        eventually(a.opens) shouldBe true
        fx(t.focus())
        fx(t.list.current @= Some("msg"))
        press(KeyCode.ENTER)
        fx(a.expanded()) shouldBe true
        press(KeyCode.ENTER)
        fx(a.expanded()) shouldBe false
        // one that shows whole has nothing to open
        fx(t.list.current @= Some("ls"))
        press(KeyCode.ENTER)
        fx(short.expanded()) shouldBe false
      }
    }

    "be read out with its body and status" in {
      val a = ChatEntry.activity("msg", "Message to The Oracle", lead = ActivityLead.Person(oracle), body = "Hello")
      val bare = ChatEntry.activity("bare", "Ran the tests", ActivityStatus.Succeeded)
      val entries = Var(Vector[ChatEntry](bare, a))
      val t = fx(new Transcript(entries))
      fx {
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      shown(t) {
        fx(t.said(bare)) shouldBe "Ran the tests, " + ChatWords.succeeded
        fx(t.said(a)) shouldBe "Message to The Oracle: Hello, " + ChatWords.running
        fx(a.status @= ActivityStatus.Failed)
        fx(t.said(a)) shouldBe "Message to The Oracle: Hello, " + ChatWords.failed
        val rows = fx(all(t.list).collect { case r: ChatRow => r })
        val failed = "Message to The Oracle: Hello, " + ChatWords.failed
        eventually(all(t.list).exists(_.semantics().label == failed)) shouldBe true
        rows should not be empty
      }
    }

    "in a transcript, start each reused row from its own entry: its lead, its body and its ring" in {
      val entries = Var((0 until 300).toVector.map { i =>
        ChatEntry.activity("a" + i, "Call " + i, if (i % 3 == 0) ActivityStatus.Running else ActivityStatus.Succeeded,
          lead = i % 4 match {
            case 0 => ActivityLead.Person(oracle)
            case 1 => ActivityLead.Icon(MaterialIcon.Terminal)
            case 2 => ActivityLead.Person(oracle)
            case _ => ActivityLead.None
          }, body = if (i % 5 == 0) "Body " + i else ""): ChatEntry
      })
      val t = fx(new Transcript(entries))
      fx {
        t.dayMarkers @= false
        t.size.width @= 600.0
        t.size.height @= 500.0
      }
      shown(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        def check(): Unit = {
          val views = fx(all(t.list).collect { case v: ActivityView => v })
          views should not be empty
          views.foreach { v =>
            val id = fx(v.kind).stripPrefix("ActivityView(").stripSuffix(")")
            val i = id.drop(1).toInt
            val a = fx(entries()(i).asInstanceOf[ChatEntry.Activity])
            val person = i % 4 == 0 || i % 4 == 2
            fx(v.leadView.isEmpty) shouldBe (i % 4 == 3)
            fx(v.bodyView.nonEmpty) shouldBe (i % 5 == 0)
            if (person) {
              fx(avatar(v).busy()) shouldBe (a.status() == ActivityStatus.Running)
              fx(avatar(v).busyRing.exists(_.visible())) shouldBe (a.status() == ActivityStatus.Running)
            }
          }
        }
        check()
        (1 to 30).foreach { step =>
          fx(t.list.scrollTop(step * 400.0))
          (1 to 2).foreach(_ => fx(t.list.refresh()))
          check()
        }
        // and settled while it shows, the rings stop
        fx(entries().foreach { case a: ChatEntry.Activity => a.status @= ActivityStatus.Succeeded; case _ => () })
        fx(t.list.scrollTop(0.0))
        (1 to 3).foreach(_ => fx(t.list.refresh()))
        check()
        fx(all(t.list).collect { case a: Avatar => a }.exists(_.busy())) shouldBe false
      }
    }

    "right to left, lead at the summary's right, the badge on the face's bottom left, the body under the summary" in {
      fx(Direction.set(true))
      try {
        val a = ChatEntry.activity("msg", "Message to The Oracle", ActivityStatus.Succeeded,
          lead = ActivityLead.Person(oracle), body = long)
        val v = view(a)
        shown(v) {
          eventually(v.bodyShown > 0.0) shouldBe true
          val lead = fx(v.leadView.get)
          fx(lead.position.left()) should be > fx(summary(v).position.left())
          fx(badge(v).position.left()) shouldBe 0.0
          // the box at the row's right, past the avatar's room
          fx(box(v).position.left() + box(v).size.width()) shouldBe (600.0 - Transcript.side - Transcript.lead)
          // the body's right edge under the summary's
          val clip = fx(box(v).children()(1))
          fx(clip.position.left() + clip.size.width()) shouldBe
            fx(line(v).position.left() + summary(v).position.left() + summary(v).size.width() +- 1.0)
        }
      } finally fx(Direction.set(false))
    }
  }
}

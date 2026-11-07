package everyui.chat

import everyui.Color
import everyui.component.{Component, Container, TextView}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import reactify.*

/** A transcript as narrow as a phone's (390 wide) on the desktop's real nodes: everybody else's avatar small at the
  * start of the name's line, every bubble, tool call, reasoning and card of the app's from just inside the side margin
  * and as wide as all but the margins, no room kept for the rails (drawn in the margin), mirrored right to left; the
  * estimate of a row as it then draws; a wide transcript as it was, and one made narrow and wide again following; and
  * "Jump to latest" a small round button in the bottom end corner, with the count on it, gone at the bottom. */
class TranscriptNarrowSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")
  private val red = Color.fromHex("#e5484d")
  private val phone = 390.0
  private val long = "A reply long enough to wrap onto several lines in a transcript as narrow as a phone's, so that " +
    "its bubble is as wide as a bubble may be there, and then some more words after that to be sure of it."

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  private def under[T](c: Component, pick: PartialFunction[Component, T]): List[T] = {
    val here = pick.lift(c).toList
    here ++ (c match {
      case k: Container => k.children().toList.flatMap(under(_, pick))
      case _ => Nil
    })
  }

  private def transcript(entries: Var[Vector[ChatEntry]], width: Double = phone, height: Double = 2000.0,
                         rails: Boolean = false): Transcript = {
    val t = fx(new Transcript(entries))
    fx {
      t.dayMarkers @= false
      t.rails @= rails
      t.size.width @= width
      t.size.height @= height
    }
    t
  }

  // (on the FX thread, or from it)
  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def row(t: Transcript, key: String): ChatRow =
    onFx(t.list.rowAt(t.list.indexOf(key)).get.asInstanceOf[ChatRow])

  // the left and right of what a row draws, across the transcript: a bubble, or a box (the view's own first child)
  private def span(t: Transcript, key: String): (Double, Double) = fx {
    val r = t.list.rowAt(t.list.indexOf(key)).get.asInstanceOf[ChatRow]
    val v = r.shown.get
    val c = r.message.map(_.bubble).getOrElse(v.asInstanceOf[Container].children().head)
    val left = v.position.left() + c.position.left()
    (left, left + c.size.width())
  }

  private def avatar(t: Transcript, key: String): Avatar = onFx(under(row(t, key), { case a: Avatar => a }).head)
  private def named(t: Transcript, key: String, name: String): TextView =
    onFx(under(row(t, key), { case v: TextView if v.content() == name => v }).head)

  private def card(): Component = new Container {
    size.width @= 600.0
    size.height @= 80.0
  }

  // somebody else's run of two, one's own, a tool call opened, reasoning opened, and a card as wide as the app makes it
  private def flow(): Vector[ChatEntry] = {
    val tool = ChatEntry.activity("a1", "Ran the test suite", ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("sbt test", "412 passed")))
    tool.expanded @= true
    val thought = ChatEntry.thinking("t1", 0L, "The release has two blockers.", Some(4000L))
    thought.expanded @= true
    Vector(
      ChatEntry.message("o1", ada, self = false, "Morning!"),
      ChatEntry.message("o2", ada, self = false, long),
      ChatEntry.message("s1", me, self = true, long),
      tool,
      thought,
      ChatEntry.custom("c1", () => card()),
      ChatEntry.message("o3", ada, self = false, "One more."))
  }

  // the same, each in a topic's rail
  private def railed(): Vector[ChatEntry] = flow().map { e =>
    e.rail @= Some(red)
    e
  }

  "A transcript as narrow as a phone's" should {
    "put everything in the flow just inside the side margin, as wide as all but the margins" in {
      val t = transcript(Var(flow()))
      onScreen(t) {
        settle(t)
        val inner = phone - Transcript.side * 2.0
        fx(t.widest()) shouldBe inner
        span(t, "o1")._1 shouldBe Transcript.side
        span(t, "o2") shouldBe ((Transcript.side, Transcript.side + inner))
        span(t, "s1") shouldBe ((Transcript.side, phone - Transcript.side))
        span(t, "a1") shouldBe ((Transcript.side, Transcript.side + inner))
        span(t, "t1") shouldBe ((Transcript.side, Transcript.side + inner))
        // the app's card where it starts; as wide as the app makes it (here wider than the transcript)
        span(t, "c1")._1 shouldBe Transcript.side
        span(t, "o3")._1 shouldBe Transcript.side
      }
    }

    "put a small avatar at the start of the name's line, the bubble under both, and answer a press on it" in {
      var pressed: List[AuthorPressed] = Nil
      val t = transcript(Var(flow()))
      fx(t.onAuthor @= Some((p: AuthorPressed) => pressed = pressed :+ p))
      onScreen(t) {
        settle(t)
        val face = avatar(t, "o1")
        val name = named(t, "o1", "Ada")
        fx(face.size.width()) shouldBe Transcript.smallAvatar
        fx(face.position.left()) shouldBe Transcript.side
        fx(name.parent().get.position.left()) shouldBe Transcript.side + Transcript.smallAvatar + Transcript.smallGap
        // on one line: their middles level, and the bubble under the taller
        val v = fx(row(t, "o1").message.get)
        fx(face.position.top() + face.size.height() / 2.0) shouldBe
          (fx(name.parent().get.position.top() + name.parent().get.size.height() / 2.0) +- 0.5)
        fx(v.bubble.position.top()) should be >= fx(face.position.top() + face.size.height())
        // the rest of the run, and one's own, have none
        fx(under(row(t, "o2"), { case a: Avatar => a })) shouldBe Nil
        fx(under(row(t, "s1"), { case a: Avatar => a })) shouldBe Nil
        fx(face.event.click @= PointerEvent(PointerKind.Click, face.absoluteLeft + 2.0, face.absoluteTop + 2.0))
        fx(name.event.click @= PointerEvent(PointerKind.Click, name.absoluteLeft + 2.0, name.absoluteTop + 2.0))
        pressed.map(p => (p.participant.id, p.messageId)) shouldBe List(("u-ada", Some("o1")), ("u-ada", Some("o1")))
      }
    }

    "keep no room for the rails, drawing them in the side margin" in {
      val t = transcript(Var(railed()), rails = true)
      onScreen(t) {
        settle(t)
        fx(t.gutter()) shouldBe 0.0
        List("o1", "o2", "s1", "a1", "t1", "c1").foreach { k =>
          fx(row(t, k).shown.get.position.left()) shouldBe 0.0
          fx(row(t, k).shown.get.size.width()) shouldBe phone
          fx(row(t, k).rail.map(_.position.left())) shouldBe Some(Transcript.railInset)
        }
        Transcript.railInset + Transcript.railWidth should be < Transcript.side
        span(t, "o2") shouldBe ((Transcript.side, phone - Transcript.side))
      }
    }

    "mirror it all right to left" in {
      fx(Direction.set(true))
      try {
        val t = transcript(Var(railed()), rails = true)
        onScreen(t) {
          settle(t)
          span(t, "o1")._2 shouldBe phone - Transcript.side
          span(t, "o2") shouldBe ((Transcript.side, phone - Transcript.side))
          span(t, "s1")._1 shouldBe Transcript.side
          span(t, "a1")._2 shouldBe phone - Transcript.side
          span(t, "t1")._2 shouldBe phone - Transcript.side
          span(t, "c1")._2 shouldBe phone - Transcript.side
          fx(avatar(t, "o1").position.left()) shouldBe phone - Transcript.side - Transcript.smallAvatar
          val header = fx(named(t, "o1", "Ada").parent().get)
          fx(header.position.left() + header.size.width()) shouldBe
            phone - Transcript.side - Transcript.smallAvatar - Transcript.smallGap
          fx(row(t, "o1").rail.map(_.position.left())) shouldBe Some(phone - Transcript.railInset - Transcript.railWidth)
        }
      } finally fx(Direction.set(false))
    }

    "estimate a message's row at no less than it draws, and a leading one near it" in {
      val entries = Var[Vector[ChatEntry]]((0 until 12).toVector.map(i => ChatEntry.message("n" + i, if (i % 3 == 0) me else ada,
        self = i % 3 == 0, if (i % 2 == 0) "Message " + i else long)))
      val t = transcript(entries)
      onScreen(t) {
        settle(t)
        entries().foreach { e =>
          val drawn = fx(t.list.heightAt(t.list.indexOf(e.id)))
          val guessed = Transcript.estimate(e, phone, 3)
          guessed should be >= drawn
        }
        // a leading one-line message nearly to the pixel
        val first = fx(t.list.heightAt(t.list.indexOf("n0")))
        Transcript.estimate(entries().head, phone, 3) shouldBe (first +- 8.0)
      }
    }
  }

  "A wide transcript" should {
    "be as it was, and follow the width across a phone's and back" in {
      val t = transcript(Var(flow()), width = 800.0, rails = true)
      onScreen(t) {
        settle(t)
        val inner = 800.0 - Transcript.railGutter
        fx(t.gutter()) shouldBe Transcript.railGutter
        fx(t.widest()) shouldBe inner * 0.75
        span(t, "o1")._1 shouldBe Transcript.railGutter + Transcript.side + Transcript.lead
        span(t, "a1")._1 shouldBe Transcript.railGutter + Transcript.side + Transcript.lead
        span(t, "c1")._1 shouldBe Transcript.railGutter + Transcript.side + Transcript.lead
        fx(avatar(t, "o1").size.width()) shouldBe Transcript.avatar
        fx(avatar(t, "o1").position.top()) shouldBe Transcript.groupGap
        fx(named(t, "o1", "Ada").parent().get.position.left()) shouldBe Transcript.side + Transcript.lead
        // narrowed to a phone's: the small avatar, the flow from the margin, no room for the rails
        fx(t.size.width @= phone)
        settle(t)
        fx(t.gutter()) shouldBe 0.0
        fx(avatar(t, "o1").size.width()) shouldBe Transcript.smallAvatar
        span(t, "o1")._1 shouldBe Transcript.side
        span(t, "a1")._1 shouldBe Transcript.side
        // and back
        fx(t.size.width @= 800.0)
        settle(t)
        fx(avatar(t, "o1").size.width()) shouldBe Transcript.avatar
        fx(under(row(t, "o1"), { case a: Avatar => a }).size) shouldBe 1
        span(t, "o1")._1 shouldBe Transcript.railGutter + Transcript.side + Transcript.lead
      }
    }
  }

  "Jump to latest" should {
    def talk(n: Int, from: Int = 0): Vector[ChatEntry] = (from until from + n).toVector.map(i =>
      ChatEntry.message("m" + i, if (i % 3 == 0) me else ada, self = i % 3 == 0, "Message " + i))

    def check(rtl: Boolean): Unit = {
      fx(Direction.set(rtl))
      try {
        val entries = Var(talk(60))
        val t = transcript(entries, height = 600.0)
        onScreen(t) {
          settle(t)
          fx(t.jump.visible()) shouldBe false
          fx(t.list.scrollBy(-900.0))
          fx(t.jump.visible()) shouldBe true
          // small and round, in the bottom end corner
          fx(t.jump.size.width()) shouldBe JumpButton.diameter
          fx(t.jump.size.height()) shouldBe JumpButton.diameter
          fx(t.jump.radius()) shouldBe JumpButton.diameter / 2.0
          fx(t.jump.position.left()) shouldBe (if (rtl) JumpButton.margin
            else phone - JumpButton.margin - JumpButton.diameter)
          fx(t.jump.position.top()) shouldBe 600.0 - JumpButton.margin - JumpButton.diameter
          fx(t.jump.label()) shouldBe "Jump to latest"
          fx(t.jump.semantics().label) shouldBe "Jump to latest"
          fx(t.jump.badge.visible()) shouldBe false
          // what arrives counted on its corner, toward the end edge
          fx(entries @= entries() ++ talk(3, 100))
          settle(t)
          fx(t.jump.badge.visible()) shouldBe true
          fx(t.jump.label()) shouldBe "Jump to latest, 3 new"
          fx(t.jump.semantics().label) shouldBe "Jump to latest, 3 new"
          val b = fx(t.jump.badge)
          if (rtl) fx(b.position.left()) should be < 0.0
          else fx(b.position.left() + b.size.width()) should be > JumpButton.diameter
          // its count drawn whole, though it hangs past the button's edge (which lets it): not held to the room there
          val count = fx(b.childComponents.collectFirst { case w: TextView => w }.get)
          fx(count.node.getText()) shouldBe "3"
          fx(count.size.width()) should be > 4.0
          // pressed: down at the newest, and gone, so the last row's own buttons are clear of it
          fx(t.jump.event.click @= PointerEvent(PointerKind.Click, t.jump.absoluteLeft + 20.0, t.jump.absoluteTop + 20.0))
          settle(t)
          fx(t.pin.pinned()) shouldBe true
          fx(t.jump.visible()) shouldBe false
          // as is scrolling to the bottom
          fx(t.list.scrollBy(-300.0))
          fx(t.jump.visible()) shouldBe true
          fx(t.list.scrollBy(10000.0))
          fx(t.jump.visible()) shouldBe false
        }
      } finally fx(Direction.set(false))
    }

    "be a small round button in the bottom end corner, with the count on it" in check(rtl = false)
    "sit in the bottom left corner right to left" in check(rtl = true)
  }
}

package everyui.chat

import everyui.component.Container
import everyui.component.types.Semantics
import everyui.i18n.Direction
import everyui.list.{RichList, RichLook, RichRow, RichSection}
import everyui.theme.Theme
import everyui.{Color, FxSpec, ui}
import reactify.*

/** Somebody's status on their avatar (`AvatarStatus`): set once for their id in `Avatar.statuses`, shown on every face
  * of theirs, made before or after, and gone as it leaves the map; one face's own over it; said by a pointer and to a
  * screen reader, with and without busy; on a rich list's rows and in what they say; a dot of its size and in its
  * place at every diameter, past the face's box and not cut by it; and the map let go of as a face is disposed of. */
class AvatarStatusSpec extends FxSpec {
  private val ada = Participant("p-ada", "Ada")
  private val bo = Participant("p-bo", "Bo")
  private val meeting = AvatarStatus(Color.fromHex("#e53935"), "In a meeting · until 3:00 PM")
  private val away = AvatarStatus(Color.fromHex("#f9a825"), "Away")

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Avatar.statuses @= Map.empty
    }
    offStage()
    super.afterAll()
  }

  private def shown[C <: everyui.component.Component, T](c: C)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx {
      ui.children.clear()
      c.dispose()
      Avatar.statuses @= Map.empty
    }
  }

  private def dotShown(face: Avatar): Boolean = face.statusMark.exists(_.visible())
  private def dotColour(face: Avatar): Color = face.statusMark.get.childComponents.head.backgroundColor()

  "Avatar.statuses" should {
    "show a person's status on their faces made before and after it is set, and drop it as it is cleared" in {
      val before = fx(new Avatar(ada, 32.0))
      val other = fx(new Avatar(bo, 32.0))
      val box = fx(new Container { children ++= List(before, other) })
      shown(box) {
        fx(before.status()) shouldBe None
        // no dot made until one shows
        fx(before.statusMark) shouldBe None
        fx(Avatar.statuses @= Map(ada.id -> meeting))
        fx(before.status()) shouldBe Some(meeting)
        fx(dotShown(before)) shouldBe true
        fx(dotColour(before)) shouldBe meeting.colour
        fx(other.status()) shouldBe None
        fx(other.statusMark) shouldBe None
        val after = fx(new Avatar(ada, 48.0))
        fx(box.children += after)
        fx(after.status()) shouldBe Some(meeting)
        fx(dotShown(after)) shouldBe true
        // changed: the colour follows
        fx(Avatar.statuses @= Map(ada.id -> away, bo.id -> meeting))
        fx(dotColour(before)) shouldBe away.colour
        fx(dotColour(after)) shouldBe away.colour
        fx(dotShown(other)) shouldBe true
        // cleared: gone from every face
        fx(Avatar.statuses @= Map.empty)
        fx(before.status()) shouldBe None
        fx(dotShown(before)) shouldBe false
        fx(dotShown(after)) shouldBe false
        fx(dotShown(other)) shouldBe false
        fx(before.tooltip()) shouldBe ""
        fx(before.semantics()) shouldBe Semantics.Plain
      }
    }

    "follow the person a face is bound to, and give somebody without an id none" in {
      val who = Var(ada)
      val face = fx(new Avatar(who(), 32.0))
      val nobody = fx(new Avatar(Participant("", ""), 32.0))
      shown(new Container { children ++= List(face, nobody) }) {
        fx(Avatar.statuses @= Map(ada.id -> meeting, "" -> away))
        fx(face.status()) shouldBe Some(meeting)
        fx(nobody.status()) shouldBe None
        fx(who @= bo)
        fx(face.status()) shouldBe None
        fx(dotShown(face)) shouldBe false
        fx(who @= ada)
        fx(face.status()) shouldBe Some(meeting)
      }
    }
  }

  "An avatar's own status" should {
    "stand over the map's, None showing none, until it is bound to the map again" in {
      val face = fx(new Avatar(ada, 32.0))
      val sibling = fx(new Avatar(ada, 32.0))
      shown(new Container { children ++= List(face, sibling) }) {
        fx(face.status @= Some(away))
        fx(dotShown(face)) shouldBe true
        fx(dotColour(face)) shouldBe away.colour
        fx(Avatar.statuses @= Map(ada.id -> meeting))
        fx(face.status()) shouldBe Some(away)
        fx(sibling.status()) shouldBe Some(meeting)
        fx(face.status @= None)
        fx(dotShown(face)) shouldBe false
        fx(face.tooltip()) shouldBe ""
        fx(sibling.status()) shouldBe Some(meeting)
        fx(face.status := Avatar.statusOf(ada.id))
        fx(face.status()) shouldBe Some(meeting)
        fx(Avatar.statuses @= Map.empty)
        fx(face.status()) shouldBe None
        // and a face with no id has only its own
        val nobody = fx(new Avatar(Participant("", ""), 24.0))
        fx(nobody.status @= Some(meeting))
        fx(dotShown(nobody)) shouldBe true
        fx(nobody.tooltip()) shouldBe meeting.words
        fx(nobody.dispose())
      }
    }

    "keep its words with its dot turned off" in {
      val face = fx(new Avatar(ada, 32.0))
      shown(face) {
        fx(face.statusDot @= false)
        fx(Avatar.statuses @= Map(ada.id -> meeting))
        fx(face.statusMark) shouldBe None
        fx(face.tooltip()) shouldBe meeting.words
        fx(face.statusDot @= true)
        fx(dotShown(face)) shouldBe true
        fx(face.statusDot @= false)
        fx(dotShown(face)) shouldBe false
        fx(face.semantics()) shouldBe Semantics.image("Ada, " + meeting.words)
      }
    }
  }

  "An avatar's status words" should {
    "be said by a pointer and to a screen reader after the name, joined to busy's" in {
      val face = fx(new Avatar(ada, 32.0))
      shown(face) {
        fx(face.status @= Some(meeting))
        fx(face.tooltip()) shouldBe meeting.words
        fx(face.semantics()) shouldBe Semantics.image("Ada, In a meeting · until 3:00 PM")
        fx {
          face.busyDetail @= "Running bash"
          face.busy @= true
        }
        fx(face.tooltip()) shouldBe "Running bash\nIn a meeting · until 3:00 PM"
        fx(face.semantics()) shouldBe Semantics.image("Ada, busy, Running bash, In a meeting · until 3:00 PM")
        fx(face.busyDetail @= "")
        fx(face.tooltip()) shouldBe meeting.words
        fx(face.semantics()) shouldBe Semantics.image("Ada, busy, In a meeting · until 3:00 PM")
        // busy without a status says as it did
        fx(face.status @= None)
        fx(face.tooltip()) shouldBe ""
        fx(face.semantics()) shouldBe Semantics.image("Ada, busy")
        fx(face.busyDetail @= "Running bash")
        fx(face.tooltip()) shouldBe "Running bash"
        // a status of no words says nothing more, though its dot shows
        fx {
          face.busy @= false
          face.status @= Some(AvatarStatus(Color.fromHex("#43a047"), ""))
        }
        fx(dotShown(face)) shouldBe true
        fx(face.tooltip()) shouldBe ""
        fx(face.semantics()) shouldBe Semantics.Plain
      }
    }
  }

  "An avatar's status dot" should {
    "be about a third of the face, 8 px at the least, ringed, on the bottom corner past the face's box" in {
      List(18.0, 24.0, 32.0, 48.0, 96.0).foreach { d =>
        val face = fx(new Avatar(ada, d))
        shown(face) {
          fx(face.status @= Some(meeting))
          val mark = fx(face.statusMark).get
          val ring = Avatar.dotRing(d)
          val outer = math.max(8.0, d * 0.3) + ring * 2.0
          fx(mark.size.width()) shouldBe outer +- 1e-9
          fx(mark.size.height()) shouldBe outer +- 1e-9
          fx(mark.radius()) shouldBe outer / 2.0 +- 1e-9
          val inner = fx(mark.childComponents.head)
          fx(inner.size.width()) shouldBe math.max(8.0, d * 0.3) +- 1e-9
          fx(inner.position.left()) shouldBe ring
          fx(inner.position.top()) shouldBe ring
          // its middle on the face's edge at the bottom-right, 45 degrees round
          val middle = d / 2.0 + d / 2.0 * math.sqrt(0.5)
          fx(mark.position.left() + outer / 2.0) shouldBe middle +- 1e-9
          fx(mark.position.top() + outer / 2.0) shouldBe middle +- 1e-9
          (fx(mark.position.top()) + outer) should be > d
          (math.max(8.0, d * 0.3) / d) should be <= (8.0 / 18.0)
          // the ground's ring and the status's colour; nothing cuts it to the face's box
          fx(mark.backgroundColor()) shouldBe fx(Theme.background())
          fx(dotColour(face)) shouldBe meeting.colour
          fx(face.clipsToBox()) shouldBe Some(false)
          fx(face.node.fx.getClip) shouldBe null
          // and drawn there, once laid out
          val left = fx(mark.position.left())
          eventually {
            val b = mark.node.fx.getBoundsInParent
            math.abs(b.getMinX - left) < 0.5 && math.abs(b.getWidth - outer) < 0.5 &&
              math.abs(b.getHeight - outer) < 0.5
          } shouldBe true
          // over the busy ring, made after it
          fx(face.busy @= true)
          fx(face.childComponents.last) shouldBe mark
        }
      }
    }

    "go to the bottom-left in a right-to-left language" in {
      val face = fx(new Avatar(ada, 40.0))
      shown(face) {
        fx(face.status @= Some(meeting))
        val mark = fx(face.statusMark).get
        val start = Avatar.dotStart(40.0)
        fx(mark.position.left()) shouldBe start
        fx(Direction.rtl @= true)
        try fx(mark.position.left()) shouldBe 40.0 - start - Avatar.dotOuter(40.0)
        finally fx(Direction.rtl @= false)
        fx(mark.position.left()) shouldBe start
      }
    }

    "ring itself in the ground it is given" in {
      val face = fx(new Avatar(ada, 32.0))
      shown(face) {
        fx(face.status @= Some(meeting))
        fx(face.statusGround @= Color.fromHex("#123456"))
        fx(face.statusMark.get.backgroundColor()) shouldBe Color.fromHex("#123456")
      }
    }
  }

  "A disposed avatar" should {
    "let go of the map, and follow it no more" in {
      val reactions = fx(Avatar.statuses.reactions().size)
      val faces = fx((0 until 10).map(i => new Avatar(Participant("p-" + i, "P" + i), 32.0)).toList)
      fx(Avatar.statuses.reactions().size) should be > reactions
      fx(faces.foreach(_.dispose()))
      fx(Avatar.statuses.reactions().size) shouldBe reactions
      fx(Avatar.statuses @= Map("p-1" -> meeting))
      fx(faces(1).status()) shouldBe None
      fx(Avatar.statuses @= Map.empty)
      // and a face that showed a dot lets go as well
      val dotted = fx(new Avatar(ada, 32.0))
      fx(Avatar.statuses @= Map(ada.id -> meeting))
      fx(dotShown(dotted)) shouldBe true
      fx(dotted.dispose())
      fx(Avatar.statuses.reactions().size) shouldBe reactions
      fx(Avatar.statuses @= Map.empty)
    }
  }

  case class Chat(id: String)

  private def look(c: Chat): RichLook = RichLook(c.id, Some("snippet"), Some(Participant("p-" + c.id, c.id)))

  "A rich list's row" should {
    "show its person's status on its avatar and say it with the row, following the map" in {
      val chats = Vector(Chat("a"), Chat("b"))
      val list = fx {
        val l = new RichList[Chat](Var(Vector(RichSection("all", "All", chats))), _.id, look, Var(Set.empty[String]))
        l.size.width @= 320.0
        l.size.height @= 400.0
        l
      }
      shown(list) {
        def row(i: Int): RichRow[Chat] = list.list.rowAt(i).get.asInstanceOf[RichRow[Chat]]
        fx(Avatar.statuses @= Map("p-a" -> meeting))
        fx(row(1).tallFace.status()) shouldBe Some(meeting)
        fx(dotShown(row(1).tallFace)) shouldBe true
        fx(row(1).tallFace.tooltip()) shouldBe meeting.words
        fx(row(1).semantics().label) should endWith("snippet, In a meeting · until 3:00 PM")
        fx(row(2).tallFace.status()) shouldBe None
        fx(row(2).semantics().label) should not include "meeting"
        // ringed in the row's ground
        fx(row(1).tallFace.statusMark.get.backgroundColor()) shouldBe fx(Theme.background())
        fx(Avatar.statuses @= Map("p-b" -> away))
        fx(row(1).semantics().label) should not include "meeting"
        fx(dotShown(row(1).tallFace)) shouldBe false
        fx(row(2).semantics().label) should endWith("snippet, Away")
        fx(dotShown(row(2).tallFace)) shouldBe true
      }
    }
  }
}

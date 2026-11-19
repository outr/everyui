package everyui.chat

import everyui.component.{Component, TextView}
import everyui.focus.Focus
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import reactify.*

/** A message sent elsewhere or come from elsewhere (`ChatEntry.Message.direction`), on the desktop's real nodes: the
  * line over its words with the other party's face, its edge and wash, said to a screen reader, changed and cleared in
  * place, its group broken before and after it, and nothing made for a message without one. */
class MessageDirectionSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val elsewhere = Participant("s-everyui", "everyui")
  private val nabo = Participant("s-nabo", "NaboTV")

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

  private def transcript(entries: Vector[ChatEntry]): Transcript = fx {
    val t = new Transcript(Var(entries))
    t.size.width @= 800.0
    t.size.height @= 500.0
    t
  }

  private def row(t: Transcript, id: String): Option[ChatRow] =
    t.list.rows.find(_.item().exists(_.id == id)).collect { case r: ChatRow => r }
  private def view(t: Transcript, id: String): MessageView = row(t, id).flatMap(_.message).get
  private def shown(t: Transcript, id: String): Boolean = row(t, id).flatMap(_.message).nonEmpty
  private def said(t: Transcript, id: String): String = row(t, id).get.semantics().label
  private def leads(t: Transcript, id: String): Boolean = {
    val r = row(t, id).get
    t.leads(r.index(), r.item().get)
  }
  // the words over the bubble, where they show
  private def line(t: Transcript, id: String): Option[String] = view(t, id).directed.filter(_.visible()).map { l =>
    l.childComponents.collect { case w: TextView if !w.isInstanceOf[everyui.component.MaterialIconView] => w.content() }
      .mkString
  }

  private def talk(): Vector[ChatEntry.Message] = Vector(
    ChatEntry.message("m1", ada, self = false, "Morning."),
    ChatEntry.message("m2", ada, self = false, "The fix is in."),
    ChatEntry.message("m3", ada, self = false, "Tests pass."))

  "A message's direction" should {
    "draw a line over its words with an edge, said first, changed and cleared in place" in {
      val Vector(m1, m2, m3) = talk()
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m1") && shown(t, "m2") && shown(t, "m3")) shouldBe true
        // the first leads its group already, so a direction draws on the view it has
        val first = fx(view(t, "m1"))
        val height = fx(first.bubble.size.height())
        val plain = fx(first.bubble.backgroundColor())
        fx(first.directed) shouldBe None

        fx(m1.direction @= Some(MessageDirection.To("everyui", elsewhere)))
        (fx(view(t, "m1")) eq first) shouldBe true
        fx(line(t, "m1")) shouldBe Some("To everyui")
        fx(first.bubble.borderWidth()) shouldBe 1.0
        fx(first.bubble.borderColor()) shouldBe fx(Theme.accent().withAlpha(0.55))
        fx(first.bubble.backgroundColor()) should not be plain
        val heading = fx(first.directed.get)
        fx(first.bubble.size.height()) shouldBe (height + fx(heading.size.height()) + 4.0 +- 0.5)
        // inside the bubble, at its top, the other party's face in it
        fx(heading.parent()).exists(_ eq first.bubble) shouldBe true
        fx(heading.position.top()) shouldBe Transcript.padY
        fx(heading.childComponents.collect { case a: Avatar => a.size.width() }.toVector) shouldBe
          Vector(MessageView.faceSize)
        eventually(said(t, "m1") == "Ada, Reply to everyui: Morning.") shouldBe true

        fx(m1.direction @= Some(MessageDirection.From("NaboTV", nabo)))
        (fx(view(t, "m1")) eq first) shouldBe true
        fx(line(t, "m1")) shouldBe Some("From NaboTV")
        fx(first.bubble.borderColor()) shouldBe fx(Theme.info().withAlpha(0.55))
        fx(said(t, "m1")) shouldBe "Ada, From NaboTV: Morning."

        fx(m1.direction @= None)
        (fx(view(t, "m1")) eq first) shouldBe true
        fx(line(t, "m1")) shouldBe None
        fx(first.bubble.borderWidth()) shouldBe 0.0
        fx(first.bubble.backgroundColor()) shouldBe plain
        fx(first.bubble.size.height()) shouldBe (height +- 0.5)
        fx(said(t, "m1")) shouldBe "Ada: Morning."
      }
    }

    "break its author's group before and after it, as it is set and as it is cleared" in {
      val Vector(m1, m2, m3) = talk()
      fx(m2.direction @= Some(MessageDirection.To("everyui", elsewhere)))
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m1") && shown(t, "m2") && shown(t, "m3")) shouldBe true
        fx(leads(t, "m1")) shouldBe true
        fx(leads(t, "m2")) shouldBe true
        fx(leads(t, "m3")) shouldBe true
        val third = fx(view(t, "m3"))
        fx(m2.direction @= None)
        eventually(!leads(t, "m2") && !leads(t, "m3")) shouldBe true
        // the next made again, as part of the group now
        eventually(!(view(t, "m3") eq third)) shouldBe true
        // grouped again, the views made again for it
        eventually(view(t, "m2").directed.isEmpty) shouldBe true
        fx(m3.direction @= Some(MessageDirection.From("NaboTV", nabo)))
        eventually(leads(t, "m3")) shouldBe true
        fx(leads(t, "m2")) shouldBe false
        eventually(line(t, "m3") == Some("From NaboTV")) shouldBe true
      }
    }

    "group as a rule: a directed message is never joined to its author's either side" in {
      val Vector(m1, m2, m3) = talk()
      Grouping.leads(Some(m1), m2) shouldBe false
      fx(m2.direction @= Some(MessageDirection.To("everyui", elsewhere)))
      Grouping.leads(Some(m1), m2) shouldBe true
      Grouping.leads(Some(m2), m3) shouldBe true
      val made = ChatEntry.message("m4", ada, self = false, "Done.",
        direction = Some(MessageDirection.From("NaboTV", nabo)))
      Grouping.leads(Some(m3), made) shouldBe true
    }

    "make nothing, and listen for nothing, for a message without one" in {
      val Vector(m1, m2, m3) = talk()
      val t = transcript(Vector(m1, m2, m3))
      onScreen(t) {
        eventually(shown(t, "m1") && shown(t, "m2")) shouldBe true
        fx(view(t, "m2").directed) shouldBe None
        m2.directionKept shouldBe false
        fx(said(t, "m2")) shouldBe "Ada: The fix is in."
      }
    }
  }
}

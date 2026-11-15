package everyui.chat

import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** A message's small spinner made only while its words are arriving: none for a message done with, one as it streams,
  * taken apart as it ends, and made again should it stream once more. */
class MessageSpinnerSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")

  "A message's spinner" should {
    "be made only while its words are arriving" in {
      val done = ChatEntry.message("m1", ada, self = false, "Done with.", meta = Some("edited"))
      val live = ChatEntry.message("m2", ada, self = false, "Still", streaming = true, meta = Some("typing"))
      val t = fx {
        val t = new Transcript(Var(Vector[ChatEntry](done, live)))
        t.size.width @= 700.0
        t.size.height @= 500.0
        ui.children.clear()
        ui.children += t
        t
      }
      try {
        def view(id: String): Option[MessageView] =
          t.list.rows.find(_.item().exists(_.id == id)).collect { case r: ChatRow => r }.flatMap(_.message)
        eventually(view("m1").nonEmpty && view("m2").nonEmpty) shouldBe true
        fx(view("m1").get.spinning) shouldBe None
        val spinner = fx(view("m2").get.spinning)
        spinner.nonEmpty shouldBe true
        fx(spinner.get.parent().nonEmpty) shouldBe true
        fx(live.streaming @= false)
        fx(view("m2").get.spinning) shouldBe None
        fx(spinner.get.takenApart) shouldBe true
        fx(live.streaming @= true)
        fx(view("m2").get.spinning.exists(s => !(s eq spinner.get) && !s.takenApart)) shouldBe true
      } finally fx {
        ui.children.clear()
        t.dispose()
        Focus.focused @= None
      }
    }
  }
}

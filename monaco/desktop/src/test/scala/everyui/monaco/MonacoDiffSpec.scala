package everyui.monaco

import everyui.ui
import fabric.*
import fabric.rw.*

/** The diff editor's changes, checkboxes and hunk edits: the protocol against a guest the spec plays, then Monaco's own
  * diff in a JavaFX web view. */
class MonacoDiffSpec extends MonacoFxSpec {
  private val deletion = MonacoLineChange(7, 8, 8, 0)
  private val edit = MonacoLineChange(4, 5, 4, 6, List(MonacoCharChange(MonacoRange(4, 20, 4, 21), MonacoRange(4, 20, 4, 30))))

  "A diff controller" should {
    "read the changes Monaco reports, map a gutter click to its change, and send the checks and hunk edits" in {
      val guest = fx(new FakeGuest("diff-spec", (_, _) => Some(Null)))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      val controller = fx(MonacoDiffController("a", "b", "scala"))
      fx(controller.checks @= List(MonacoDiffCheck(1, checked = true)))
      fx(controller.attach(bridge, "diff-1"))
      // the checks set before the diff was made go to it as it attaches
      fx(guest.methods.contains("diff.setChecks")) shouldBe true
      var heard: List[List[MonacoLineChange]] = Nil
      fx(controller.onDidUpdateDiff.attach(c => heard = heard :+ c))
      fx(guest.say("diff.updated", obj("diffId" -> str("diff-1"), "changes" -> arr(edit.json, deletion.json))))
      fx(controller.changes()) shouldBe List(edit, deletion)
      heard shouldBe List(List(edit, deletion))
      edit.charChanges.head.modifiedRange.endColumn shouldBe 30
      // a click on line 5 is within the edit; on line 8, the deletion's anchor
      var clicks: List[MonacoDiffClick] = Nil
      fx(controller.onGutterClick.attach(c => clicks = clicks :+ c))
      fx(guest.say("diff.gutterClick", obj("diffId" -> str("diff-1"), "line" -> num(5), "kind" -> str("line"))))
      fx(guest.say("diff.gutterClick", obj("diffId" -> str("diff-1"), "line" -> num(8), "kind" -> str("change"))))
      clicks shouldBe List(MonacoDiffClick(5, isLine = true, Some(edit)), MonacoDiffClick(8, isLine = false, Some(deletion)))
      // the usual checks: one per change, one per changed line of the working copy
      val checks = MonacoDiffCheck.forChanges(List(edit, deletion), _ == edit, lines = true, _ != 5)
      checks shouldBe List(MonacoDiffCheck(4, true), MonacoDiffCheck(4, true, line = true), MonacoDiffCheck(5, false, line = true),
        MonacoDiffCheck(6, true, line = true), MonacoDiffCheck(8, false))
      fx(controller.checks @= checks)
      val sent = fx(guest.calls.filter(_._1 == "diff.setChecks").last._2)
      sent("checks").asVector.map(c => (Wire.int(c, "line"), Wire.bool(c, "checked"), Wire.string(c, "kind"))) shouldBe
        Vector((4, true, "change"), (4, true, "line"), (5, false, "line"), (6, true, "line"), (8, false, "change"))
      fx(controller.revertChange(deletion))
      fx(controller.acceptChange(edit))
      val applied = fx(guest.calls.filter(_._1 == "diff.applyChange").map(_._2))
      applied.map(a => Wire.string(a, "from")) shouldBe List("original", "modified")
      applied.head("change").as[MonacoLineChange] shouldBe deletion
      fx(bridge.close())
    }
  }

  "Monaco's diff in a JavaFX web view" should {
    "report its changes, roll one back and take another in, each undoable" in {
      val original = "one\ntwo\nthree\nfour\nfive\n"
      val modified = "one\nTWO\nthree\nfive\nsix\n"
      val controller = fx(MonacoDiffController(original, modified, "plaintext"))
      val editor = fx {
        val e = new MonacoDiffEditor(controller)
        e.size.width @= 900.0
        e.size.height @= 500.0
        ui.children += e
        e
      }
      withClue("the diff worked out: ") { eventually(120.0)(controller.changes().size == 3) shouldBe true }
      val changes = fx(controller.changes())
      // TWO for two; four deleted; six added
      changes.map(c => (c.isAddition, c.isDeletion)) shouldBe List((false, false), (false, true), (true, false))
      fx(controller.checks @= MonacoDiffCheck.forChanges(changes, _ => true, lines = true))
      // rolled back, the working copy has "two" again
      fx(controller.revertChange(changes.head))
      eventually(10.0)(controller.modified().startsWith("one\ntwo\n")) shouldBe true
      // each edit moves the lines under the changes after it, which are worked out again: the next is taken from those
      eventually(10.0)(controller.changes().size == 2) shouldBe true
      // "four" deleted in the working copy: rolled back, it comes back
      fx(controller.revertChange(controller.changes().head))
      eventually(10.0)(controller.modified() == "one\ntwo\nthree\nfour\nfive\nsix\n") shouldBe true
      // "six" added: taken into the original
      eventually(10.0)(controller.changes().size == 1) shouldBe true
      fx(controller.acceptChange(controller.changes().head))
      eventually(10.0)(controller.original() == "one\ntwo\nthree\nfour\nfive\nsix\n") shouldBe true
      // and with the two sides the same, no change is left
      var left: List[MonacoLineChange] = List(null)
      eventually(15.0) {
        controller.lineChanges().foreach(l => left = l)(using Monaco.uiThread)
        left.isEmpty
      } shouldBe true
      fx(controller.changes()) shouldBe Nil
      fx(editor.dispose())
      fx(controller.dispose())
    }
  }
}

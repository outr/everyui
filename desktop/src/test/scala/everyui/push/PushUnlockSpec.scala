package everyui.push

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A button that requires an unlock (`PushButton.requiresUnlock`): read from "requiresUnlock" in a push's "actions",
  * false where it is not said, written back as it was sent, and, on Android before 12, opening the app where it would
  * have run with the app closed. */
class PushUnlockSpec extends AnyWordSpec with Matchers {
  private val approve = PushButton("approve", "Approve", background = true, requiresUnlock = true)
  private val deny = PushButton("deny", "Deny", background = true)
  private val answer = PushButton("answer", "Answer", input = true, hint = "Your answer", requiresUnlock = true)

  "A button that requires an unlock" should {
    "be read from a JSON array, true or \"true\"" in {
      PushButton.parse("""[{"id":"approve","label":"Approve","background":true,"requiresUnlock":true},
        |{"id":"answer","label":"Answer","input":"true","hint":"Your answer","requiresUnlock":"true"},
        |{"id":"deny","label":"Deny","background":true,"requiresUnlock":false}]""".stripMargin)
        .shouldBe(Vector(approve, answer, deny))
    }
    "be false where it is not said, or is anything but true" in {
      PushButton("x", "X").requiresUnlock shouldBe false
      PushButton.parse("""[{"id":"a"},{"id":"b","requiresUnlock":"yes"},{"id":"c","requiresUnlock":1}]""")
        .map(_.requiresUnlock).shouldBe(Vector(false, false, false))
    }
    "be written back as it was sent, and not at all where it is not asked" in {
      PushButton.json(Vector(approve, deny)) shouldBe
        """[{"id":"approve","label":"Approve","background":true,"requiresUnlock":true},""" +
          """{"id":"deny","label":"Deny","background":true}]"""
      PushButton.parse(PushButton.json(Vector(approve, deny, answer))) shouldBe Vector(approve, deny, answer)
    }
    "come through a push's JSON text, and its keys written back" in {
      val sent = Map("title" -> "Run a command?", "body" -> "ls -la", "path" -> "/approvals/7", "request" -> "7",
        "actions" -> ("""[{"id":"approve","label":"Approve","background":"true","requiresUnlock":"true"},""" +
          """{"id":"deny","label":"Deny","background":true}]"""))
      val m = PushMessage.of(sent)
      m.actions shouldBe Vector(approve, deny)
      m.data shouldBe Map("request" -> "7")
      PushMessage.of(m.fields) shouldBe m
      PushAction("approve", m).button.map(_.requiresUnlock) shouldBe Some(true)
    }
  }

  "On Android before 12" should {
    "open the app for a background button or a reply that requires an unlock" in {
      approve.opensToUnlock(30) shouldBe true
      approve.opensToUnlock(23) shouldBe true
      answer.opensToUnlock(30) shouldBe true
    }
    "leave the rest as they are, and from 12 on run them where they would have" in {
      approve.opensToUnlock(31) shouldBe false
      approve.opensToUnlock(35) shouldBe false
      answer.opensToUnlock(PushButton.unlockSdk) shouldBe false
      deny.opensToUnlock(30) shouldBe false
      // one that opens the app already asks for the unlock as it opens
      PushButton("open", "Open", requiresUnlock = true).opensToUnlock(30) shouldBe false
    }
  }
}

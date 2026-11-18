package everyui.push

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A copy button (`PushButton.copyText`): read from "copy" and "copyDismisses" in a push's "actions", none and
  * false where they are not said, written back as they were sent, and, required to unlock before Android 12, opening
  * the app; the case class's own copy kept. */
class PushCopySpec extends AnyWordSpec with Matchers {
  private val copying = PushButton("copy", "Copy 430151", copyText = Some("430151"))
  private val dismissing = PushButton("copy", "Copy 430151", copyText = Some("430151"), copyDismisses = true)

  "A copy button" should {
    "be read from a JSON array, its text a string or a number, and copyDismisses true or \"true\"" in {
      PushButton.parse("""[{"id":"copy","label":"Copy 430151","copy":"430151"}]""") shouldBe Vector(copying)
      PushButton.parse("""[{"id":"copy","label":"Copy 430151","copy":430151,"copyDismisses":true}]""") shouldBe
        Vector(dismissing)
      PushButton.parse("""[{"id":"copy","label":"Copy 430151","copy":"430151","copyDismisses":"true"}]""") shouldBe
        Vector(dismissing)
    }
    "have no text and stay by default, or where they are anything else" in {
      PushButton("x", "X").copyText shouldBe None
      PushButton("x", "X").copyDismisses shouldBe false
      PushButton.parse("""[{"id":"a"},{"id":"b","copy":"","copyDismisses":"yes"},""" +
        """{"id":"c","copy":null,"copyDismisses":1}]""")
        .map(b => (b.copyText, b.copyDismisses)).shouldBe(Vector((None, false), (None, false), (None, false)))
    }
    "be written back as it was sent, and not at all where it is not a copy" in {
      PushButton.json(Vector(dismissing, PushButton("open", "Open"))) shouldBe
        """[{"id":"copy","label":"Copy 430151","copy":"430151","copyDismisses":true},{"id":"open","label":"Open"}]"""
      PushButton.json(Vector(copying)) shouldBe """[{"id":"copy","label":"Copy 430151","copy":"430151"}]"""
      val quoted = PushButton("c", "Copy", copyText = Some("a \"b\"\n"))
      PushButton.parse(PushButton.json(Vector(copying, dismissing, quoted))) shouldBe
        Vector(copying, dismissing, quoted)
    }
    "come through a push's JSON text, and its keys written back" in {
      val sent = Map("title" -> "New mail", "body" -> "Your code is 430151", "tag" -> "mail-9", "account" -> "a1",
        "actions" -> """[{"id":"copy","label":"Copy 430151","copy":"430151","copyDismisses":"true"}]""")
      val m = PushMessage.of(sent)
      m.actions shouldBe Vector(dismissing)
      m.data shouldBe Map("account" -> "a1")
      PushMessage.of(m.fields) shouldBe m
      PushAction("copy", m).button.flatMap(_.copyText) shouldBe Some("430151")
      PushAction("copy", m).background shouldBe false
    }
    "open the app to be unlocked before Android 12 where it requires an unlock, and not from 12" in {
      val locked = copying.copy(requiresUnlock = true)
      locked.opensToUnlock(30) shouldBe true
      locked.opensToUnlock(PushButton.unlockSdk) shouldBe false
      copying.opensToUnlock(30) shouldBe false
    }
  }
}

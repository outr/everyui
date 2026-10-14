package everyui.app

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What a platform's signals become: away once on leaving, resumed once on coming back, never a return at launch. */
class PresenceSpec extends AnyWordSpec with Matchers {
  private def told(): (Presence, () => List[String]) = {
    var said = List.empty[String]
    val presence = new Presence(() => said = said :+ "away", () => said = said :+ "resumed")
    (presence, () => said)
  }

  "A Presence" should {
    "say nothing when it is first shown, which is a launch" in {
      val (presence, said) = told()
      presence.shown()
      presence.shown()
      said() shouldBe Nil
      presence.isAway shouldBe false
    }
    "say away once however many signals say it is hidden" in {
      val (presence, said) = told()
      presence.hidden()
      presence.hidden()
      said() shouldBe List("away")
      presence.isAway shouldBe true
    }
    "say resumed once, after away, however many signals say it is shown" in {
      val (presence, said) = told()
      presence.hidden()
      presence.shown()
      presence.shown()
      said() shouldBe List("away", "resumed")
      presence.isAway shouldBe false
    }
    "say each in turn for each leaving and return" in {
      val (presence, said) = told()
      presence.shown()
      presence.hidden()
      presence.hidden()
      presence.shown()
      presence.hidden()
      presence.shown()
      presence.shown()
      said() shouldBe List("away", "resumed", "away", "resumed")
    }
  }
}

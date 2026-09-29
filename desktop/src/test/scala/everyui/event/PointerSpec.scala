package everyui.event

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A consumed pointer event keeps the components under it from firing it while it climbs, and no longer. */
class PointerSpec extends AnyWordSpec with Matchers {
  "A consumed event" should {
    "block its equal while it climbs" in {
      val click = PointerEvent(PointerKind.Click, 10.0, 20.0)
      click.consume()
      Pointer.blocks(PointerEvent(PointerKind.Click, 10.0, 20.0)) shouldBe true
      Pointer.blocks(PointerEvent(PointerKind.Click, 11.0, 20.0)) shouldBe false
    }
    "let a second click at the same pixel fire once it has passed" in {
      val click = PointerEvent(PointerKind.Click, 10.0, 20.0)
      click.consume()
      Pointer.consumedAt = Pointer.consumedAt - Pointer.hold - 1.0
      Pointer.blocks(PointerEvent(PointerKind.Click, 10.0, 20.0)) shouldBe false
    }
  }
}

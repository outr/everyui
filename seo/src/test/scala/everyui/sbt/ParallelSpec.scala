package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A build's work spread over the cores: answers in order, and fails rather than waits where an item fails, an error
  * Scala calls fatal too (a library missing from the class path left a seal waiting for ever). */
class ParallelSpec extends AnyWordSpec with Matchers {
  "Parallel.map" should {
    "answer in the items' order" in {
      Parallel.map((1 to 50).toList)(i => { Thread.sleep((50 - i) % 7); i * 2 }) shouldBe (1 to 50).map(_ * 2)
    }
    "answer nothing for no items" in {
      Parallel.map(Seq.empty[Int])(identity) shouldBe Nil
    }
    "fail with an item's NoClassDefFoundError rather than wait" in {
      val thrown = intercept[NoClassDefFoundError] {
        Parallel.map((1 to 8).toList)(i => if (i == 3) throw new NoClassDefFoundError("com/example/Missing") else i)
      }
      thrown.getMessage shouldBe "com/example/Missing"
    }
    "fail with an item's exception" in {
      intercept[IllegalStateException](Parallel.map(List(1, 2))(i => if (i == 2) throw new IllegalStateException("no") else i))
    }
  }
}

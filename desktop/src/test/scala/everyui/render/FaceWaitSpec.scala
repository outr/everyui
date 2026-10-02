package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Which faces words wait for before they are measured on the web (FaceWait), against a page's faces as a browser
  * keeps them: NaboTV's Roboto, four weights loaded at the start and Black declared for later, each in slices by
  * script. A load of words in a weight loads what the browser takes for them, as `document.fonts.load` does; words
  * wait, load and look again until nothing is left to wait for, as TextFit does, and must come to an end. */
class FaceWaitSpec extends AnyWordSpec with Matchers {
  private val latin = "U+0-FF, U+131, U+152-153, U+2000-206F"
  private val cyrillic = "U+300-36F, U+400-45F"

  private final class Face(val weight: String, val range: String, var status: String) {
    override def toString: String = s"$weight $range $status"
  }

  // Roboto as NaboTV's page has it: 300, 400, 500 and 700 at the start (their Latin slices loaded), 900 later
  private def roboto(): Seq[Face] = Seq("300", "400", "500", "700", "900").flatMap { w =>
    Seq(new Face(w, latin, if (w == "900") "unloaded" else "loaded"), new Face(w, cyrillic, "unloaded"))
  }

  private def waiting(faces: Seq[Face], weight: String, text: String, tried: Seq[Face] = Nil): Seq[Face] =
    FaceWait.waiting(faces, weight, text, tried)(_.weight, _.status, _.range)

  // what document.fonts.load(weight, text) does: the slices of the weight the browser takes that hold the text
  private def load(faces: Seq[Face], weight: String, text: String): Unit = {
    val drawn = FaceWait.matched(FaceWait.weight(weight), faces.map(f => FaceWait.weight(f.weight)).distinct)
    faces.filter(f => drawn.contains(FaceWait.weight(f.weight)) && FaceWait.holds(f.range, text) && f.status == "unloaded")
      .foreach(_.status = "loaded")
  }

  // TextFit's rounds: wait, load, look again, never asking a face twice; the rounds it took
  private def rounds(faces: Seq[Face], weight: String, text: String): Int = {
    var tried = Seq.empty[Face]
    var n = 0
    var left = waiting(faces, weight, text)
    while (left.nonEmpty) {
      n += 1
      if (n > 10) fail(s"words in $weight still wait after 10 loads, for ${left.mkString(", ")}")
      load(faces, weight, text)
      tried ++= left
      left = waiting(faces, weight, text, tried)
    }
    n
  }

  "Words" should {
    "not wait for a face the browser does not draw them in: 600 is drawn in 700, not in a Black declared for later" in {
      val faces = roboto()
      waiting(faces, "600", "Continue watching") shouldBe empty
      rounds(faces, "600", "Continue watching") shouldBe 0
      faces.filter(_.weight == "900").map(_.status).distinct shouldBe Seq("unloaded")
    }

    "wait for the slice that holds them, in the weight they are drawn in, and stop once it is in" in {
      val faces = roboto()
      waiting(faces, "600", "Привет").map(f => (f.weight, f.range)) shouldBe Seq(("700", cyrillic))
      rounds(faces, "600", "Привет") shouldBe 1
      faces.filter(f => f.weight == "700" && f.range == cyrillic).head.status shouldBe "loaded"
    }

    "wait for a face declared for later where it is the one drawn, and stop once it is in" in {
      val faces = roboto()
      waiting(faces, "800", "Now playing").map(_.weight) shouldBe Seq("900")
      rounds(faces, "800", "Now playing") shouldBe 1
    }

    "come to an end where a load leaves a face unloaded" in {
      // a browser that loads nothing (the file is refused, or another face answers): asked once, then not again
      val faces = roboto()
      val left = waiting(faces, "900", "Now playing")
      left should not be empty
      waiting(faces, "900", "Now playing", left) shouldBe empty
    }

    "wait for nothing where no character of theirs is in a slice not loaded" in {
      waiting(roboto(), "400", "Home 🎬 中文") shouldBe empty
    }
  }

  "The weight drawn" should {
    "be chosen as CSS chooses it" in {
      val weights = Seq(300, 400, 500, 700, 900)
      FaceWait.matched(600, weights) shouldBe Some(700)
      FaceWait.matched(800, weights) shouldBe Some(900)
      FaceWait.matched(450, Seq(300, 400, 700)) shouldBe Some(400)
      FaceWait.matched(400, Seq(300, 500, 700)) shouldBe Some(500)
      FaceWait.matched(200, weights) shouldBe Some(300)
      FaceWait.matched(950, weights) shouldBe Some(900)
      FaceWait.matched(400, Nil) shouldBe None
      FaceWait.weight("bold") shouldBe 700
      FaceWait.weight("normal") shouldBe 400
    }
  }
}

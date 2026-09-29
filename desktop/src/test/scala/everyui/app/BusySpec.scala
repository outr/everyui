package everyui.app

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The busy indicator's arithmetic: what a Busy says, which picture it is at a moment, and the title's prefix. */
class BusySpec extends AnyWordSpec with Matchers {
  private val spinning = Some(Busy())

  "A Busy" should {
    "hold its progress to 0..1, and read a NaN as none" in {
      Busy(Some(0.42)).fraction shouldBe Some(0.42)
      Busy(Some(-0.5)).fraction shouldBe Some(0.0)
      Busy(Some(1.7)).fraction shouldBe Some(1.0)
      Busy(Some(Double.NaN)).fraction shouldBe None
      Busy().fraction shouldBe None
    }
    "round its percent down, so 100 is done" in {
      Busy(Some(0.999)).percent shouldBe Some(99)
      Busy(Some(1.0)).percent shouldBe Some(100)
      Busy(Some(0.0)).percent shouldBe Some(0)
    }
    "sum itself up in a few words" in {
      Busy(Some(0.42), Some("deliver")).summary shouldBe "42% deliver"
      Busy(Some(0.42)).summary shouldBe "42%"
      Busy(None, Some("deliver")).summary shouldBe "deliver"
      Busy(None, Some("  ")).summary shouldBe ""
      Busy().summary shouldBe ""
    }
  }

  "A title" should {
    "take a prefix while busy, and lose it exactly once idle" in {
      val prefix = Busy.titlePrefix(Some(Busy(Some(0.42), Some("deliver"))))
      prefix shouldBe "(42% deliver) "
      Busy.titlePrefix(spinning) shouldBe "(…) "
      Busy.titlePrefix(None) shouldBe ""
      Busy.untitled(prefix + "Dashboard", prefix) shouldBe "Dashboard"
    }
    "keep a title the app changed since, which no longer has the prefix" in {
      Busy.untitled("Workflows", "(42%) ") shouldBe "Workflows"
      Busy.untitled("(1) Inbox", "") shouldBe "(1) Inbox"
    }
  }

  "A frame" should {
    "be nothing while idle" in {
      BusyFrame.at(None, 3.0) shouldBe None
      BusyFrame.moving(None) shouldBe false
    }
    "turn the spinner by the time alone, eight pictures a second, twelve to a turn" in {
      BusyFrame.at(spinning, 0.0) shouldBe Some(BusyFrame(None, 0))
      BusyFrame.at(spinning, 0.124) shouldBe Some(BusyFrame(None, 0))
      BusyFrame.at(spinning, 0.125) shouldBe Some(BusyFrame(None, 1))
      BusyFrame.at(spinning, 1.5) shouldBe Some(BusyFrame(None, 0))
      BusyFrame.at(spinning, 1.625) shouldBe Some(BusyFrame(None, 1))
      BusyFrame.at(spinning, -1.0) shouldBe Some(BusyFrame(None, 0))
      BusyFrame.moving(spinning) shouldBe true
    }
    "show the frame due after a long sleep, not the ones missed (a tab in the background)" in {
      // woken once a second: every tick is one picture, whichever it is due to be
      (0 until 5).map(s => BusyFrame.at(spinning, s.toDouble).get.turn) shouldBe List(0, 8, 4, 0, 8)
      BusyFrame.at(spinning, 3600.0 + 0.25).get.turn shouldBe 2
    }
    "place the spinner's arc by its turn" in {
      val f = BusyFrame(None, 3)
      f.start shouldBe 0.25
      f.sweep shouldBe BusyFrame.spinnerSweep
    }
    "fill the ring from twelve o'clock by the progress, in sixtieths, and not move by itself" in {
      val f = BusyFrame.at(Some(Busy(Some(0.5), Some("deliver"))), 9.0).get
      f shouldBe BusyFrame(Some(30), 0)
      f.start shouldBe 0.0
      f.sweep shouldBe 0.5
      BusyFrame.at(Some(Busy(Some(1.0))), 0.0).get.sweep shouldBe 1.0
      BusyFrame.at(Some(Busy(Some(0.0))), 0.0).get.sweep shouldBe 0.0
      BusyFrame.moving(Some(Busy(Some(0.5)))) shouldBe false
    }
    "be the same picture for progress too close to show, or a new label" in {
      BusyFrame.at(Some(Busy(Some(0.500))), 0.0) shouldBe BusyFrame.at(Some(Busy(Some(0.510), Some("x"))), 5.0)
      BusyFrame.at(Some(Busy(Some(0.500))), 0.0) should not be BusyFrame.at(Some(Busy(Some(0.517))), 0.0)
    }
  }

  "A progress standing still" should {
    val half = Some(Busy(Some(0.5)))
    "have no orbit unless asked for one, or before it has stood still long enough" in {
      BusyFrame.at(half, 0.0, 60.0).get.orbit shouldBe None
      BusyFrame.at(half, 0.0, 2.9, Some(3.0)).get.orbit shouldBe None
      BusyFrame.moving(half, None) shouldBe false
      BusyFrame.moving(half, Some(3.0)) shouldBe true
      BusyFrame.moving(None, Some(3.0)) shouldBe false
    }
    "turn its orbit by the time since it began, as the spinner turns" in {
      BusyFrame.at(half, 0.0, 3.0, Some(3.0)) shouldBe Some(BusyFrame(Some(30), 0, Some(0)))
      BusyFrame.at(half, 0.0, 3.25, Some(3.0)).get.orbit shouldBe Some(2)
      BusyFrame.at(half, 0.0, 3.0 + 1.5, Some(3.0)).get.orbit shouldBe Some(0)
    }
    "leave an indeterminate busy's spinner as it was" in {
      BusyFrame.at(spinning, 0.125, 60.0, Some(3.0)) shouldBe Some(BusyFrame(None, 1))
    }
    "be drawn over the fill and the empty ring apart, across twelve o'clock too" in {
      def pieces(f: BusyFrame) = f.orbitPieces.map(p => (math.rint(p._1 * 1000) / 1000, math.rint(p._2 * 1000) / 1000, p._3))
      // at 0.25 over a half fill: all over the fill
      pieces(BusyFrame(Some(30), 0, Some(3))) shouldBe List((0.25, 0.12, true))
      // at 5/12 over a half fill: the fill up to 0.5, the empty ring after
      pieces(BusyFrame(Some(30), 0, Some(5))) shouldBe List((0.417, 0.083, true), (0.5, 0.037, false))
      // at 11/12, past twelve o'clock: the empty ring to 1, the fill from 0
      pieces(BusyFrame(Some(30), 0, Some(11))) shouldBe List((0.917, 0.083, false), (0.0, 0.037, true))
      BusyFrame(Some(30), 0).orbitPieces shouldBe Nil
    }
  }
}

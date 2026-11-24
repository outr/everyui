package everyui.location

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The dwell detector Android's visits are judged by (`Dwell`): positions fed one after another, and the arrivals and
  * departures they make, with what it passes over (old, rough) and how it is kept in the settings. */
class DwellSpec extends AnyWordSpec with Matchers {
  private val options = VisitOptions(minMinutes = 10, radius = 100.0)
  private val minute = 60000L
  /** `metres` north of 51.5, -0.12, fixed at `minutes`, `accuracy` metres. */
  private def at(metres: Double, minutes: Long, accuracy: Double = 20.0): Position =
    Position(51.5 + metres / 111195.0, -0.12, accuracy, None, None, None, minutes * minute)

  /** Every position fed in turn, and what each said. */
  private def fed(positions: Position*): (Option[Dwell], List[VisitEvent]) =
    positions.foldLeft((Option.empty[Dwell], List.empty[VisitEvent])) { case ((dwell, said), p) =>
      val (after, events) = Dwell.heard(options, dwell, p)
      (after, said ++ events)
    }

  "The dwell detector" should {
    "arrive once the device has stayed within the radius for the minutes, and only once" in {
      val (dwell, events) = fed(at(0, 0), at(30, 5), at(-20, 10), at(10, 25))
      events.size shouldBe 1
      val VisitEvent.Arrived(visit) = events.head: @unchecked
      visit.arrived shouldBe 0L
      visit.departed shouldBe None
      visit.latitude shouldBe (51.5 + (0.0 + 30.0 - 20.0) / 3.0 / 111195.0) +- 1e-9
      dwell.get.arrived shouldBe true
      dwell.get.fixes shouldBe 4
    }

    "not arrive before the minutes have passed" in {
      fed(at(0, 0), at(10, 5), at(5, 9))._2 shouldBe Nil
    }

    "depart at the first fix beyond the radius, as last seen there, and begin a stay where it is" in {
      val (dwell, events) = fed(at(0, 0), at(10, 12), at(5, 20), at(2000, 35))
      events.map(_.getClass.getSimpleName) shouldBe List("Arrived", "Departed")
      events(1).visit.arrived shouldBe 0L
      events(1).visit.departed shouldBe Some(20 * minute)
      dwell.get.since shouldBe 35 * minute
      dwell.get.arrived shouldBe false
    }

    "start again without a departure where it left before arriving" in {
      val (dwell, events) = fed(at(0, 0), at(500, 5), at(510, 16))
      events.map(_.getClass.getSimpleName) shouldBe List("Arrived")
      events.head.visit.arrived shouldBe 5 * minute
      dwell.get.since shouldBe 5 * minute
    }

    "pass over a fix no later than the last, and a rough one that may still be within the radius" in {
      val (dwell, events) = fed(at(0, 0), at(10, 5), at(800, 5), at(300, 8, accuracy = 400.0))
      events shouldBe Nil
      dwell.get.fixes shouldBe 2
      dwell.get.last shouldBe 5 * minute
    }

    "take a rough fix that is far away for certain as a departure, and begin nothing at it" in {
      val (dwell, events) = fed(at(0, 0), at(10, 12), at(5000, 30, accuracy = 1000.0))
      events.map(_.getClass.getSimpleName) shouldBe List("Arrived", "Departed")
      dwell shouldBe None
    }

    "be kept in the settings and read back as it was" in {
      val (dwell, _) = fed(at(0, 0), at(10, 12))
      Dwell.read(dwell.get.written) shouldBe dwell
      Dwell.read("") shouldBe None
      Dwell.read("1,2,x,4,5,6,1") shouldBe None
      VisitOptions.read(options.written) shouldBe Some(options)
      VisitOptions.read("ten,5") shouldBe None
    }
  }
}

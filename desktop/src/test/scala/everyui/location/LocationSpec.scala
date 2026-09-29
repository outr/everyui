package everyui.location

import everyui.{FxSpec, Permission}

/** Distances between positions, a watch's distance filter (what moved far enough from the last one heard, what the
  * platform says otherwise, the end a refusal makes, nothing after a stop), and the desktop, which has no location. */
class LocationSpec extends FxSpec {
  private def at(latitude: Double, longitude: Double): Position = Position(latitude, longitude, 10.0, None, None, None, 0L)
  /** `metres` north of 51.5, -0.12. */
  private def north(metres: Double): Position = at(51.5 + metres / 111195.0, -0.12)

  "A position" should {
    "measure the distance to another in metres" in {
      north(0.0).distanceTo(north(100.0)) shouldBe 100.0 +- 0.5
      at(51.5, -0.12).distanceTo(at(51.5, -0.1186)) shouldBe 97.0 +- 1.0   // 0.0014° of longitude at 51.5° north
      at(51.5, -0.12).distanceTo(at(51.5, -0.12)) shouldBe 0.0
    }
  }

  "A watch" should {
    "hear the first position, then only those far enough from the last one heard" in {
      var heard: List[LocationAnswer] = Nil
      val watch = new LocationWatch(25.0, a => heard = heard :+ a)
      List(0.0, 10.0, 30.0, 45.0, 60.0).foreach(m => watch.answer(LocationAnswer.Located(north(m))))
      heard shouldBe List(0.0, 30.0, 60.0).map(m => LocationAnswer.Located(north(m)))
    }

    "hear every position with no distance" in {
      var heard = 0
      val watch = new LocationWatch(0.0, _ => heard += 1)
      List(0.0, 0.0, 1.0).foreach(m => watch.answer(LocationAnswer.Located(north(m))))
      heard shouldBe 3
    }

    "go on after a spell without a position, and end at a refusal" in {
      var heard: List[LocationAnswer] = Nil
      var halted = false
      val watch = new LocationWatch(25.0, a => heard = heard :+ a)
      watch.begin(() => halted = true)
      watch.answer(LocationAnswer.LocationUnavailable)
      watch.running shouldBe true
      watch.answer(LocationAnswer.LocationDenied)
      watch.running shouldBe false
      halted shouldBe true
      watch.answer(LocationAnswer.Located(north(0.0)))
      heard shouldBe List(LocationAnswer.LocationUnavailable, LocationAnswer.LocationDenied)
    }

    "hear nothing once stopped, and stop the platform's watch" in {
      var heard = 0
      var halted = false
      val watch = new LocationWatch(0.0, _ => heard += 1)
      watch.begin(() => halted = true)
      watch.stop()
      watch.answer(LocationAnswer.Located(north(0.0)))
      (heard, halted, watch.running) shouldBe ((0, true, false))
    }
  }

  "The desktop" should {
    "have no location, and answer so at once" in {
      Location.supported shouldBe false
      var permission: Option[Permission] = None
      var now: Option[LocationAnswer] = None
      var watched: List[LocationAnswer] = Nil
      fx {
        Location.permission.map(p => permission = Some(p)).start()
        Location.current().map(a => now = Some(a)).start()
        Location.watch()(a => watched = watched :+ a)
      }
      permission shouldBe Some(Permission.Refused)
      now shouldBe Some(LocationAnswer.LocationUnsupported)
      watched shouldBe List(LocationAnswer.LocationUnsupported)
    }
  }
}

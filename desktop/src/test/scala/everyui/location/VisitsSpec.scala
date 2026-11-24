package everyui.location

import everyui.FxSpec
import everyui.async.Task
import everyui.background.{Background, BackgroundApp, Work, WorkResult}
import everyui.render.Platform

/** The visits' own paths, on the desktop, which has neither visits nor the location with the app closed: off until
  * started, a start refused, positions heard and told to the app's `BackgroundApp`, a stop that forgets everything, and
  * visits that find the location taken back (as a revocation leaves them) stopping by themselves, quietly. */
class VisitsSpec extends FxSpec {
  private val options = VisitOptions(minMinutes = 10, radius = 100.0)
  private def at(metres: Double, minutes: Long): Position =
    Position(51.5 + metres / 111195.0, -0.12, 20.0, None, None, None, minutes * 60000L)

  private object Heard extends BackgroundApp {
    var events: List[VisitEvent] = Nil
    override def onVisit(event: VisitEvent): Task[Unit] = Task { events = events :+ event }
  }

  /** Visits as a start on a platform that has them leaves them. */
  private def started(): Unit = Platform.saveSetting("everyui.visits.options", options.written)

  private def clean(): Unit = {
    Visits.stop()
    Work.cancel(Visits.checkKey)
    Background.forget()
    Heard.events = Nil
  }

  override def afterAll(): Unit = {
    fx(clean())
    super.afterAll()
  }

  "Visits" should {
    "be off until started, and refuse to start where there are none" in fx {
      clean()
      Visits.supported shouldBe false
      Visits.running shouldBe false
      Visits.start(options) shouldBe false
      Visits.running shouldBe false
    }

    "tell the app's BackgroundApp what the positions heard make of the stay" in fx {
      clean()
      Background.start(Heard)
      started()
      Visits.heard(List(at(0, 0), at(10, 5))).start()
      Heard.events shouldBe Nil
      Visits.heard(List(at(1500, 30), at(20, 12))).start()
      Heard.events.map(_.getClass.getSimpleName) shouldBe List("Arrived", "Departed")
      Heard.events(1).visit.departed shouldBe Some(12 * 60000L)
    }

    "hear nothing once stopped, and forget the stay" in fx {
      clean()
      Background.start(Heard)
      started()
      Visits.heard(List(at(0, 0))).start()
      Platform.setting("everyui.visits.dwell") should not be ""
      Visits.stop()
      Visits.running shouldBe false
      Platform.setting("everyui.visits.dwell") shouldBe ""
      Visits.heard(List(at(5, 15))).start()
      Heard.events shouldBe Nil
    }

    "stop by themselves where the location with the app closed is not allowed, in their work or as they resume" in fx {
      clean()
      started()
      var result: Option[WorkResult] = None
      Background.work(Visits.workKey, Map.empty).map(r => result = Some(r)).start()
      result shouldBe Some(WorkResult.Done)
      Visits.running shouldBe false
      started()
      Visits.resume()
      Visits.running shouldBe false
      // nothing more, and nothing asked: the work with no visits running is done at once
      Background.work(Visits.workKey, Map.empty).map(r => result = Some(r)).start()
      result shouldBe Some(WorkResult.Done)
    }
  }
}

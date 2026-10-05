package everyui.perf

import everyui.FxSpec
import reactify.*

/** The level the app runs at, and how the session steps itself down: the level given at start applies, one slow window
  * changes nothing, a run of them steps down one level and no further, nothing steps back up within the session, and
  * the summary says what was measured, None where nothing was. The windows are made here, or measured from frames and
  * keys at times the spec gives, with no clock. */
class QualitySpec extends FxSpec {
  private def window(keys: Int = 10, keyP95: Double = 100.0, frames: Int = 600, dropPct: Double = 0.0,
                     memory: String = "normal"): PerfWindow =
    PerfWindow(0L, 60.0, keys, keyP95 / 2.0, keyP95, frames, 8.0, 17.0, dropPct, -1.0, memory, 40, Quality())

  private def fresh(): Unit = fx {
    Quality.forget()
    Perf.restart(0.0)
  }

  /** `count` windows of `w`, judged one after another a minute apart. */
  private def judged(count: Int, w: => PerfWindow): Unit = fx((1 to count).foreach(i => Quality.judge(w, i * 60.0)))

  "A histogram" should {
    "read percentiles within their buckets, and -1 with nothing taken" in {
      val h = new PerfHistogram
      h.percentile(0.5) shouldBe -1.0
      (1 to 100).foreach(i => h.add(i.toDouble))
      h.count shouldBe 100
      h.percentile(0.5) shouldBe 50.0 +- 6.0
      h.percentile(0.95) shouldBe 95.0 +- 6.0
      h.percentile(1.0) shouldBe 100.0
      h.add(9000.0)
      h.percentile(1.0) shouldBe 9000.0
    }
  }

  "The policy" should {
    "call for Reduced and Minimal at the server's thresholds, and never on too few keys or frames" in {
      val p = QualityPolicy()
      p.wants(window()) shouldBe QualityLevel.Full
      p.wants(window(keyP95 = 320.0)) shouldBe QualityLevel.Reduced
      p.wants(window(keyP95 = 650.0)) shouldBe QualityLevel.Minimal
      p.wants(window(dropPct = 9.0)) shouldBe QualityLevel.Reduced
      p.wants(window(dropPct = 25.0)) shouldBe QualityLevel.Minimal
      p.wants(window(memory = "low")) shouldBe QualityLevel.Reduced
      p.wants(window(memory = "critical")) shouldBe QualityLevel.Minimal
      p.wants(window(keys = 2, keyP95 = 900.0)) shouldBe QualityLevel.Full
      p.wants(window(frames = 10, dropPct = 90.0)) shouldBe QualityLevel.Full
      p.why(window(keyP95 = 420.0, dropPct = 12.0)) shouldBe "key p95 420ms >= 300, drop 12% >= 8"
    }
  }

  "The quality level" should {
    "start at the level given, and at Full for none" in {
      fresh()
      fx(Quality.start(Some(QualityLevel.Reduced)))
      fx(Quality()) shouldBe QualityLevel.Reduced
      fx(Quality.level()) shouldBe QualityLevel.Reduced
      fx(Quality.start(None))
      fx(Quality()) shouldBe QualityLevel.Full
      fx(Quality.start(Some(QualityLevel.Minimal)))
      fx(Quality()) shouldBe QualityLevel.Minimal
      fx(Quality.steppedDown) shouldBe false
    }
    "not step down for one bad minute, nor for slow windows that are not in a row" in {
      fresh()
      fx(Quality.start(None))
      judged(1, window(keyP95 = 900.0))
      judged(2, window())
      judged(2, window(keyP95 = 900.0))
      judged(1, window())
      judged(2, window(dropPct = 30.0))
      fx(Quality()) shouldBe QualityLevel.Full
      fx(Quality.steppedDown) shouldBe false
    }
    "step down one level after a sustained run, however slow, and once more after another" in {
      fresh()
      fx(Quality.start(None))
      val heard = fx {
        var levels = List.empty[QualityLevel]
        Quality.level.attach(l => levels = levels :+ l)
        () => levels
      }
      judged(3, window(keyP95 = 900.0))
      fx(Quality()) shouldBe QualityLevel.Reduced
      fx(Quality.steppedDown) shouldBe true
      fx(Quality.stepEvidence) should include("Full -> Reduced")
      fx(Quality.stepEvidence) should include("key p95 900ms >= 600")
      // a window calling for Reduced is no reason to go lower than Reduced
      judged(5, window(keyP95 = 400.0))
      fx(Quality()) shouldBe QualityLevel.Reduced
      judged(2, window(keyP95 = 900.0))
      fx(Quality()) shouldBe QualityLevel.Reduced
      judged(1, window(keyP95 = 900.0))
      fx(Quality()) shouldBe QualityLevel.Minimal
      heard() shouldBe List(QualityLevel.Reduced, QualityLevel.Minimal)
    }
    "never step back up within the session, though the windows are fine again or a higher level is given" in {
      fresh()
      fx(Quality.start(None))
      judged(3, window(memory = "low"))
      fx(Quality()) shouldBe QualityLevel.Reduced
      judged(10, window())
      fx(Quality()) shouldBe QualityLevel.Reduced
      fx(Quality.start(Some(QualityLevel.Full)))
      fx(Quality()) shouldBe QualityLevel.Reduced
      // a lower level given still applies
      fx(Quality.start(Some(QualityLevel.Minimal)))
      fx(Quality()) shouldBe QualityLevel.Minimal
    }
    "not step at all where the policy says not to" in {
      fresh()
      fx {
        Quality.start(None)
        Quality.policy = QualityPolicy(steps = false)
      }
      judged(6, window(keyP95 = 900.0))
      fx(Quality()) shouldBe QualityLevel.Full
    }
  }

  "The session's measures" should {
    "time a key to the frame after the one that drew it, and close a window each minute" in {
      fresh()
      fx(Quality.start(None))
      val closed = fx {
        var ws = List.empty[PerfWindow]
        Perf.windows.attach(w => ws = ws :+ w)
        () => ws
      }
      fx {
        Perf.restart(0.0)
        Perf.keyedAt(1000.0)
        // the frame that handles it begins, then the next once that one is drawn: 250ms in all
        Perf.frame(16.0, 4.0, 1100.0)
        Perf.frame(150.0, 4.0, 1250.0)
        // frames at 60 a second for the rest of the minute, a tenth of them dropped
        var t = 1250.0
        var i = 0
        while (t < 61000.0) {
          val ms = if (i % 10 == 0) 50.0 else 16.0
          t += ms
          Perf.frame(ms, 3.0, t)
          i += 1
        }
      }
      closed().size shouldBe 1
      val w = closed().head
      w.keys shouldBe 1
      w.keyP95 shouldBe 250.0 +- 0.5
      w.dropPct shouldBe 10.0 +- 1.5
      w.frameP95 should (be >= 40.0 and be <= 50.0)
      w.updateP95 shouldBe 3.0 +- 1.0
      w.line should include("keys 1 p50/p95")
    }
    "say how the session went, with the step down and its evidence, and None for what it did not measure" in {
      fresh()
      fx(Quality.start(None))
      val sent = fx {
        var ss = List.empty[PerfSession]
        Perf.summaries.attach(s => ss = ss :+ s)
        () => ss
      }
      // nothing measured yet: nothing said
      fx(Perf.summarise())
      sent() shouldBe Nil
      // frames, no keys
      fx((1 to 100).foreach(i => Perf.frame(16.0, 2.0, i * 16.0)))
      fx(Perf.summarise())
      val quiet = sent().last
      quiet.keyLatencyMsP50 shouldBe None
      quiet.keyLatencyMsP95 shouldBe None
      quiet.frameDropPct shouldBe Some(0)
      quiet.frames shouldBe Some(100)
      quiet.levelGiven shouldBe Some("Full")
      quiet.levelRun shouldBe Some("Full")
      quiet.steppedDown shouldBe Some(false)
      quiet.memoryLevel shouldBe None
      quiet.platform shouldBe Some("Desktop")
      quiet.sessionId should not be empty
      // three slow minutes: keys answered in 700ms
      fx {
        var t = 2000.0
        while (t < 200000.0) {
          Perf.keyedAt(t)
          Perf.frame(16.0, 2.0, t + 300.0)
          Perf.frame(16.0, 2.0, t + 700.0)
          t += 1000.0
        }
      }
      fx(Quality()) shouldBe QualityLevel.Reduced
      fx(Perf.summarise())
      val slow = sent().last
      slow.sessionId shouldBe quiet.sessionId
      slow.keyLatencyMsP95.get should be >= 650
      slow.keyLatencyMsP50.get should be >= 650
      slow.levelGiven shouldBe Some("Full")
      slow.levelRun shouldBe Some("Reduced")
      slow.steppedDown shouldBe Some(true)
      slow.evidence.get should include("stepped Full -> Reduced")
      slow.evidence.get should include("key p95")
      slow.sessionSeconds.get should be >= 0
    }
  }

  override def afterAll(): Unit = {
    fx(Quality.forget())
    super.afterAll()
  }
}

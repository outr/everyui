package everyui.perf

import everyui.FxSpec

/** The frames line and the memory line, as NaboTV's FrameWatch writes the first: the slow frames of the window and how
  * many, the worst with its update and the rest, the screen, and the frames dropped against the display's refresh;
  * then the heap, the components and the pictures. A window closes after ten seconds of frames and the next counts
  * afresh. Frames are given here at times the spec chooses. */
class FrameLogSpec extends FxSpec {
  override def afterAll(): Unit = {
    fx(FrameLog.stop())
    super.afterAll()
  }

  "The frame log" should {
    "write the frames of a window: slow ones, the worst split into build and raster, the screen, and drops" in {
      val line = fx {
        FrameLog.stop()
        (1 to 10).foreach(i => FrameLog.frame(16.7, 2.0, i * 16.7))
        FrameLog.frame(84.3, 40.1, 300.0)
        FrameLog.frame(33.0, 3.0, 340.0)
        // the app away for a minute is no frame it drew
        FrameLog.frame(60000.0, 0.0, 400.0)
        FrameLog.framesLine()
      }
      // the desktop says no refresh, so a frame is dropped at 40ms
      line should startWith("frames: 2/12 slow (17%) worst 84.3ms = build 40.1ms + raster 44.2ms screen ")
      line should fullyMatch regex """frames: .* screen \d+x\d+@\d+\.\d, 1 dropped \(8%\) at 40ms"""
    }
    "count afresh once a window has closed" in {
      val line = fx {
        FrameLog.stop()
        FrameLog.frame(100.0, 1.0, 0.0)
        FrameLog.frame(16.0, 1.0, 10000.0)
        FrameLog.framesLine()
      }
      line should startWith("frames: 0/0 slow (0%) worst 0.0ms")
    }
    "write the memory: the heap, the components and the pictures" in {
      fx(FrameLog.memoryLine()) should fullyMatch regex
        """memory: heap \d+\.\d of \d+ MB \(\d+%\), \d+ components, pictures \d+\.\d MB in \d+, \d+ kept out of sight \d+\.\d MB"""
    }
    "write tenths and megabytes as a person reads them" in {
      FrameLog.tenths(84.349) shouldBe "84.3"
      FrameLog.tenths(16.0) shouldBe "16.0"
      FrameLog.megabytes(1048576.0 * 2.25) shouldBe "2.3"
    }
  }
}

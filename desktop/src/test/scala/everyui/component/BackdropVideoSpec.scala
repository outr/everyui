package everyui.component

import everyui.motion.ReduceMotion
import everyui.render.{Nodes, Platform}
import everyui.{FxSpec, ui}

/** A backdrop's video (`videoSource`), where the platform plays one behind a screen: made behind the picture, out of
  * sight, the picture showing until the video can play through, then replaced in one step or faded off it; the picture
  * staying where the video fails, and coming back once the source is emptied or people ask for less motion; paused
  * while the app is away; none of it where the platform plays none. The video is a stand-in, told what became of it. */
class BackdropVideoSpec extends FxSpec {
  private val clip = "/backgrounds/void-blue-veil-loop.webm"

  private def withVideo[T](plays: Boolean = true)(test: Backdrop => T): T = {
    val was = (Backdrop.plays, Backdrop.makesVideo)
    Backdrop.plays = () => plays
    Backdrop.makesVideo = () => new VideoView(Nodes.container())
    val b = fx {
      val made = new Backdrop
      made.size.width @= 640.0
      made.size.height @= 360.0
      ui.children.clear()
      ui.children += made
      made
    }
    try test(b) finally {
      fx {
        ui.children.clear()
        b.dispose()
        ReduceMotion.on @= false
      }
      Backdrop.plays = was._1
      Backdrop.makesVideo = was._2
    }
  }

  private def picture(b: Backdrop): ImageView = b.children().collectFirst { case v: ImageView => v }.get
  private def video(b: Backdrop): Option[VideoView] = b.children().collectFirst { case v: VideoView => v }

  "A backdrop's video" should {
    "wait out of sight behind the picture, and take its place in one step once it can play through" in withVideo() { b =>
      fx(b.videoSource @= clip)
      val v = fx(video(b)).get
      fx(b.children().head) shouldBe v
      fx(v.source()) shouldBe clip
      fx(v.muted()) shouldBe true
      fx(v.loop()) shouldBe true
      fx(v.opacity()) shouldBe 0.0
      fx(picture(b).visible()) shouldBe true
      fx(b.videoShown()) shouldBe false
      fx(b.heard(clip, failedNow = false))
      fx(b.videoShown()) shouldBe true
      fx(v.opacity()) shouldBe 1.0
      fx(picture(b).visible()) shouldBe false
      // asked for again, it is left playing
      fx(b.videoSource @= clip)
      fx(b.videoShown()) shouldBe true
      fx(v.source()) shouldBe clip
    }
    "fade the picture off it with crossfade" in withVideo() { b =>
      fx {
        b.crossfade @= true
        b.videoSource @= clip
        b.heard(clip, failedNow = false)
      }
      fx(video(b).get.opacity()) shouldBe 1.0
      eventually(!picture(b).visible()) shouldBe true
      fx(b.videoShown()) shouldBe true
    }
    "keep the picture where it fails, and where it fails once it is up" in withVideo() { b =>
      fx(b.videoSource @= clip)
      fx(b.heard(clip, failedNow = true))
      fx(b.videoFailed()) shouldBe true
      fx(b.videoShown()) shouldBe false
      fx(picture(b).visible()) shouldBe true
      fx(video(b).get.source()) shouldBe ""
      // what became of a video since let go is not heard
      fx(b.heard(clip, failedNow = false))
      fx(b.videoShown()) shouldBe false
      fx(b.videoSource @= clip + "?again")
      fx(b.videoFailed()) shouldBe false
    }
    "give the picture back once its source is emptied, and wait again for the next" in withVideo() { b =>
      fx {
        b.videoSource @= clip
        b.heard(clip, failedNow = false)
        b.videoSource @= ""
      }
      fx(b.videoShown()) shouldBe false
      fx(picture(b).visible()) shouldBe true
      fx(picture(b).opacity()) shouldBe 1.0
      fx(video(b).get.opacity()) shouldBe 0.0
      fx(video(b).get.source()) shouldBe ""
      fx(b.videoSource @= clip)
      fx(video(b).get.source()) shouldBe clip
      fx(b.videoShown()) shouldBe false
      fx(b.heard(clip, failedNow = false))
      fx(b.videoShown()) shouldBe true
    }
    "give the picture back while people ask for less motion, and play again after" in withVideo() { b =>
      fx {
        b.videoSource @= clip
        b.heard(clip, failedNow = false)
        ReduceMotion.on @= true
      }
      fx(b.videoShown()) shouldBe false
      fx(picture(b).visible()) shouldBe true
      fx(video(b).get.source()) shouldBe ""
      fx(ReduceMotion.on @= false)
      fx(video(b).get.source()) shouldBe clip
      fx(b.heard(clip, failedNow = false))
      fx(b.videoShown()) shouldBe true
    }
    "pause while the app is away, and play on as it comes back" in withVideo() { b =>
      fx {
        b.videoSource @= clip
        b.heard(clip, failedNow = false)
      }
      fx(Platform.away())
      fx(video(b).get.playing()) shouldBe false
      fx(Platform.resumed())
      fx(video(b).get.playing()) shouldBe true
    }
    "be none where the platform plays none, the picture as it was" in withVideo(plays = false) { b =>
      fx(b.videoSource @= clip)
      fx(video(b)) shouldBe None
      fx(picture(b).visible()) shouldBe true
      fx(b.videoShown()) shouldBe false
    }
  }
}

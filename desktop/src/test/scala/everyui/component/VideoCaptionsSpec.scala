package everyui.component

import everyui.FxSpec
import everyui.component.TextChoice.*

/** A video's captions chosen as a TextChoice, with textTrack the same choice as an id, each following the other. */
class VideoCaptionsSpec extends FxSpec {
  "A video's captions" should {
    "start as the device's, with textTrack None" in {
      val video = fx(new VideoView)
      fx(video.captions()) shouldBe TextFromDevice
      fx(video.textTrack()) shouldBe None
    }
    "be said through textTrack as None, Some(\"\") and Some(id)" in {
      val video = fx(new VideoView)
      fx(video.captions @= TextOff)
      fx(video.textTrack()) shouldBe Some("")
      fx(video.captions @= TextTrack("t1"))
      fx(video.textTrack()) shouldBe Some("t1")
      fx(video.captions @= TextFromDevice)
      fx(video.textTrack()) shouldBe None
    }
    "follow textTrack set as code written before captions sets it" in {
      val video = fx(new VideoView)
      fx(video.textTrack @= Some("t2"))
      fx(video.captions()) shouldBe TextTrack("t2")
      // a track set afresh, through none first, as a player re-picks one on a new source
      fx {
        video.textTrack @= Some("")
        video.textTrack @= Some("t2")
      }
      fx(video.captions()) shouldBe TextTrack("t2")
      fx(video.textTrack @= Some(""))
      fx(video.captions()) shouldBe TextOff
      // None is the device's, not none
      fx(video.textTrack @= None)
      fx(video.captions()) shouldBe TextFromDevice
    }
  }
}

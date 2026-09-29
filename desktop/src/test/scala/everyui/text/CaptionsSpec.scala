package everyui.text

import everyui.component.{MediaTrack, TextChoice}
import everyui.text.CaptionMode.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Which text track a video reads: the app's choice where it made one, else the device's caption setting, in its
  * language or the first track there is; and the device's setting as each platform names it. */
class CaptionsSpec extends AnyWordSpec with Matchers {
  private val tracks = List(
    MediaTrack("a1", MediaTrack.Audio, "fr"),
    MediaTrack("t1", MediaTrack.Text, "fre", "Français"),
    MediaTrack("t2", MediaTrack.Text, "eng", "English"),
    MediaTrack("t3", MediaTrack.Text, "es"))
  private def device(mode: CaptionMode, language: String = ""): Option[CaptionPreference] =
    Some(CaptionPreference(mode, if (language.isEmpty) None else Some(language)))

  "Choosing captions" should {
    "read the track the app chose, whatever the device says" in {
      Captions.choose(TextChoice.TextTrack("t3"), device(CaptionsOff), tracks) shouldBe Some("t3")
      Captions.choose(TextChoice.TextTrack("t3"), device(CaptionsOn, "en"), tracks) shouldBe Some("t3")
      Captions.choose(TextChoice.TextTrack("t3"), None, tracks) shouldBe Some("t3")
    }
    "read none where the app chose none, even with the device's on" in {
      Captions.choose(TextChoice.TextOff, device(CaptionsOn, "en"), tracks) shouldBe None
      Captions.choose(TextChoice.TextTrack(""), device(CaptionsOn, "en"), tracks) shouldBe None
    }
    "with nothing chosen and the device's on, read the track in its language" in {
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn, "en"), tracks) shouldBe Some("t2")
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn, "fr"), tracks) shouldBe Some("t1")
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn, "es-MX"), tracks) shouldBe Some("t3")
    }
    "with nothing chosen and the device's on, read the first track where none is in its language, or it has none" in {
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn, "de"), tracks) shouldBe Some("t1")
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn), tracks) shouldBe Some("t1")
    }
    "with nothing chosen and the device's off, or unknown, read none" in {
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOff, "en"), tracks) shouldBe None
      Captions.choose(TextChoice.TextFromDevice, None, tracks) shouldBe None
    }
    "leave a mode that shows captions only at times to the platform" in {
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOnReplay, "en"), tracks) shouldBe None
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsWhenMuted, "en"), tracks) shouldBe None
      // the track to have ready for when it does
      Captions.deviceTrack(device(CaptionsWhenMuted, "en"), tracks) shouldBe Some("t2")
      Captions.deviceTrack(device(CaptionsOff, "en"), tracks) shouldBe None
    }
    "read nothing where there is no text track" in {
      val audio = tracks.filter(_.kind == MediaTrack.Audio)
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn, "fr"), audio) shouldBe None
      Captions.choose(TextChoice.TextFromDevice, device(CaptionsOn, "fr"), Nil) shouldBe None
    }
  }

  "A caption choice" should {
    "be read from textTrack and said back as it, both ways" in {
      TextChoice.fromTrack(None) shouldBe TextChoice.TextFromDevice
      TextChoice.fromTrack(Some("")) shouldBe TextChoice.TextOff
      TextChoice.fromTrack(Some("t2")) shouldBe TextChoice.TextTrack("t2")
      List(TextChoice.TextFromDevice, TextChoice.TextOff, TextChoice.TextTrack("t2"))
        .foreach(c => TextChoice.fromTrack(TextChoice.asTrack(c)) shouldBe c)
    }
  }

  "A caption mode" should {
    "be read from the names a Roku and an Apple TV give it" in {
      CaptionMode.named("On") shouldBe Some(CaptionsOn)
      CaptionMode.named("Off") shouldBe Some(CaptionsOff)
      CaptionMode.named("Instant replay") shouldBe Some(CaptionsOnReplay)
      CaptionMode.named("When mute") shouldBe Some(CaptionsWhenMuted)
      CaptionMode.named("Automatic") shouldBe Some(CaptionsAutomatic)
      CaptionMode.named("") shouldBe None
    }
  }

  "The desktop" should {
    "say it has no caption setting to tell, nor one to write" in {
      Captions.supported shouldBe false
      Captions.device shouldBe None
      Captions.writable shouldBe false
      Captions.setDevice(CaptionsOff) shouldBe false
    }
  }
}

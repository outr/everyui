package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer

/** Sound routed as a Roku routes it, against a fake output: Audio nodes taking turns at the one player (the newest
  * plays, the one it displaced waits and comes back where it was), and effects that mix over them, WAVs on the device
  * only, anything else reported and played through the player. */
class SoundTurnsSpec extends AnyWordSpec with Matchers {
  /** What each fake output was asked, in order, all of them in one list. */
  private class Rig {
    val calls: ListBuffer[String] = ListBuffer.empty
    val logged: ListBuffer[String] = ListBuffer.empty
    val stack: PlayerStack = new PlayerStack(line => logged += line)

    def turns(id: String): SoundTurns = new SoundTurns(new Fake(id), stack)

    class Fake(val ident: String) extends SoundOutput {
      private var file = ""
      def id(): String = ident
      def source(url: String): Unit = {
        file = url
        calls += ident + " source " + url
      }
      def playing(on: Boolean): Unit = calls += ident + " playing " + on
      def yieldPlayer(): Unit = calls += ident + " yields"
      def reclaimPlayer(): Unit = calls += ident + " reclaims"
      def handedOff(): Unit = calls += ident + " handed"
      def loadEffect(url: String): Unit = calls += ident + " load " + url
      def playEffect(url: String, volume: Double): Unit = calls += ident + " effect " + url + " " + volume
      def stopEffect(url: String): Unit = calls += ident + " stop " + url
      def report(key: String, message: String): Unit = calls += ident + " report " + message
    }

    def playingNow: List[String] = stack.playing().map(_.soloName()).toList
    def waitingNow: List[String] = stack.waiting().map(_.soloName())
  }

  "Turns at the player" should {
    "let the newest take it, the one it displaced stopping first, and give it back at its end" in {
      val r = new Rig
      val music = r.turns("music")
      val clip = r.turns("clip")
      music.setSource("pkg:/music.mp3")
      r.calls.toList shouldBe List("music source pkg:/music.mp3")
      r.calls.clear()
      clip.setSource("https://x/clip.mp3")
      // the music stops before the clip is given its source
      r.calls.toList shouldBe List("music yields", "clip handed", "clip source https://x/clip.mp3")
      r.playingNow shouldBe List("clip clip.mp3")
      r.waitingNow shouldBe List("music music.mp3")
      r.calls.clear()
      clip.heard("ready")
      r.calls.toList shouldBe Nil
      clip.heard("ended")
      r.calls.toList shouldBe List("music reclaims")
      r.playingNow shouldBe List("music music.mp3")
      r.logged.toList shouldBe List("audio: clip clip.mp3 takes the player from music music.mp3",
        "audio: music music.mp3 back from clip clip.mp3")
    }
    "give it back when the newcomer is stopped, fails or is let go, after it has stopped" in {
      List[SoundTurns => Unit](_.setPlaying(false), _.heard("error"), _.released(), _.setSource("")).foreach { stop =>
        val r = new Rig
        val music = r.turns("music")
        val clip = r.turns("clip")
        music.setSource("pkg:/music.mp3")
        clip.setSource("pkg:/clip.mp3")
        r.calls.clear()
        stop(clip)
        r.calls.toList.last shouldBe "music reclaims"
        r.playingNow shouldBe List("music music.mp3")
      }
    }
    "stack more than two, each coming back in turn" in {
      val r = new Rig
      val a = r.turns("a")
      val b = r.turns("b")
      val c = r.turns("c")
      a.setSource("pkg:/a.mp3")
      b.setSource("pkg:/b.mp3")
      c.setSource("pkg:/c.mp3")
      r.stack.describe() shouldBe "c c.mp3 over b b.mp3 over a a.mp3"
      r.calls.clear()
      c.heard("ended")
      b.heard("ended")
      r.calls.toList shouldBe List("b reclaims", "a reclaims")
    }
    "drop one stopped while it waits, so the newcomer's end brings nothing back" in {
      val r = new Rig
      val music = r.turns("music")
      val clip = r.turns("clip")
      music.setSource("pkg:/music.mp3")
      clip.setSource("pkg:/clip.mp3")
      music.setPlaying(false)
      r.stack.describe() shouldBe "clip clip.mp3"
      r.logged.last shouldBe "audio: music music.mp3 stopped while waiting"
      r.calls.clear()
      clip.heard("ended")
      r.calls.toList shouldBe Nil
      r.stack.describe() shouldBe "nothing"
    }
    "let one that waits take it back by playing again" in {
      val r = new Rig
      val music = r.turns("music")
      val clip = r.turns("clip")
      music.setSource("pkg:/music.mp3")
      clip.setSource("pkg:/clip.mp3")
      music.setPlaying(false)
      r.calls.clear()
      music.setPlaying(true)
      r.calls.toList shouldBe List("clip yields", "music handed", "music playing true")
      r.stack.describe() shouldBe "music music.mp3 over clip clip.mp3"
    }
    "not take it for a source set while not playing, nor again for the one that has it" in {
      val r = new Rig
      val music = r.turns("music")
      val clip = r.turns("clip")
      music.setSource("pkg:/music.mp3")
      clip.setPlaying(false)
      clip.setSource("pkg:/clip.mp3")
      r.stack.describe() shouldBe "music music.mp3"
      r.calls.clear()
      music.setPlaying(true)
      r.calls.toList shouldBe List("music playing true")
    }
  }

  "An effect" should {
    "mix a WAV on the device, never touching the player" in {
      val r = new Rig
      val music = r.turns("music")
      val fx = r.turns("fx")
      music.setSource("pkg:/music.mp3")
      fx.setEffect(true)
      fx.setVolume(0.85)
      r.calls.clear()
      fx.setSource("pkg:/sounds/cheer.wav")
      r.calls.toList shouldBe List("fx effect pkg:/sounds/cheer.wav 0.85")
      r.stack.describe() shouldBe "music music.mp3"
    }
    "play when turned on after its source, once, and stop when turned off" in {
      val r = new Rig
      val fx = r.turns("fx")
      fx.setEffect(true)
      fx.setPlaying(false)
      r.calls.clear()
      fx.setSource("tmp:/cheer.WAV?v=2")
      r.calls.toList shouldBe List("fx load tmp:/cheer.WAV?v=2")
      r.calls.clear()
      fx.setPlaying(true)
      fx.setPlaying(true)
      fx.setPlaying(false)
      r.calls.toList shouldBe List("fx effect tmp:/cheer.WAV?v=2 1.0", "fx stop tmp:/cheer.WAV?v=2")
    }
    "stay quiet when woken part way in, as a player put away and shown again is" in {
      val r = new Rig
      val fx = r.turns("fx")
      fx.setEffect(true)
      fx.setStart(0.4)
      fx.setSource("pkg:/cheer.wav")
      r.calls.toList shouldBe List("fx load pkg:/cheer.wav")
    }
    "report one it cannot mix and play it through the player" in {
      List("pkg:/sounds/fx_fail1.mp3" -> "not a WAV", "https://nabo.tv/fx.wav" -> "an address").foreach { (url, why) =>
        val r = new Rig
        val music = r.turns("music")
        val fx = r.turns("fx")
        music.setSource("pkg:/music.mp3")
        fx.setEffect(true)
        r.calls.clear()
        fx.setSource(url)
        r.calls.head should startWith("fx report audio: fx effect " + url + " is " + why)
        r.calls.toList.tail shouldBe List("music yields", "fx handed", "fx source " + url)
        fx.heard("ended")
        r.stack.describe() shouldBe "music music.mp3"
      }
    }
    "be an ordinary player when not an effect, whatever its file" in {
      val r = new Rig
      val fx = r.turns("fx")
      fx.setSource("pkg:/cheer.wav")
      r.calls.toList shouldBe List("fx source pkg:/cheer.wav")
      r.stack.describe() shouldBe "fx cheer.wav"
    }
  }

  "EffectSource" should {
    "know a WAV on the device from anything else" in {
      EffectSource.mixes("pkg:/sounds/a.wav") shouldBe true
      EffectSource.mixes("cachefs:/trivia/A.WAV?x=1") shouldBe true
      EffectSource.mixes("pkg:/sounds/a.mp3") shouldBe false
      EffectSource.mixes("http://host/a.wav") shouldBe false
      EffectSource.mixes("HTTPS://host/a.wav") shouldBe false
      EffectSource.mixes("") shouldBe false
      EffectSource.unfit("pkg:/a.wav") shouldBe ""
    }
  }
}

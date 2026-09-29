package everyui.app

import everyui.audio.{AudioChunk, Capture, CaptureEnd, Microphone, Pcm, PcmPlayer}
import everyui.component.{Button, ButtonStyle, Checkbox, Column, ProgressBar, Row, TextView}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Voice audio: a record button with a level meter, what was recorded played back (and, with Loopback ticked, played
  * as it is heard), and a tone streamed in chunks the way text to speech arrives, which Stop cuts off at once
  * (barge-in). Everything it hears is logged: `[ui] voice chunk n=2048 rate=16000 level=0.123`, `voice ended Stopped`,
  * `voice drained`, `voice stopped`. `theme=light` shows the light palette; `probe=1` runs it by itself (a second of
  * recording, the recording played back, a tone to its end, a tone stopped part way) and logs `voice probe done`. */
class VoiceDemo {
  private val rate = 16000
  private val frames = 2048
  private val recording: Var[Option[Capture]] = Var(None)
  private val level: Var[Double] = Var(0.0)
  private val heard: Var[String] = Var("Nothing recorded yet")
  private val drains: Var[Int] = Var(0)
  private var recorded: Vector[Array[Short]] = Vector.empty
  private val loopback = new Checkbox("Loopback: play what is heard as it is heard")
  // the recording's player at the capture's rate, the tone's at text to speech's usual 24 kHz
  private val voice = new PcmPlayer(rate)
  private val tones = new PcmPlayer(24000)
  // the tone's chunks still to come: each run is numbered, so a stop ends the run it stopped
  private var toneRun: Int = 0

  List(voice, tones).foreach { p =>
    p.drained.attach { _ =>
      drains @= drains() + 1
      Platform.log("voice drained " + (if (p eq voice) "recording" else "tone"))
    }
  }

  private val meter = new ProgressBar(320.0, 10.0)
  meter.fraction := math.min(1.0, level() * 4.0)

  private val record = Button.bound(if (recording().nonEmpty) "Stop recording" else "Record", () => toggle(),
    ButtonStyle.Filled)
  private val replay = new Button("Play back", () => playBack())
  private val tone = new Button("Play tone", () => playTone(3.0))
  private val stop = new Button("Stop", () => bargeIn())
  private val quieter = Button.bound("Volume " + (tones.volume() * 100).round.toString + "%",
    () => {
      val v = if (tones.volume() > 0.3) 0.25 else 1.0
      voice.volume @= v
      tones.volume @= v
    })

  private val column = Column(14.0, padding = 24.0)(
    TextView("Voice", 22.0),
    TextView("Microphone " + (if (Microphone.supported) "supported" else "not supported here") + "; playback " +
      (if (PcmPlayer.supported) "supported" else "not supported here"), 13.0, Theme.muted()),
    Row(12.0, middle = true)(meter, record),
    TextView.bound(heard(), 13.0, Theme.muted()),
    loopback,
    Row(12.0, middle = true)(replay, tone, stop, quieter),
    TextView.bound((if (voice.playing() || tones.playing()) "Playing" else "Quiet") + " · drained " + drains().toString +
      " times", 13.0, Theme.muted()),
    TextView("Start voice from a tap: a browser plays nothing until the page has been touched.", 12.0, Theme.muted())
  )
  ui.children += column

  private def toggle(): Unit = recording() match {
    case Some(c) => c.stop()
    case None => start()
  }

  private def start(): Unit = {
    recorded = Vector.empty
    heard @= "Opening the microphone..."
    recording @= Some(Microphone.capture(rate, frames)(chunk => got(chunk), why => ended(why)))
  }

  private def got(chunk: AudioChunk): Unit = {
    recorded :+= chunk.samples
    level @= chunk.level
    val seconds = recorded.map(_.length).sum.toDouble / rate
    heard @= recorded.length.toString + " chunks, " + f"$seconds%.1f" + " s at " + chunk.sampleRate.toString +
      " Hz, level " + f"${chunk.level}%.3f"
    Platform.log("voice chunk n=" + chunk.samples.length.toString + " rate=" + chunk.sampleRate.toString + " level=" +
      f"${chunk.level}%.4f" + " bytes=" + chunk.bytes.length.toString)
    if (loopback.checked()) voice.play(chunk.samples)
  }

  private def ended(why: CaptureEnd): Unit = {
    recording @= None
    level @= 0.0
    Platform.log("voice ended " + why.toString)
    if (why != CaptureEnd.Stopped) heard @= "Ended: " + why.toString
  }

  private def playBack(): Unit = recorded.foreach(voice.play)

  /** A 440 Hz tone, `seconds` long, handed over in 100 ms chunks a little faster than it plays, as speech streams in. */
  private def playTone(seconds: Double): Unit = {
    toneRun += 1
    val run = toneRun
    val chunks = Pcm.tone(440.0, seconds, 24000, 0.1).grouped(2400).toVector
    chunks.zipWithIndex.foreach { case (c, i) =>
      Platform.after(i * 0.08, () => if (run == toneRun) {
        tones.play(c)
        if (i == chunks.length - 1) Platform.log("voice tone queued " + chunks.length.toString + " chunks")
      })
    }
  }

  private def bargeIn(): Unit = {
    toneRun += 1
    voice.stop()
    tones.stop()
    Platform.log("voice stopped playing=" + (voice.playing() || tones.playing()).toString)
  }

  /** The demo run by itself, for a headless browser with a fake microphone. */
  def probe(): Unit = {
    start()
    Platform.after(1.2, () => {
      recording().foreach(_.stop())
      playBack()
    })
    Platform.after(3.0, () => playTone(0.5))
    Platform.after(4.5, () => playTone(3.0))
    Platform.after(5.0, () => bargeIn())
    Platform.after(6.5, () => Platform.log("voice probe done drained=" + drains().toString + " playing=" +
      (voice.playing() || tones.playing()).toString))
  }
}

object VoiceDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val demo = new VoiceDemo
    if (Platform.launchArg("probe") == "1") demo.probe()
  }
}

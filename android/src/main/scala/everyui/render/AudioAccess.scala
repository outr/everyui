package everyui.render

import android.media.audiofx.AcousticEchoCanceler
import android.media.{AudioFormat, AudioRecord}
import everyui.audio.{CaptureEnd, PcmOutput}

import java.util.concurrent.atomic.AtomicBoolean

/** Voice audio (`everyui.audio`) through Android's own: `AudioRecord` to record, a streaming `AudioTrack` for each
  * player ([[TrackOutput]]). The microphone is read on a thread of its own and heard on the one that draws.
  *
  * Recording takes RECORD_AUDIO, which the app's manifest declares and which is asked for the first time a capture
  * starts ([[Permissions]]). With echo cancelling it records from VOICE_COMMUNICATION, the source a device tunes for
  * calls (its echo cancelling and noise suppression), and turns on an AcousticEchoCanceler where the device has one;
  * without, from VOICE_RECOGNITION, tuned for speech to text. It records at the rate asked for where the device
  * takes it, and at 48 or 44.1 kHz where it does not (the shared layer resamples). */
object AudioAccess {
  // MediaRecorder.AudioSource's, which Scala does not see in the SDK's stubs
  private val voiceRecognition = 6
  private val voiceCommunication = 7

  private def rates(asked: Int): List[Int] = List(asked, 48000, 44100, 16000).distinct

  def capturable(): Boolean =
    try Host.activity.getPackageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)
    catch { case _: Throwable => false }

  def playable(): Boolean = true

  def capture(rate: Int, echoCancellation: Boolean, opened: Int => Unit, heard: Array[Float] => Unit,
              ended: CaptureEnd => Unit): () => Unit = {
    val running = new AtomicBoolean(true)
    if (!capturable()) ended(CaptureEnd.Unavailable)
    else Permissions.ask(android.Manifest.permission.RECORD_AUDIO) { granted =>
      if (!granted) ended(CaptureEnd.Denied)
      else if (running.get) {
        val source = if (echoCancellation) voiceCommunication else voiceRecognition
        record(source, rate) match {
          case None => ended(CaptureEnd.Unavailable)
          case Some(recorder) =>
            val canceller =
              if (echoCancellation && AcousticEchoCanceler.isAvailable)
                Option(AcousticEchoCanceler.create(recorder.getAudioSessionId)).map { c =>
                  c.setEnabled(true)
                  c
                }
              else None
            val deviceRate = recorder.getSampleRate
            opened(deviceRate)
            val reader = new Thread(() => {
              // about 20 ms a read, so the level keeps up with the voice
              val samples = new Array[Short](math.max(1, deviceRate / 50))
              var why: Option[CaptureEnd] = None
              try {
                recorder.startRecording()
                if (recorder.getRecordingState != AudioRecord.RECORDSTATE_RECORDING) why = Some(CaptureEnd.Unavailable)
                while (running.get && why.isEmpty) {
                  val n = recorder.read(samples, 0, samples.length)
                  if (n < 0) why = Some(if (n == AudioRecord.ERROR_DEAD_OBJECT) CaptureEnd.Lost else CaptureEnd.Failed("AudioRecord.read " + n))
                  else if (n > 0) {
                    val block = new Array[Float](n)
                    var i = 0
                    while (i < n) {
                      block(i) = everyui.audio.Pcm.toFloat(samples(i))
                      i += 1
                    }
                    Platform.later(() => heard(block))
                  }
                }
              }
              catch { case t: Throwable => why = Some(CaptureEnd.Failed(String.valueOf(t.getMessage))) }
              finally {
                try recorder.stop() catch { case _: Throwable => () }
                recorder.release()
                canceller.foreach(_.release())
              }
              why.foreach(w => Platform.later(() => ended(w)))
            }, "everyui-pcm-in")
            reader.setDaemon(true)
            reader.start()
        }
      }
    }
    () => running.set(false)
  }

  /** A recorder on `source` at `rate` or the nearest the device takes. */
  private def record(source: Int, rate: Int): Option[AudioRecord] = rates(rate).iterator.flatMap { r =>
    val least = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (least <= 0) None
    else try {
      val recorder = new AudioRecord(source, r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        math.max(least, r / 5 * 2))
      if (recorder.getState == AudioRecord.STATE_INITIALIZED) Some(recorder)
      else {
        recorder.release()
        None
      }
    }
    catch { case _: Throwable => None }
  }.nextOption()

  def output(sampleRate: Int, drained: Long => Unit): PcmOutput = new TrackOutput(sampleRate, drained)
}

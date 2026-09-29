package everyui.render

import everyui.audio.{CaptureEnd, PcmOutput}

import javax.sound.sampled.{AudioFormat, AudioSystem, DataLine, LineUnavailableException, SourceDataLine, TargetDataLine}

/** Voice audio (`everyui.audio`) through Java Sound: the default `TargetDataLine` to record, a `SourceDataLine` for
  * each player ([[LineOutput]]). A line is read on a thread of its own and heard on the one that draws.
  *
  * The line is opened at the rate asked for where the system takes it, and at 48 or 44.1 kHz where it does not (the
  * shared layer resamples). Java Sound has no echo cancelling: talking over a player wants a headset, or the sound
  * server's own (PipeWire's echo-cancel module). A Mac asks for the microphone the first time a line is opened, and
  * a line that answers nothing but silence there has been refused. */
object AudioAccess {
  /** 16-bit signed little-endian mono at `rate`. */
  def format(rate: Int): AudioFormat = new AudioFormat(rate.toFloat, 16, 1, true, false)

  private def rates(asked: Int): List[Int] = List(asked, 48000, 44100).distinct

  def capturable(): Boolean =
    try rates(16000).exists(r => AudioSystem.isLineSupported(new DataLine.Info(classOf[TargetDataLine], format(r))))
    catch { case _: Throwable => false }

  def playable(): Boolean =
    try rates(24000).exists(r => AudioSystem.isLineSupported(new DataLine.Info(classOf[SourceDataLine], format(r))))
    catch { case _: Throwable => false }

  def capture(rate: Int, echoCancellation: Boolean, opened: Int => Unit, heard: Array[Float] => Unit,
              ended: CaptureEnd => Unit): () => Unit = {
    val running = new java.util.concurrent.atomic.AtomicBoolean(true)
    Platform.offThread(() => {
      val line = open(rate)
      line match {
        case None => Platform.later(() => ended(CaptureEnd.Unavailable))
        case Some(l) =>
          val deviceRate = l.getFormat.getSampleRate.toInt
          Platform.later(() => opened(deviceRate))
          // about 20 ms a read, so the level keeps up with the voice
          val bytes = new Array[Byte](math.max(2, deviceRate / 50 * 2))
          var why: Option[CaptureEnd] = None
          try {
            l.start()
            while (running.get && why.isEmpty) {
              val n = l.read(bytes, 0, bytes.length)
              if (n <= 0) {
                if (running.get) why = Some(CaptureEnd.Lost)
              }
              else {
                val block = new Array[Float](n / 2)
                var i = 0
                while (i < block.length) {
                  block(i) = everyui.audio.Pcm.toFloat(((bytes(i * 2) & 0xff) | (bytes(i * 2 + 1) << 8)).toShort)
                  i += 1
                }
                Platform.later(() => heard(block))
              }
            }
          }
          catch { case t: Throwable => why = Some(CaptureEnd.Failed(String.valueOf(t.getMessage))) }
          finally {
            try l.close() catch { case _: Throwable => () }
          }
          why.foreach(w => Platform.later(() => ended(w)))
      }
    })
    () => running.set(false)
  }

  /** The default microphone, open at `rate` or the nearest the system takes. */
  private def open(rate: Int): Option[TargetDataLine] = rates(rate).iterator.flatMap { r =>
    try {
      val line = AudioSystem.getLine(new DataLine.Info(classOf[TargetDataLine], format(r))).asInstanceOf[TargetDataLine]
      line.open(format(r), r / 5 * 2)
      Some(line)
    }
    catch {
      case _: LineUnavailableException | _: IllegalArgumentException | _: SecurityException => None
    }
  }.nextOption()

  def output(sampleRate: Int, drained: Long => Unit): PcmOutput = new LineOutput(sampleRate, drained)
}

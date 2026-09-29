package everyui.render

import everyui.audio.PcmOutput

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import javax.sound.sampled.{AudioSystem, DataLine, SourceDataLine}

/** A player's output on the desktop: a Java Sound `SourceDataLine` at the player's rate, fed from a queue by a thread
  * of its own in slices of 20 ms, so a flush takes hold within one. The line's frame position against the frames
  * written says when it has played everything out. The volume scales the samples as they are written, so a change
  * is heard after what the line holds already (a fifth of a second). A system with no line plays nothing, and each
  * chunk is over at once. */
final class LineOutput(sampleRate: Int, drained: Long => Unit) extends PcmOutput {
  private val queue = new LinkedBlockingQueue[(Array[Short], Long, Int)]()
  @volatile private var generation: Int = 0
  @volatile private var gain: Double = 1.0
  @volatile private var open: Boolean = true
  private val line: Option[SourceDataLine] =
    try {
      val format = AudioAccess.format(sampleRate)
        val l = AudioSystem.getLine(new DataLine.Info(classOf[SourceDataLine], format)).asInstanceOf[SourceDataLine]
        l.open(format, sampleRate / 5 * 2)
        l.start()
        Some(l)
      }
      catch {
        case t: Throwable =>
          Platform.logError("audio: no line to play on", t)
          None
      }
  private val writer = new Thread(() => feed(), "everyui-pcm-out")
  writer.setDaemon(true)
  if (line.nonEmpty) writer.start()

  def write(samples: Array[Short], number: Long): Unit =
    if (line.isEmpty) Platform.later(() => drained(number))
    else queue.put((samples, number, generation))

  def flush(): Unit = line.foreach { l =>
    generation += 1
    queue.clear()
    l.flush()
  }

  def volume(level: Double): Unit = gain = math.max(0.0, math.min(1.0, level))

  def close(): Unit = {
    open = false
    generation += 1
    queue.clear()
    line.foreach(l => try l.close() catch { case _: Throwable => () })
  }

  private def feed(): Unit = line.foreach { l =>
    val slice = math.max(1, sampleRate / 50)
    val bytes = new Array[Byte](slice * 2)
    var written = 0L
    var base = l.getLongFramePosition
    var sinceFlush = generation
    var last: Option[Long] = None
    try {
      while (open) {
        val item = queue.poll(10, TimeUnit.MILLISECONDS)
        if (sinceFlush != generation) {
          // flushed: the line's position starts again from where it stands now
          sinceFlush = generation
          base = l.getLongFramePosition
          written = 0L
          last = None
        }
        if (item != null && item._3 == generation) {
          val (samples, number, made) = item
          var at = 0
          while (at < samples.length && made == generation && open) {
            val n = math.min(slice, samples.length - at)
            val g = gain
            var i = 0
            while (i < n) {
              val s = (samples(at + i) * g).toInt
              bytes(i * 2) = (s & 0xff).toByte
              bytes(i * 2 + 1) = ((s >> 8) & 0xff).toByte
              i += 1
            }
            l.write(bytes, 0, n * 2)
            written += n
            at += n
          }
          if (made == generation) last = Some(number)
        }
        else if (item == null && last.nonEmpty && l.getLongFramePosition - base >= written) {
          val number = last.get
          last = None
          Platform.later(() => drained(number))
        }
      }
    }
    catch {
      case t: Throwable => if (open) Platform.logError("audio: the output stopped", t)
    }
  }
}

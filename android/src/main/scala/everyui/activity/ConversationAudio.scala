package everyui.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.os.{Handler, Looper}
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.jdk.CollectionConverters.*

case class CommunicationRoute(id: Int, kind: Int, name: String)
case class ConversationAudioStatus(selected: Option[CommunicationRoute], recording: Option[CommunicationRoute],
                                   playback: Option[CommunicationRoute], queued: Int, echoCancellation: Boolean)

/** Android 12+ service-owned PCM16 audio. Bounded playback, no activity references, explicit focus and routing.
  * Call start only from an eligible foreground microphone service after runtime permissions were granted. */
final class ConversationAudio(context: Context, input: Array[Byte] => Unit, drained: Long => Unit,
                              interrupted: String => Unit, report: ConversationAudioStatus => Unit,
                              focusChanged: Boolean => Unit = _ => ()) extends AutoCloseable {
  private val manager = context.getApplicationContext.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
  private val main = new Handler(Looper.getMainLooper)
  private val live = new AtomicBoolean(false)
  private val paused = new AtomicBoolean(false)
  private val generation = new AtomicLong(0L)
  private case class Packet(bytes: Array[Byte], rate: Int, last: Boolean, generation: Long)
  private val queue = new ArrayBlockingQueue[Packet](32)
  private var previousMode = AudioManager.MODE_NORMAL
  private var previousRoute: AudioDeviceInfo = null
  private var focusGranted = false
  private var modeChanged = false
  private var callbackRegistered = false
  @volatile private var recorder: AudioRecord = null
  @volatile private var track: AudioTrack = null
  private var echo: AcousticEchoCanceler = null
  private var captureThread: Thread = null
  private var playbackThread: Thread = null
  private val attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
  private val focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener {
      def onAudioFocusChange(change: Int): Unit = if (live.get()) {
        if (change == AudioManager.AUDIOFOCUS_GAIN && paused.get()) {
          try {
            Option(recorder).foreach(_.startRecording())
            paused.set(false)
            Option(track).foreach(_.play())
            focusChanged(false)
          } catch { case _: Exception => close(); interrupted("Audio could not resume. Reopen voice when ready.") }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
          if (paused.compareAndSet(false, true)) {
            Option(recorder).foreach(r => scala.util.Try(r.stop()))
            synchronized { queue.clear(); Option(track).foreach(t => { t.pause(); t.flush() }) }
            focusChanged(true)
          }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
          close()
          interrupted("Another app took audio focus. Reopen voice when ready.")
        }
      }
    }, main).build()
  private val routesChanged = new AudioDeviceCallback {
    override def onAudioDevicesRemoved(devices: Array[AudioDeviceInfo]): Unit = if (live.get()) {
      val recording = Option(recorder).flatMap(r => Option(r.getRoutedDevice)).map(_.getId)
      if (devices.exists(d => recording.contains(d.getId))) { close(); interrupted("The microphone route disconnected.") }
      else diagnostics()
    }
    override def onAudioDevicesAdded(devices: Array[AudioDeviceInfo]): Unit = if (live.get()) diagnostics()
  }
  private def route(d: AudioDeviceInfo): CommunicationRoute = CommunicationRoute(d.getId, d.getType, d.getProductName.toString)
  def routes: List[CommunicationRoute] = manager.getAvailableCommunicationDevices.asScala.toList.map(route)
  def diagnostics(): Unit = report(ConversationAudioStatus(Option(manager.getCommunicationDevice).map(route),
    Option(recorder).flatMap(r => Option(r.getRoutedDevice)).map(route),
    Option(track).flatMap(t => Option(t.getRoutedDevice)).map(route), queue.size(), echo != null && echo.getEnabled))
  def start(preferredRoute: Option[Int] = None): Unit = if (live.compareAndSet(false, true)) {
    try {
      require(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, "Microphone permission is required.")
      previousMode = manager.getMode
      previousRoute = manager.getCommunicationDevice
      require(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED, "Audio focus is unavailable.")
      focusGranted = true
      manager.setMode(AudioManager.MODE_IN_COMMUNICATION)
      modeChanged = true
      val available = manager.getAvailableCommunicationDevices.asScala.toList
      val chosen = preferredRoute.flatMap(id => available.find(_.getId == id)).orElse(available.find(d =>
        d.getType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.getType == AudioDeviceInfo.TYPE_BLE_HEADSET))
      chosen.foreach(d => require(manager.setCommunicationDevice(d), "The selected audio route is unavailable."))
      manager.registerAudioDeviceCallback(routesChanged, main)
      callbackRegistered = true
      val format = new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
      recorder = new AudioRecord.Builder().setAudioSource(7 /* MediaRecorder.AudioSource.VOICE_COMMUNICATION; absent from Scala SDK stubs */).setAudioFormat(format)
        .setBufferSizeInBytes(math.max(8192, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
      require(recorder.getState == AudioRecord.STATE_INITIALIZED, "Microphone could not initialize.")
      if (AcousticEchoCanceler.isAvailable) { echo = AcousticEchoCanceler.create(recorder.getAudioSessionId); if (echo != null) echo.setEnabled(true) }
      recorder.startRecording()
      captureThread = new Thread(() => capture(), "everyui-voice-capture")
      playbackThread = new Thread(() => playback(), "everyui-voice-playback")
      captureThread.start(); playbackThread.start()
      diagnostics()
    } catch { case t: Exception => close(); throw t }
  }
  private def capture(): Unit = {
    val buffer = new Array[Byte](4096)
    try while (live.get()) {
      if (paused.get()) Thread.sleep(20)
      else {
        val n = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING)
        if (n > 0 && live.get() && !paused.get()) input(java.util.Arrays.copyOf(buffer, n))
        else if (n < 0 && live.get() && !paused.get()) throw new IllegalStateException("Microphone capture ended.")
      }
    } catch { case _: Exception => if (live.get()) main.post(() => { close(); interrupted("Microphone capture ended.") }) }
  }
  /** False means overflow, invalid format, or a closed session; the caller must stop rather than replay stale speech. */
  def play(bytes: Array[Byte], rate: Int, last: Boolean, gen: Long): Boolean = {
    val valid = live.get() && bytes.length <= 65536 && bytes.length % 2 == 0 && Set(16000, 22050, 24000, 44100, 48000).contains(rate)
    if (!valid || gen < generation.get()) false
    else if (paused.get()) true // Discard interrupted speech; never replay it when focus returns.
    else { if (gen > generation.get()) hush(gen); queue.offer(Packet(bytes.clone(), rate, last, gen)) }
  }
  def hush(gen: Long): Unit = if (gen >= generation.get()) synchronized {
    generation.set(gen)
    queue.clear()
    Option(track).foreach { t => t.pause(); t.flush(); if (!paused.get()) t.play() }
  }
  private def playback(): Unit = {
    var rate = 0
    var frames = 0L
    var playingGeneration = -1L
    try while (live.get()) {
      val packet = queue.poll(100, TimeUnit.MILLISECONDS)
      if (packet != null && !paused.get() && packet.generation == generation.get()) {
        if (track == null || rate != packet.rate) {
          Option(track).foreach(_.release())
          rate = packet.rate
          track = new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(new AudioFormat.Builder()
            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(math.max(8192,
              AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
          track.play(); frames = 0L; playingGeneration = packet.generation
          main.post(() => if (live.get()) diagnostics())
        }
        if (playingGeneration != packet.generation) { frames = 0; playingGeneration = packet.generation }
        var offset = 0
        while (offset < packet.bytes.length && live.get() && !paused.get() && packet.generation == generation.get()) {
          val written = synchronized {
            if (paused.get() || packet.generation != generation.get()) 0
            else track.write(packet.bytes, offset, math.min(4096, packet.bytes.length - offset), AudioTrack.WRITE_BLOCKING)
          }
          if (written < 0) throw new IllegalStateException("Playback stopped.")
          offset += written
          frames += written / 2
        }
        if (packet.last && !paused.get()) {
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
          while (live.get() && !paused.get() && packet.generation == generation.get() &&
            java.lang.Integer.toUnsignedLong(track.getPlaybackHeadPosition) < frames && System.nanoTime() < deadline) Thread.sleep(10)
          if (live.get() && !paused.get() && packet.generation == generation.get()) {
            if (java.lang.Integer.toUnsignedLong(track.getPlaybackHeadPosition) >= frames) drained(packet.generation)
            else main.post(() => { close(); interrupted("Audio playback stalled.") })
          }
        }
      }
    } catch { case _: Exception => if (live.get()) main.post(() => { close(); interrupted("Audio playback ended.") }) }
  }
  def close(): Unit = if (live.compareAndSet(true, false)) {
    queue.clear()
    Option(recorder).foreach(r => scala.util.Try(r.stop()))
    Option(track).foreach(t => scala.util.Try(t.stop()))
    List(captureThread, playbackThread).filter(t => t != null && t != Thread.currentThread()).foreach { t => t.interrupt(); t.join(1000) }
    Option(recorder).foreach(_.release()); recorder = null
    Option(track).foreach(_.release()); track = null
    Option(echo).foreach(_.release()); echo = null
    if (callbackRegistered) { manager.unregisterAudioDeviceCallback(routesChanged); callbackRegistered = false }
    if (modeChanged) {
      manager.clearCommunicationDevice()
      Option(previousRoute).foreach(manager.setCommunicationDevice)
      manager.setMode(previousMode)
      modeChanged = false
    }
    if (focusGranted) { manager.abandonAudioFocusRequest(focus); focusGranted = false }
  }
}

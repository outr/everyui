package everyui.render

import everyui.audio.Downmix
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

/** A `video` or `audio` element's sound folded to stereo through Web Audio, its centre at the level asked for
  * (MediaSupport.centerMix, [[Downmix]]): the element's source, made up to 5.1 the way Web Audio makes up channels
  * (mono to the centre, stereo to the front pair), split, each channel into the left and the right at its share, and
  * through [[Downmix.softClip]] (a WaveShaperNode) to the page's AudioContext.
  *
  * Once an element's sound is taken into Web Audio it never comes out again, and Web Audio hears nothing of media
  * from another origin that has not allowed it (CORS): it plays on, silent. So it is taken only when a centre other
  * than the usual is first asked for, while the context may make sound (a browser keeps a page quiet until it is
  * used), the output plays two channels (a page whose output plays 5.1 is left to play it), and what plays is
  * [[WebMedia]]'s to hear: a stream through hls.js (Media Source Extensions, a `blob:` of the page's own) or a file
  * from the page's origin. Until then the element plays as ever, and back at 0 dB it is the browser's own fold. A
  * file from another origin that comes to an element already taken is asked whether it allows the page (`cors`),
  * and played as such where it does.
  *
  * Five analysers listen to the channels: once a centre has been heard with another channel the source is known to
  * be a surround one, and [[mixing]]; a centre heard alone for [[CenterFold.MonoSeconds]] is mono made up, and goes
  * to both sides whole, as the browser would play it. A second after each change of level it says how loud the
  * centre went in and each side came out (`media: centre mix …`). */
final class CenterFold(el: html.Element, changed: () => Unit) {
  private var db = 0.0
  private var graph: Option[js.Dynamic] = None
  private var waking = false
  // per source: a centre heard with another channel, or alone, and for how long
  private var surround = false
  private var mono = false
  private var alone = 0.0
  private var tellAt = 0.0
  private var meters: List[js.Dynamic] = Nil
  private var gains: Map[String, js.Dynamic] = Map.empty

  /** Whether it is taken into Web Audio. */
  def taken: Boolean = graph.nonEmpty

  /** Whether the centre is being folded at the level asked for: a surround source heard through the graph. */
  def mixing: Boolean = graph.nonEmpty && surround && !mono

  /** The centre at `to` dB over the usual, for a source `hearable` (WebMedia's to hear). */
  def level(to: Double, hearable: Boolean): Unit = {
    db = to
    if (graph.isEmpty) {
      if (to != 0.0 && hearable) take()
    }
    else {
      shares()
      tellAt = now() + 1000.0
    }
  }

  /** A new source: what it is is heard afresh. */
  def fresh(): Unit = {
    val was = mixing
    surround = false
    mono = false
    alone = 0.0
    shares()
    if (was) changed()
  }

  private def take(): Unit = if (CenterFold.available && !waking) {
    val ctx = AudioAccess.context()
    if (ctx.destination.maxChannelCount.asInstanceOf[Int] > 2)
      Platform.log("media: centre mix: the output plays " + ctx.destination.maxChannelCount.toString +
        " channels, and the browser sends it every one: not folded")
    else if (ctx.state.asInstanceOf[String] == "running") build(ctx)
    else {
      // taken only once the page may make sound: a suspended context would silence the element
      waking = true
      ctx.resume().asInstanceOf[js.Promise[Any]].`then`[Unit]((_: Any) => {
        waking = false
        if (ctx.state.asInstanceOf[String] == "running" && graph.isEmpty && db != 0.0) build(ctx)
      }, js.defined((_: Any) => waking = false))
    }
  }

  private def build(ctx: js.Dynamic): Unit =
    try {
      val source = ctx.createMediaElementSource(el)
      val up = ctx.createGain()
      up.channelCount = 6
      up.channelCountMode = "explicit"
      up.channelInterpretation = "speakers"
      source.connect(up)
      val split = ctx.createChannelSplitter(6)
      up.connect(split)
      val merge = ctx.createChannelMerger(2)
      def share(name: String, from: Int, to: Int, gain: Double): Unit = {
        val g = ctx.createGain()
        g.gain.value = gain
        split.connect(g, from)
        g.connect(merge, 0, to)
        gains += name -> g
      }
      share("L", 0, 0, 1.0)
      share("R", 1, 1, 1.0)
      share("cL", 2, 0, Downmix.centerGain(db))
      share("cR", 2, 1, Downmix.centerGain(db))
      share("sL", 4, 0, Downmix.Surround)
      share("sR", 5, 1, Downmix.Surround)
      // the limiter: the sum brought into the curve's range, and bent under full scale
      val into = ctx.createGain()
      into.gain.value = 1.0 / CenterFold.Range
      val bend = ctx.createWaveShaper()
      val points = Downmix.softCurve(8193, CenterFold.Range)
      val curve = new Float32Array(points.length)
      var i = 0
      while (i < points.length) {
        curve(i) = points(i)
        i += 1
      }
      bend.curve = curve
      bend.oversample = "none"
      merge.connect(into)
      into.connect(bend)
      bend.connect(ctx.destination)
      // what is heard: each channel in, and each side out
      val out = ctx.createChannelSplitter(2)
      bend.connect(out)
      meters = List(0, 1, 2, 4, 5).map(c => meter(ctx, split, c)) ++ List(0, 1).map(c => meter(ctx, out, c))
      graph = Some(source)
      tellAt = now() + 1000.0
      Platform.log("media: centre mix: the sound taken into Web Audio, folded to stereo with the centre at " +
        Downmix.signed(db) + " dB")
      listen()
    } catch {
      case t: Throwable =>
        Platform.log("media: centre mix: Web Audio would not take the sound (" + t.toString + "): played as it is")
    }

  private def meter(ctx: js.Dynamic, from: js.Dynamic, channel: Int): js.Dynamic = {
    val a = ctx.createAnalyser()
    a.fftSize = 2048
    from.connect(a, channel)
    a
  }

  // each channel's share: the centre at the level asked for, or whole for mono made up
  private def shares(): Unit = if (gains.nonEmpty) {
    val ctx = AudioAccess.context()
    val c = if (mono) 1.0 else Downmix.centerGain(db)
    List("cL", "cR").foreach(n => gains(n).gain.setTargetAtTime(c, ctx.currentTime, Downmix.GlideSeconds))
  }

  // four times a second while it is taken: what each channel carries, and a second after a change what came out
  private def listen(): Unit = js.timers.setTimeout(250.0) {
    if (graph.nonEmpty) {
      if (!el.asInstanceOf[js.Dynamic].paused.asInstanceOf[Boolean]) heard()
      listen()
    }
  }

  private def heard(): Unit = {
    val levels = meters.map(rms)
    val (front, centre, sides) = (math.max(levels(0), levels(1)), levels(2), math.max(levels(3), levels(4)))
    val audible = 1e-4
    val was = mixing
    if (!surround && centre > audible && (front > audible || sides > audible)) {
      surround = true
      mono = false
      shares()
    }
    else if (!surround && !mono && centre > audible) {
      alone += 0.25
      if (alone >= CenterFold.MonoSeconds) {
        mono = true
        shares()
        Platform.log("media: centre mix: a centre alone, mono made up: played whole on both sides")
        tellAt = now() + 1000.0
      }
    }
    if (mixing != was) changed()
    if (tellAt > 0.0 && now() >= tellAt) {
      tellAt = 0.0
      Platform.log("media: centre mix at " + Downmix.signed(db) + " dB" + (if (mono) " (mono, left whole)" else "") +
        ": centre in " + Downmix.decibels(centre) + " dBFS, out left " + Downmix.decibels(levels(5)) + " right " +
        Downmix.decibels(levels(6)) + " dBFS")
    }
  }

  private def rms(a: js.Dynamic): Double = {
    val data = new Float32Array(a.fftSize.asInstanceOf[Int])
    a.getFloatTimeDomainData(data)
    var sum = 0.0
    var i = 0
    while (i < data.length) {
      sum += data(i) * data(i)
      i += 1
    }
    math.sqrt(sum / data.length)
  }

  private def now(): Double = dom.window.performance.now()
}

object CenterFold {
  /** How far past full scale the curve bends the sum within: past it, the curve's end, just under full scale. */
  val Range: Double = 4.0
  /** How long a centre is heard alone before the source is taken to be mono made up. */
  val MonoSeconds: Double = 2.0

  /** Whether the browser has Web Audio and its element source. */
  def available: Boolean = {
    val w = dom.window.asInstanceOf[js.Dynamic]
    !js.isUndefined(w.AudioContext) || !js.isUndefined(w.webkitAudioContext)
  }
}

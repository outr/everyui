package everyui.render

import everyui.audio.{CaptureEnd, PcmOutput}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

/** Voice audio (`everyui.audio`) through Web Audio: one AudioContext for the page, the microphone from `getUserMedia`
  * read by an AudioWorklet, and players as buffers scheduled on the context's clock ([[WebAudioOutput]]).
  *
  * The worklet's code is a string here, loaded from a Blob URL, so it comes with the published `everyui-web` and needs
  * no file of its own. It hands the microphone's samples (the channels mixed down) to the page in blocks of 1024 at
  * the context's rate, and the shared layer resamples them. A browser without AudioWorklet falls back to a
  * ScriptProcessor.
  *
  * A browser keeps a page silent until the person has touched it: the context is resumed on the first pointer or key
  * after it is made, and again on each start of a capture or a chunk played, which a tap lets through. */
object AudioAccess {
  private val processor: String =
    """class EveryUICapture extends AudioWorkletProcessor {
      |  constructor() { super(); this.block = new Float32Array(1024); this.n = 0; }
      |  process(inputs) {
      |    const input = inputs[0];
      |    if (input && input.length > 0) {
      |      const channels = input.length, frames = input[0].length;
      |      for (let i = 0; i < frames; i++) {
      |        let v = 0;
      |        for (let c = 0; c < channels; c++) v += input[c][i];
      |        this.block[this.n++] = v / channels;
      |        if (this.n === this.block.length) {
      |          this.port.postMessage(this.block, [this.block.buffer]);
      |          this.block = new Float32Array(1024);
      |          this.n = 0;
      |        }
      |      }
      |    }
      |    return true;
      |  }
      |}
      |registerProcessor('everyui-capture', EveryUICapture);
      |""".stripMargin

  private var shared: Option[js.Dynamic] = None
  private var worklet: Option[js.Promise[Any]] = None

  private def audioContextClass: js.Dynamic = {
    val w = dom.window.asInstanceOf[js.Dynamic]
    if (!js.isUndefined(w.AudioContext)) w.AudioContext else w.webkitAudioContext
  }

  /** The page's AudioContext, made on first use. */
  def context(): js.Dynamic = shared.getOrElse {
    val ctx = js.Dynamic.newInstance(audioContextClass)(js.Dynamic.literal(latencyHint = "interactive"))
    shared = Some(ctx)
    val wake: js.Function1[dom.Event, Unit] = (_: dom.Event) => resume()
    List("pointerdown", "keydown", "touchend").foreach(e => dom.document.addEventListener(e, wake, true))
    ctx
  }

  /** The context let run, where the page may make sound yet. */
  def resume(): Unit = shared.foreach { ctx =>
    if (ctx.state.asInstanceOf[String] == "suspended")
      ctx.resume().asInstanceOf[js.Promise[Any]].`catch`[Any]((_: Any) => ())
  }

  def capturable(): Boolean = {
    val nav = dom.window.navigator.asInstanceOf[js.Dynamic]
    !js.isUndefined(audioContextClass) && !js.isUndefined(nav.mediaDevices) &&
      !js.isUndefined(nav.mediaDevices.getUserMedia)
  }

  def playable(): Boolean = !js.isUndefined(audioContextClass)

  def capture(rate: Int, echoCancellation: Boolean, opened: Int => Unit, heard: Array[Float] => Unit,
              ended: CaptureEnd => Unit): () => Unit = {
    var stopped = false
    var release: () => Unit = () => ()
    if (!capturable()) ended(CaptureEnd.Unavailable)
    else {
      val ctx = context()
      resume()
      val constraints = js.Dynamic.literal(audio = js.Dynamic.literal(
        echoCancellation = echoCancellation, noiseSuppression = echoCancellation, autoGainControl = echoCancellation,
        channelCount = 1))
      val asked = dom.window.navigator.asInstanceOf[js.Dynamic].mediaDevices.getUserMedia(constraints)
        .asInstanceOf[js.Promise[js.Dynamic]]
      asked.`then`[Unit]((stream: js.Dynamic) => {
        val tracks = stream.getTracks().asInstanceOf[js.Array[js.Dynamic]]
        def stopTracks(): Unit = tracks.foreach(t => t.stop())
        if (stopped) stopTracks()
        else {
          tracks.foreach(t => t.onended = (() => if (!stopped) {
            stopped = true
            release()
            ended(CaptureEnd.Lost)
          }): js.Function0[Unit])
          val source = ctx.createMediaStreamSource(stream)
          // a node that is never heard: what keeps the graph pulling the microphone through in every browser
          val silent = ctx.createGain()
          silent.gain.value = 0
          silent.connect(ctx.destination)
          val block: js.Function1[Float32Array, Unit] = (data: Float32Array) =>
            if (!stopped) heard(Array.tabulate(data.length)(i => data(i)))
          def connect(node: js.Dynamic): Unit = {
            source.connect(node)
            node.connect(silent)
            release = () => {
              stopTracks()
              try source.disconnect() catch { case _: Throwable => () }
              try node.disconnect() catch { case _: Throwable => () }
              try silent.disconnect() catch { case _: Throwable => () }
            }
            opened(ctx.sampleRate.asInstanceOf[Double].toInt)
          }
          if (js.isUndefined(ctx.audioWorklet)) {
            val node = ctx.createScriptProcessor(2048, 1, 1)
            node.onaudioprocess = ((e: js.Dynamic) =>
              block(e.inputBuffer.getChannelData(0).asInstanceOf[Float32Array])): js.Function1[js.Dynamic, Unit]
            connect(node)
          }
          else loadWorklet(ctx).`then`[Unit]((_: Any) => {
            if (stopped) stopTracks()
            else {
              val node = js.Dynamic.newInstance(dom.window.asInstanceOf[js.Dynamic].AudioWorkletNode)(ctx, "everyui-capture",
                js.Dynamic.literal(numberOfInputs = 1, numberOfOutputs = 1, outputChannelCount = js.Array(1)))
              node.port.onmessage = ((e: js.Dynamic) => block(e.data.asInstanceOf[Float32Array])): js.Function1[js.Dynamic, Unit]
              connect(node)
            }
          }, js.defined((e: Any) => {
            stopTracks()
            if (!stopped) {
              stopped = true
              ended(CaptureEnd.Failed("the capture worklet did not load: " + e))
            }
          }))
        }
      }, js.defined((e: Any) => if (!stopped) {
        stopped = true
        val name = String.valueOf(e.asInstanceOf[js.Dynamic].name)
        ended(
          if (name == "NotAllowedError" || name == "SecurityError") CaptureEnd.Denied
          else if (name == "NotFoundError" || name == "OverconstrainedError" || name == "NotReadableError") CaptureEnd.Unavailable
          else CaptureEnd.Failed(String.valueOf(e)))
      }))
    }
    () => if (!stopped) {
      stopped = true
      release()
    }
  }

  /** The capture worklet, added to the context once. */
  private def loadWorklet(ctx: js.Dynamic): js.Promise[Any] = worklet.getOrElse {
    val blob = new dom.Blob(js.Array(processor), new dom.BlobPropertyBag { `type` = "application/javascript" })
    val url = dom.URL.createObjectURL(blob)
    val loading = ctx.audioWorklet.addModule(url).asInstanceOf[js.Promise[Any]]
    worklet = Some(loading)
    loading
  }

  def output(sampleRate: Int, drained: Long => Unit): PcmOutput = new WebAudioOutput(sampleRate, drained)
}

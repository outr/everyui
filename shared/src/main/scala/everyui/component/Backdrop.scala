package everyui.component

import everyui.easing.Easing
import everyui.image.Image
import everyui.render.Platform
import everyui.task.*
import reactify.*

import scala.concurrent.duration.*

/** An image covering the whole of its box at its own proportions, centred, with what overflows cut off: what sits
  * behind a screen. It follows `source` (a site-absolute path or a URL; empty shows nothing), keeping the image it has
  * until the next one has loaded, so changing it never flashes an empty box.
  *
  * Where the picture sits as it is cropped is `alignX` and `alignY`, each from -1 (its left or top edge on the box's)
  * to 1 (its right or bottom), 0 in the middle. A `fallbackSource` stands in for a source that will not load — an
  * image addressed indirectly, which the server may not have — rather than the box going on showing a picture that
  * belongs to something else. With `crossfade`, a new source cross-fades in: what is showing fades out at once, and
  * the new picture fades up only once it has loaded, so a slow one holds on the ground beneath rather than flashing
  * blank; one that never loads brings the old picture back.
  *
  * A `videoSource` (a file, a site-absolute path or a URL; empty for none) plays behind the picture where the platform
  * plays one behind a screen (`Platform.backdropVideo`: a browser), muted, inline and looping on its own, never
  * loaded again at the loop. The picture shows at once as ever, and the video takes its place only once it is playing
  * and has enough in hand to play to its end without stopping: in one step, or with `crossfade` the picture fading
  * off it, so there is never a flash or an empty box. It is cropped as the picture is (`alignX`, `alignY`), only one
  * of the two is seen once it is up, and a pointer goes through it. It pauses while the app is away from the front
  * and plays on as it comes back. The picture stays, or comes back, where the video fails to load or play
  * (`videoFailed`), where people ask for less motion (`everyui.motion.ReduceMotion`), and once `videoSource` is
  * empty again; an app that lets people turn background videos off empties it. Elsewhere it keeps the picture. */
class Backdrop extends Container {
  override def kind: String = "Backdrop"

  val source: Var[String] = own(Var(""))
  /** What the picture shows, in words (`ImageView.alt`). */
  def alt: Var[String] = view.alt
  val alignX: Var[Double] = own(Var(0.0))
  val alignY: Var[Double] = own(Var(0.0))
  /** Shown in place of `source` when that cannot be loaded; empty for none. */
  val fallbackSource: Var[String] = own(Var(""))
  /** Whether a change of picture cross-fades rather than cutting. */
  val crossfade: Var[Boolean] = own(Var(false))
  /** Whether the picture asked for could not be had (nor its fallback): something else can stand in for it then, as a
    * film mark does for a poster that will not load. False again as soon as another is asked for. */
  val failed: Var[Boolean] = own(Var(false))
  /** The video to play in the picture's place, once it can play through; empty, the default, for none. */
  val videoSource: Var[String] = own(Var(""))
  /** Whether the video showing in place of the picture is up: false while the picture shows. */
  val videoShown: Var[Boolean] = own(Var(false))
  /** Whether the video asked for could not be played: the picture stays. False again once another is asked for. */
  val videoFailed: Var[Boolean] = own(Var(false))

  private val view = new ImageView
  private var shown: Image = Image.empty
  /** The fade under way, so the next one calls it off rather than fighting it. */
  private var fading: Option[TaskInstance] = None
  // the video, made the first time one is asked for, and the fade or the wait for it to play through under way
  private var video: Option[VideoView] = None
  private var videoFading: Option[TaskInstance] = None
  // the video the wait is for, so a wait for one since changed does nothing
  private var waitingFor = ""
  private var stopListening: List[() => Unit] = Nil

  children += view
  source.attach { requested =>
    failed @= false
    if (crossfade() && requested.nonEmpty) fadeTo(0.0, Backdrop.fadeOut)
    load(requested, requested, 0)
  }
  videoSource.attach(_ => playVideo())
  if (Backdrop.plays()) everyui.motion.ReduceMotion.on.attach(_ => playVideo())

  /** No picture asked for any more, so a load or a retry still on its way shows nothing, and then taken apart. */
  override def dispose(): Unit = {
    source @= ""
    videoSource @= ""
    stopListening.foreach(stop => stop())
    stopListening = Nil
    super.dispose()
  }

  size.width.attach(_ => place())
  size.height.attach(_ => place())
  alignX.attach(_ => place())
  alignY.attach(_ => place())

  /** Load `target` for the source `requested` and, once it has arrived, show it. An image that comes back with no size
    * did not load: the `fallbackSource` is tried then, where there is one; otherwise it is asked for again, a beat later
    * and a few times over, since on a Roku the loader saturates when a screen builds many pictures at once (a cast
    * montage) and some time out. Re-requesting also sets the view loading afresh, so a display that dropped its own
    * load is started again; and a spurious empty never overwrites a picture already shown. */
  private def load(requested: String, target: String, attempt: Int): Unit =
    if (target.isEmpty) show(Image.empty)
    else Image(target).map { loaded =>
      if (source() == requested) {
        if (loaded.width > 0.0 && loaded.height > 0.0) show(loaded)
        else if (target == requested && fallbackSource().nonEmpty && fallbackSource() != requested)
          load(requested, fallbackSource(), 0)
        else if (attempt < Backdrop.retries)
          Platform.after(Backdrop.retryDelay * (attempt + 1),
            () => if (source() == requested) load(requested, target, attempt + 1))
        // given up on: the picture that was showing comes back up rather than leaving the box faded out
        else {
          failed @= true
          if (crossfade()) fadeTo(1.0, Backdrop.fadeIn)
        }
      }
    }.startUnit()

  private def show(image: Image): Unit = {
    shown = image
    view.image @= image
    place()
    // (under the video, where it is up, the picture waits out of sight)
    if (crossfade() && !videoShown()) fadeTo(1.0, Backdrop.fadeIn)
  }

  /** The picture's opacity to `target` over `time`, in place of whatever fade was under way. */
  private def fadeTo(target: Double, time: FiniteDuration): Unit = {
    fading.foreach(_.cancel())
    fading = Some(view.opacity.to(target).in(time).easing(Easing.cubicInOut).start())
  }

  /** The image scaled until it covers the box on both sides, placed by `alignX` and `alignY`. */
  private def place(): Unit = {
    node.setClip(size.width(), size.height())
    cover(view, shown.width, shown.height)
    video.foreach(v => v.videoSize match {
      case Some((w, h)) => cover(v, w, h)
      // until its size is known, the box (it is out of sight until then)
      case None =>
        v.position.left @= 0.0
        v.position.top @= 0.0
        v.size.width @= size.width()
        v.size.height @= size.height()
    })
  }

  // `c`, `width` by `height` as it is drawn, scaled until it covers the box on both sides, placed by `alignX` and `alignY`
  private def cover(c: Component, width: Double, height: Double): Unit = {
    val scale = if (width > 0.0 && height > 0.0) math.max(size.width() / width, size.height() / height) else 0.0
    c.size.width @= width * scale
    c.size.height @= height * scale
    c.position.left @= (size.width() - c.size.width()) * (1.0 + alignX()) / 2.0
    c.position.top @= (size.height() - c.size.height()) * (1.0 + alignY()) / 2.0
  }

  /** The video asked for now playing, out of sight until it can play through, or none, the picture back. */
  private def playVideo(): Unit = {
    val wanted = if (Backdrop.plays() && !everyui.motion.ReduceMotion()) videoSource() else ""
    videoFailed @= false
    if (wanted.isEmpty) {
      waitingFor = ""
      backToPicture()
      video.foreach(_.source @= "")
    }
    // the one playing, or on its way: left as it is
    else if (video.exists(_.source() == wanted) && (videoShown() || waitingFor == wanted)) ()
    else {
      backToPicture()
      val v = video.getOrElse(makeVideo())
      waitingFor = wanted
      if (v.source() != wanted) v.source @= wanted
      watchThrough(wanted, 0)
    }
  }

  private def makeVideo(): VideoView = {
    val v = Backdrop.makesVideo()
    // muted and looping before it has a source, so the browser lets it start by itself and loops it without a reload
    v.muted @= true
    v.loop @= true
    v.cover @= true
    v.opacity @= 0.0
    everyui.render.Gestures.passThrough(v.node)
    // failing while it is waited for or once it is up: the picture back
    v.events(e => if (e == "error") {
      if (waitingFor.isEmpty && videoShown()) {
        videoFailed @= true
        backToPicture()
        v.source @= ""
      }
      else heard(v.source(), failedNow = true)
    })
    // behind the picture
    children @= v +: children()
    video = Some(v)
    stopListening = List(Platform.onAway(() => v.playing @= false), Platform.onResume(() => v.playing @= true))
    v
  }

  // the video `wanted` asked, a beat at a time, whether it is playing with enough in hand to play through
  private def watchThrough(wanted: String, tries: Int): Unit = Platform.after(Backdrop.throughCheck, () =>
    if (waitingFor == wanted) video.foreach { v =>
      if (v.source() == wanted && v.videoSize.nonEmpty && v.node.mediaPlaysThrough() && v.playhead() > 0.0)
        heard(wanted, failedNow = false)
      else if (tries < Backdrop.throughTries) watchThrough(wanted, tries + 1)
      // never ready: the picture stays
      else heard(wanted, failedNow = true)
    })

  /** What became of the video `wanted`: it failed, and the picture stays, or it is ready, and takes the picture's place. */
  private[everyui] def heard(wanted: String, failedNow: Boolean): Unit = if (waitingFor == wanted && wanted.nonEmpty) {
    waitingFor = ""
    if (failedNow) {
      videoFailed @= true
      backToPicture()
      video.foreach(_.source @= "")
    }
    else video.foreach { v =>
      place()
      videoShown @= true
      fading.foreach(_.cancel())
      fading = None
      videoFading.foreach(_.cancel())
      videoFading = None
      if (crossfade()) {
        // the picture faded off the video; where the picture was still coming up, the video fades up under it too, so
        // it never comes out of the ground at once
        val under = if (view.opacity() >= 0.99) {
          v.opacity @= 1.0
          None
        } else Some(v.opacity.to(1.0).in(Backdrop.fadeIn).easing(Easing.cubicInOut))
        val off = view.opacity.to(0.0).in(Backdrop.fadeIn).easing(Easing.cubicInOut)
        val run = sequential(under.map(u => parallel(u, off)).getOrElse(parallel(off)), synchronous {
          v.opacity @= 1.0
          view.visible @= false
        }).start()
        videoFading = Some(run)
        // (where the app runs no frame loop nothing would step it: done at once)
        if (!everyui.task.Frames.running) {
          run.cancel()
          videoFading = None
          v.opacity @= 1.0
          view.visible @= false
        }
      }
      else {
        v.opacity @= 1.0
        view.visible @= false
      }
    }
  }

  // the picture up again, in front, and the video out of sight behind it
  private def backToPicture(): Unit = {
    videoFading.foreach(_.cancel())
    videoFading = None
    view.visible @= true
    if (videoShown()) {
      fading.foreach(_.cancel())
      fading = None
      view.opacity @= 1.0
    }
    videoShown @= false
    video.foreach(_.opacity @= 0.0)
  }
}

object Backdrop {
  /** How many times a picture that came back empty is asked for again before it is let go. */
  private val retries: Int = 3
  /** The wait before the next try, times the try's number, so it lengthens while the loader stays busy. */
  private val retryDelay: Double = 0.6
  /** How long what is showing takes to fade out once another picture is asked for, and the new one to fade up. */
  private val fadeOut: FiniteDuration = 200.millis
  private val fadeIn: FiniteDuration = 380.millis
  /** Whether a video plays behind a screen here: the platform's say, or a spec's. */
  private[everyui] var plays: () => Boolean = () => Platform.backdropVideo()
  /** What its video is made as: a `VideoView`, or a spec's stand-in. */
  private[everyui] var makesVideo: () => VideoView = () => new VideoView
  /** How often a video starting is asked whether it can play through, and how many times before it is given up. */
  private val throughCheck: Double = 0.25
  private val throughTries: Int = 240
}

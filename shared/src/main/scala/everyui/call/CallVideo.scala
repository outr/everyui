package everyui.call

import reactify.*

/** A camera's or a screen's picture in a call (`track`, read reactively), drawn by the call's engine to fill the box
  * (`cover`, cut at the edges) or whole inside it, turned as in a mirror where `mirror` is on (this person's own
  * camera). Nothing is drawn while `track` is None, or the call is not in the room. A [[VideoTile]] holds one. */
class CallVideo(val call: Call, track: => Option[CallTrack], filling: Boolean = true, mirrored: => Boolean = false)
  extends everyui.component.Container {
  /** Filling the box and cut at its edges (a face), or whole inside it (a screen). */
  val cover: Var[Boolean] = own(Var(filling))
  /** Turned as in a mirror: this person's own camera, as they expect to see themselves. */
  val mirror: Var[Boolean] = own(Var(mirrored))
  private val shown: Val[Option[CallTrack]] = own(Val(track))
  private var drawn: Option[CallTrack] = None

  /** The track it draws now. */
  def drawing: Option[CallTrack] = drawn

  private def draw(next: Option[CallTrack]): Unit = if (next != drawn) {
    drawn.foreach(t => call.hide(t, this))
    drawn = next
    drawn.foreach(t => call.show(t, this))
  }

  // drawn afresh as it is told to fill or to mirror otherwise
  private def redraw(): Unit = drawn.foreach { t =>
    call.hide(t, this)
    call.show(t, this)
  }

  shown.attach(draw)
  cover.attach(_ => redraw())
  mirror.attach(_ => redraw())
  // the room reached after the track was known (a tile made while connecting): drawn now
  private val reached = call.state.attach(s => if (s == CallState.Connected) redraw())
  onDispose(() => {
    call.state.reactions -= reached
    draw(None)
  })
  draw(shown())

  override def kind: String = "CallVideo(" + drawn.map(_.id).getOrElse("") + ")"
}

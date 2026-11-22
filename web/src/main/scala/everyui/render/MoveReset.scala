package everyui.render

/** Whether a media element moved on the page (put in another box, or a box holding it moved) came out of the move
  * without what it was playing: LG's Chromium 38 (webOS 3.5) lets the source's pipeline go as the element is taken off
  * the page and put back, and leaves it with nothing loaded, no source and its playhead at 0, its address still set,
  * where every other engine plays on. Told by the element's state after the move, not by the engine's name, and
  * [[WebMedia]] opens the source again where it was. Plain Scala, so the desktop's specs read it as the page does. */
object MoveReset {
  /** `networkState` of an element whose source has gone (NETWORK_NO_SOURCE). */
  val noSource: Int = 3

  /** How long after a move the element is looked at again, in ms: an engine may let it go a task or more later. */
  val checks: List[Int] = List(100, 500, 1500)

  /** Whether the source was let go: it had loaded (`loaded`, its metadata in), it was `was` seconds in before the move,
    * and it now has nothing loaded (`readyState` 0), no source, or stands more than [[ResumeSeek.near]] back from
    * where it was, not by a seek of its own (`seeking`) nor by a loop coming round (`looping`). */
  def dropped(loaded: Boolean, was: Double, readyState: Int, networkState: Int, now: Double, seeking: Boolean,
              looping: Boolean): Boolean =
    loaded && (readyState == 0 || networkState == noSource || (!seeking && !looping && was - now > ResumeSeek.near))
}

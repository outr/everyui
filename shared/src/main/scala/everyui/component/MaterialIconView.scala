package everyui.component

import everyui.font.MaterialIcon
import everyui.motion.{Keyframe, ReduceMotion, Track}
import everyui.render.Nodes
import reactify.{Val, Var}

/** youi's `MaterialIconView`: one Material Icons glyph, `points` pixels square; `icon` changes it, `color` tints it.
  * While `busy`, the glyph itself breathes, fading slowly down and back, run by the platform as any `motion` is, with
  * nothing drawn over it; where less motion is asked for (`ReduceMotion`) it stands still at half strength instead. */
class MaterialIconView(initial: MaterialIcon = MaterialIcon.Empty, points: Double = 24.0) extends TextView(Nodes.icon(initial.glyph, points)) {
  val icon: Var[MaterialIcon] = own(Var(initial))
  /** At work: the glyph breathes until it is set back. */
  val busy: Var[Boolean] = own(Var(false))
  // the glyph's own motion, and how strong it is drawn, while it is not busy
  private var rest: Option[(List[Track], Double)] = None
  // the text view's font starts as the default family at 16px: the glyph needs its own face, at its own size
  font.family @= "Material Icons"
  font.size @= points
  icon.attach(i => content @= i.glyph)
  private def breathe(): Unit = {
    if (busy()) {
      if (rest.isEmpty) rest = Some((motion(), opacity()))
      if (ReduceMotion()) {
        motion @= Nil
        opacity @= stillStrength
      }
      else {
        opacity @= rest.get._2
        motion @= List(MaterialIconView.breath)
      }
    }
    else rest.foreach { case (tracks, strength) =>
      rest = None
      motion @= tracks
      opacity @= strength
    }
  }
  // whether it breathes: busy, where it may move; made only once it is first busy
  private lazy val moving: Val[Boolean] = {
    val v = own(Val(busy() && !ReduceMotion()))
    v.attach(_ => breathe())
    v
  }
  busy.attach { b =>
    if (b) moving
    breathe()
  }
  /** How strong a busy glyph is drawn where it may not move: half, unless something else marks it (a button tints it
    * in the accent). */
  protected def stillStrength: Double = MaterialIconView.still
  // a glyph is drawn whole: one cut short is nothing
  override private[everyui] def keepsToBox: Boolean = false

  override def kind: String = "MaterialIconView(" + icon().name + ")"
}

object MaterialIconView {
  /** A busy glyph's breath: from whole down to a third and back over two seconds. */
  val breath: Track = Track(Track.Opacity, 2.0, List(Keyframe(0.0, 1.0), Keyframe(0.5, 0.35), Keyframe(1.0, 1.0)))
  /** How strong a busy glyph is drawn where it may not move. */
  val still: Double = 0.5
}

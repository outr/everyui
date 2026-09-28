package everyui

/** A hard edge drawn round every letter, `width` wide on each side, in `color`: crisper than a shadow over a bright
  * picture, which a blur only softens. */
case class TextOutline(width: Double, color: Color)

object TextOutline {
  /** The edge for words free over a picture (a clock, a caption over a photo), so they never disappear into a bright
    * one: half a point of translucent black, enough to hold white letters against snow without drawing a second,
    * heavier line round every one. */
  val overPicture: TextOutline = TextOutline(0.5, Color.Black.withAlpha(0.6))

  /** Lighter still, for coloured words over a picture, whose colour holds against a bright one by itself. */
  val overPictureLight: TextOutline = TextOutline(0.3, Color.Black.withAlpha(0.35))
}

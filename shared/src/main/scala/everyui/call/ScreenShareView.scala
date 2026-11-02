package everyui.call

/** A screen someone shares in a call, whole inside a box the app sizes and named as theirs: `sharer`'s (an identity),
  * or with None whoever shares one first, so a call's stage follows whoever shares. It is empty, and says nothing,
  * while nobody shares (`sharing` false), for the app to hide or give the room to the tiles.
  *
  * {{{
  *   val stage = new ScreenShareView(call)
  *   stage.visible := stage.sharing()
  * }}} */
class ScreenShareView(call: Call, sharer: Option[String] = None)
  extends VideoTile(call, sharer.getOrElse(ScreenShareView.first(call)), TrackSource.Screen) {
  /** Whether a screen is shared and shown. */
  val sharing: reactify.Val[Boolean] = own(reactify.Val(track().nonEmpty))

  override def kind: String = "ScreenShareView(" + person().map(_.identity).getOrElse("") + ")"
}

object ScreenShareView {
  /** Whoever shares a screen first in `call`, this person after the others; "" while nobody does. */
  def first(call: Call): String =
    (call.participants() ++ call.local().toVector).find(_.screen.nonEmpty).map(_.identity).getOrElse("")
}

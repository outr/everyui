package everyui.component

import everyui.Gradient

/** How a screen wants the artwork behind it treated (`UIScreen.treatment`): where the picture sits in the backdrop
  * when it is cropped (`alignX` and `alignY`, each -1 to 1 from one edge to the other, 0 the middle), and the washes
  * laid over it, bottom first. A screen moving to another's treatment blends into it rather than cutting
  * (`ScreenManager.washes`), so the wash travels with the picture instead of snapping between two frames. */
case class Treatment(alignX: Double = 0.0, alignY: Double = 0.0, washes: List[Gradient] = Nil)

object Treatment {
  /** The artwork as it is, centred, with nothing over it. */
  val none: Treatment = Treatment()

  /** Part of the way from `a` to `b`: the alignment straight between them, and each wash by its place in the list
    * (`Gradient.lerp`), one that only one side has fading in or out. */
  def lerp(a: Treatment, b: Treatment, t: Double): Treatment = {
    val count = math.max(a.washes.size, b.washes.size)
    Treatment(a.alignX + (b.alignX - a.alignX) * t, a.alignY + (b.alignY - a.alignY) * t,
      (0 until count).toList.flatMap(i => Gradient.lerp(a.washes.drop(i).headOption, b.washes.drop(i).headOption, t)))
  }
}

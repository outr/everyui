package everyui.layout

/** How far in from each edge, in the app's units: the screen's safe area (`Platform.safeArea`), the part no cutout,
  * rounded corner, system bar or gesture area covers. */
case class Insets(top: Double = 0.0, right: Double = 0.0, bottom: Double = 0.0, left: Double = 0.0)

object Insets {
  val none: Insets = Insets()
}

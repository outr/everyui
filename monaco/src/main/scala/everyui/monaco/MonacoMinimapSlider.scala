package everyui.monaco

/** When the minimap's slider shows (`minimap.showSlider`). */
enum MonacoMinimapSlider(val wire: String) extends MonacoEnum {
  case Always extends MonacoMinimapSlider("always")
  case Mouseover extends MonacoMinimapSlider("mouseover")
}

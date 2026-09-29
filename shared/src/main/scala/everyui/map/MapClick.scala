package everyui.map

import everyui.event.Modifiers

/** A click on a map (`MapView.clicked`), or OK pressed on it, which clicks the middle of the view: the place it was
  * `at`, what is there if anything is, and the modifier keys held. A drag or a pinch is no click. */
case class MapClick(at: LatLng, feature: Option[MapFeature], modifiers: Modifiers)

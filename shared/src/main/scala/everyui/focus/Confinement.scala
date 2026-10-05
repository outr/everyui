package everyui.focus

import everyui.component.Component

/** One region the focus is held in (`Focus.confine`): the part of the screen it is, whether OK and a click press
  * what is in it, whether OK held and a long press hold it, whether the arrows move the focus in it, and the parts of
  * the screen that were up when it began, which alone it holds back from the pointer (a note put up beside it
  * afterwards is clicked as ever). */
private[focus] class Confinement(val region: Component,
                                 val presses: Boolean,
                                 val holds: Boolean,
                                 val arrows: Boolean,
                                 val layers: Vector[Component])

package everyui.component

import everyui.Color

/** One bar of a `Bars`: how tall (0..1 of the chart), its colour, and what a pointer resting on it says. */
case class Bar(fraction: Double, color: Color, tooltip: String = "")

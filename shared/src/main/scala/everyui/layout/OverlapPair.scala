package everyui.layout

import everyui.component.{Component, Container}

/** Two children of `container` drawn over each other (`Overlaps`), with their kinds and boxes as told. */
case class OverlapPair(container: Container, first: Component, second: Component, boxes: String)

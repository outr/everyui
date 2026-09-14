package everyui.event

/** The last pointer event a handler consumed; a component under it does not fire it again. */
object Pointer {
  var consumed: Option[PointerEvent] = None
}

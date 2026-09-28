package everyui.monaco

/** How the cursor blinks (`cursorBlinking`). */
enum MonacoCursorBlinking(val wire: String) extends MonacoEnum {
  case Blink extends MonacoCursorBlinking("blink")
  case Smooth extends MonacoCursorBlinking("smooth")
  case Phase extends MonacoCursorBlinking("phase")
  case Expand extends MonacoCursorBlinking("expand")
  case Solid extends MonacoCursorBlinking("solid")
}

package everyui.monaco

/** The cursor's shape (`cursorStyle`). */
enum MonacoCursorStyle(val wire: String) extends MonacoEnum {
  case Line extends MonacoCursorStyle("line")
  case Block extends MonacoCursorStyle("block")
  case Underline extends MonacoCursorStyle("underline")
  case LineThin extends MonacoCursorStyle("line-thin")
  case BlockOutline extends MonacoCursorStyle("block-outline")
  case UnderlineThin extends MonacoCursorStyle("underline-thin")
}

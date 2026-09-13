package everyui.component.types

sealed abstract class WhiteSpace(val value: String)

object WhiteSpace:
  case object Normal extends WhiteSpace("normal")
  case object NoWrap extends WhiteSpace("nowrap")
  case object Pre extends WhiteSpace("pre")
  case object PreWrap extends WhiteSpace("pre-wrap")
  case object PreLine extends WhiteSpace("pre-line")

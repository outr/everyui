package universal.component.types

sealed abstract class PositionType(val name: String)

object PositionType:
  case object Static extends PositionType("")
  case object Relative extends PositionType("relative")
  case object Absolute extends PositionType("absolute")
  case object Fixed extends PositionType("fixed")
  case object Sticky extends PositionType("sticky")

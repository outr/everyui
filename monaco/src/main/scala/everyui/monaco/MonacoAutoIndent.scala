package everyui.monaco

/** How much a new line is indented for itself (`autoIndent`). */
enum MonacoAutoIndent(val wire: String) extends MonacoEnum {
  case None extends MonacoAutoIndent("none")
  case Keep extends MonacoAutoIndent("keep")
  case Brackets extends MonacoAutoIndent("brackets")
  case Advanced extends MonacoAutoIndent("advanced")
  case Full extends MonacoAutoIndent("full")
}

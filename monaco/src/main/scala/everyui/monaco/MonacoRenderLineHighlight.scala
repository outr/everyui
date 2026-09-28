package everyui.monaco

/** How the cursor's line is lit (`renderLineHighlight`). */
enum MonacoRenderLineHighlight(val wire: String) extends MonacoEnum {
  case None extends MonacoRenderLineHighlight("none")
  case Gutter extends MonacoRenderLineHighlight("gutter")
  case Line extends MonacoRenderLineHighlight("line")
  case All extends MonacoRenderLineHighlight("all")
}

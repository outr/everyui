package everyui.monaco

/** How foldable regions are found (`foldingStrategy`). */
enum MonacoFoldingStrategy(val wire: String) extends MonacoEnum {
  case Auto extends MonacoFoldingStrategy("auto")
  case Indentation extends MonacoFoldingStrategy("indentation")
}

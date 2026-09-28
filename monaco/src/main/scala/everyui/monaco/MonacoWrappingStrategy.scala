package everyui.monaco

/** How wrap points are found (`wrappingStrategy`). */
enum MonacoWrappingStrategy(val wire: String) extends MonacoEnum {
  case Simple extends MonacoWrappingStrategy("simple")
  case Advanced extends MonacoWrappingStrategy("advanced")
}

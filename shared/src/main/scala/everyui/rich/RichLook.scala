package everyui.rich

import everyui.Color

/** How a rich document is drawn: its words' size and face, their colours, and the colours a link, code and a quote are
  * picked out in. From the theme by default (`RichLook.themed`). */
case class RichLook(size: Double, family: String, text: Color, muted: Color, accent: Color, codeBackground: Color,
                    quoteBar: Color, selection: Color)

object RichLook {
  /** The theme's, at `size`. */
  def themed(size: Double = 15.0): RichLook = {
    import everyui.theme.Theme
    RichLook(size, Theme.family(), Theme.text(), Theme.muted(), Theme.accent(), Theme.control(), Theme.border(),
      Theme.accent().withAlpha(0.3))
  }
}

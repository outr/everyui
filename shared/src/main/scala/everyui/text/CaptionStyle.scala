package everyui.text

import everyui.Color

/** How the device's own caption settings say subtitles should look: the words' colour, what they sit on, the window
  * around them, the edge drawn round the letters (`none`, `outline`, `shadow`, `raised`, `depressed`) and its colour,
  * and how large, as a multiple of the usual size. */
case class CaptionStyle(text: Color, background: Color, window: Color, edge: String, edgeColor: Color, scale: Double)

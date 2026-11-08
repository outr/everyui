package everyui.widget

import everyui.font.MaterialIcon

/** Where a widget's picture comes from: an icon (drawn in the words' colour), an address to fetch it from as the
  * widget is drawn, or one of the app's own pictures (`assets/`). */
enum WidgetPicture {
  case Icon(icon: MaterialIcon)
  case Url(url: String)
  case Asset(path: String)
}

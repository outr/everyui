package everyui.widget

/** A colour in a widget, by its role in the theme (`Widgets.light` and `Widgets.dark`): drawn in the light or the dark
  * one as the device is set, and again as that changes where the platform can (Android 12 and later). `Surface` and
  * `Raised` are grounds, for a card (`WidgetGround`); the rest are for words and icons. */
enum WidgetColour {
  // new roles go at the end: a kept tree names each by its place here
  case Text, Muted, Accent, Danger, OnAccent, Surface, Raised
}

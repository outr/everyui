package everyui.widget

/** A colour in a widget, by its role in the theme (`Widgets.light` and `Widgets.dark`): drawn in the light or the dark
  * one as the device is set, and again as that changes where the platform can (Android 12 and later). */
enum WidgetColour {
  case Text, Muted, Accent, Danger, OnAccent
}

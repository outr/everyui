package everyui.kit

/** What a `Toast` says of what happened: `Info` (the default, as every toast was), `Success`, `Warning` or `Error`.
  * Toast's own draws all but Info with a bar of the theme's colour for it (success's, warning's, danger's) at
  * its start, and an Error stays two seconds longer; an app's `presenter` has it in its `ToastRequest`. */
enum ToastTone {
  case Info, Success, Warning, Error
}

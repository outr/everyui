package everyui.push

/** How much a channel's notices interrupt: Min (folded away, no icon in the status bar), Low (shown, silent), or
  * Default (with the device's sound). An ongoing notice is Low unless the app says otherwise. */
enum NoticeImportance {
  case Min, Low, Default
}

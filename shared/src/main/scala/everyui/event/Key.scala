package everyui.event

/** A key, by what it does rather than what the platform calls it: every remote's OK is `Enter` and its back is
  * `Escape`; a key with no name here is `Other`, and the event still carries the platform's name. */
enum Key {
  case Enter, Escape, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, PageUp, PageDown, Tab, Backspace, Space,
    MediaPlayPause, MediaRewind, MediaFastForward, MediaReplay, ContextMenu, Other
}


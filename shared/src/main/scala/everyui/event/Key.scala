package everyui.event

/** A key, by what it does rather than what the platform calls it: every remote's OK is `Enter`, and its back button
  * (Android's Back, the Roku's back, the Siri Remote's Menu, a TV browser's GoBack, a mouse's back button) is `Back`,
  * which returns to the previous screen; `Escape` is the keyboard's. A key with no name here is `Other`, and the event
  * still carries the platform's name. */
enum Key {
  case Enter, Escape, Back, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, PageUp, PageDown, Tab, Backspace, Space,
    MediaPlay, MediaPause, MediaPlayPause, MediaStop, MediaRewind, MediaFastForward, MediaReplay, ContextMenu, Other
}

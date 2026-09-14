package everyui.event

import everyui.event.Key.*

/** The keys by name; apart from the enum, since the emitter does not carry an enum's companion. */
object Keys {
  /** The key for a platform's name — the web's names, which every backend reports (`Platform.onKey`). */
  def of(name: String): Key =
    if (name == "Enter") Enter
    else if (name == "Escape") Escape
    else if (name == "ArrowUp") ArrowUp
    else if (name == "ArrowDown") ArrowDown
    else if (name == "ArrowLeft") ArrowLeft
    else if (name == "ArrowRight") ArrowRight
    else if (name == "PageUp") PageUp
    else if (name == "PageDown") PageDown
    else if (name == "Tab") Tab
    else if (name == "Backspace") Backspace
    else if (name == " ") Space
    else if (name == "MediaPlayPause") MediaPlayPause
    else if (name == "MediaRewind") MediaRewind
    else if (name == "MediaFastForward") MediaFastForward
    else if (name == "MediaReplay") MediaReplay
    else if (name == "ContextMenu") ContextMenu
    else Other

  def isArrow(key: Key): Boolean = key == ArrowUp || key == ArrowDown || key == ArrowLeft || key == ArrowRight
}

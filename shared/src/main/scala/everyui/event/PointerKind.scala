package everyui.event

/** What a pointer did. `Menu` asks for a context menu: the secondary button, or whatever the platform has for it. */
enum PointerKind {
  case Down, Up, Move, Enter, Leave, Click, Wheel, Menu
}

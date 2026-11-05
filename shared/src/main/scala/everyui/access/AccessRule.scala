package everyui.access

/** What `Access` checks a screen for, each a WCAG 2.2 AA success criterion, by its `id` in what is reported. */
enum AccessRule(val id: String, val criterion: String) {
  /** Focusable or clicked, with nothing a screen reader can say for it: no words, label, alt or tooltip (4.1.2). */
  case Name extends AccessRule("name", "4.1.2")
  /** Clicked, but nothing says what it is (a button, a link, a switch), so a screen reader calls it nothing (4.1.2). */
  case Role extends AccessRule("role", "4.1.2")
  /** Clicked, but nothing the keys reach: neither it nor anything holding it takes the focus (2.1.1). */
  case Keyboard extends AccessRule("keyboard", "2.1.1")
  /** Clicked, smaller than 24 by 24, with another target within the 24 about it (2.5.8). */
  case Target extends AccessRule("target", "2.5.8")
  /** Words under 4.5:1 against what is behind them (3:1 large, and for an icon) (1.4.3, 1.4.11). */
  case Contrast extends AccessRule("contrast", "1.4.3")
}

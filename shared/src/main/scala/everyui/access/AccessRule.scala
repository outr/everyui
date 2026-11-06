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
  /** Tab moves the focus to something above the last, in the same column, or before it in the same line: an order
    * that does not follow the reading order (2.4.3). */
  case Order extends AccessRule("order", "2.4.3")
  /** Words under 4.5:1 against what is behind them (3:1 large, and for an icon) (1.4.3, 1.4.11). */
  case Contrast extends AccessRule("contrast", "1.4.3")
  /** Shown past the screen's left or right edge, outside anything that scrolls or cuts what it holds: at the width the
    * screen has (320 across, at 400% of a 1280 window) the person cannot reach it without scrolling sideways (1.4.10). */
  case Reflow extends AccessRule("reflow", "1.4.10")
  /** A motion of its own that turns its opacity up and down more than three times a second, over more than a small
    * part of the screen (21,824 square pixels, WCAG's small safe area): a flash that can bring on a seizure (2.3.1). */
  case Flash extends AccessRule("flash", "2.3.1")
}

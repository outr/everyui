package everyui.component.types

/** Whether what a part of the screen says is read out as it changes (`Component.liveRegion`): not at all, the default;
  * when a screen reader next can, not cutting in (a status line, a count of results); or at once, cutting in on what
  * it is reading (an error that stops what the user is doing). ARIA's `aria-live`. */
enum Live {
  case Off, Polite, Assertive
}

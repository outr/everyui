package everyui.list

import everyui.font.MaterialIcon

/** A thing a `RichList`'s section does from its header ("New conversation"), an item from its row's end or under its
  * text where it is expanded (`RichExpansion`), or a `StackedPane` from its header: `icon` drawn as a small button,
  * `label` its tooltip and what a screen reader calls it (and, under an expanded item, its words), and `run` what it
  * does, without opening or closing anything. `shown` shows it always, rather than only while the pointer or the focus
  * is there (a section's always shows). A `danger`ous one (Delete) is drawn in the danger colour where it has words. */
case class RichAction(icon: MaterialIcon, label: String, run: () => Unit, shown: Boolean = false, danger: Boolean = false)

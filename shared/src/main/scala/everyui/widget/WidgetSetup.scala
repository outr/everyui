package everyui.widget

/** What a placed widget was set to in its configure screen, kept for it by its id until it is removed: its kind, the
  * choices it draws from, and whether it shows counts alone. */
case class WidgetSetup(kind: String, choices: Set[String], countsOnly: Boolean)

object WidgetSetup {
  /** What a widget of `kind` draws from and its configure screen opens on: what it was set to, or the kind's defaults
    * among `offered` where nothing was chosen, its "counts only" kept. Nothing chosen is a widget never set (placed
    * with its configure screen passed by) or kept with no choices, as one placed while the app had none to offer
    * (signed out) was before; a widget draws from something, so neither is taken for a choice of none. */
  def resolved(kind: WidgetKind, offered: Vector[WidgetChoice], saved: Option[WidgetSetup]): WidgetSetup =
    saved.filter(_.kind == kind.key) match {
      case Some(set) if set.choices.nonEmpty => set
      case Some(set) => set.copy(choices = kind.defaults(offered))
      case None => WidgetSetup(kind.key, kind.defaults(offered), countsOnly = false)
    }
}

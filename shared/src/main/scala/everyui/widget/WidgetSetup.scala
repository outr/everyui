package everyui.widget

/** What a placed widget was set to in its configure screen, kept for it by its id until it is removed: its kind, the
  * choices it draws from, whether it shows counts alone, and the option chosen for each of its kind's settings
  * (`WidgetKind.settings`), by the setting's key. A setup kept before settings were offered has none. */
case class WidgetSetup(kind: String, choices: Set[String], countsOnly: Boolean,
                       settings: Map[String, String] = Map.empty)

object WidgetSetup {
  /** What a widget of `kind` draws from and its configure screen opens on: what it was set to, or the kind's defaults
    * among `offered` where nothing was chosen, its "counts only" kept, and each of the kind's settings as `settled`
    * has it. Nothing chosen is a widget never set (placed with its configure screen passed by) or kept with no
    * choices, as one placed while the app had none to offer (signed out) was before; a widget draws from something,
    * so neither is taken for a choice of none. */
  def resolved(kind: WidgetKind, offered: Vector[WidgetChoice], saved: Option[WidgetSetup]): WidgetSetup = {
    val set = saved.filter(_.kind == kind.key) match {
      case Some(set) if set.choices.nonEmpty => set
      case Some(set) => set.copy(choices = kind.defaults(offered))
      case None => WidgetSetup(kind.key, kind.defaults(offered), countsOnly = false)
    }
    set.copy(settings = settled(kind, set.settings))
  }

  /** What a widget of `kind` is handed as its settings (`WidgetRequest.settings`): one for each setting the kind
    * offers, the option `saved` where it was chosen and is still offered, and the setting's default otherwise (never
    * set, kept before the setting was offered, or its option since taken away). A saved key the kind no longer
    * offers is left out. */
  def settled(kind: WidgetKind, saved: Map[String, String]): Map[String, String] =
    kind.settings.map(s => s.key -> s.resolve(saved.get(s.key))).toMap
}

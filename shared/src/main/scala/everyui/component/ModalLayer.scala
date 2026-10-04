package everyui.component

/** One dialog on [[Modal]]'s stack: the sheet it sits on, the panel it is, what had the focus before it (and gets it
  * back when it closes), what hears it close, what it wants focused, whether Back or a click on the sheet may close
  * it, whether the panel is centred on the sheet, and whether it is kept whole once it closes, to be put up again. Each
  * layer hears Back for itself, registered as it goes up, so the handlers of the dialog under it (registered after that
  * one went up) are exactly where they were once this one is gone. */
private[component] class ModalLayer(val sheet: Container,
                                    val panel: Container,
                                    var before: Option[Component],
                                    val after: () => Unit,
                                    val inside: Component,
                                    val dismissible: Boolean,
                                    val centred: Boolean,
                                    val keep: Boolean) {
  /** What stops this layer hearing Back. */
  var release: () => Unit = () => ()
}

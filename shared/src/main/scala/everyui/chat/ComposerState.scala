package everyui.chat

/** What a [[Composer]] is doing when a key comes, which is all `ComposerKeys.action` decides by: whether Enter types
  * a new line (`touch`: a keyboard on the screen), whether it is in use (`enabled`) and a reply is coming (`busy`),
  * whether its field is being typed in (`editing`), whether there is anything to send (`sendable`), and whether a
  * suggestion or a list of completions shows. */
case class ComposerState(touch: Boolean = false, enabled: Boolean = true, busy: Boolean = false, editing: Boolean = true,
                         sendable: Boolean = false, suggesting: Boolean = false, completing: Boolean = false)

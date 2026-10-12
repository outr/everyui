package everyui.chat

/** What a [[Composer]] is doing when a key comes, which is all `ComposerKeys.action` decides by: whether Enter types
  * a new line (`touch`: a keyboard on the screen), whether it is in use (`enabled`) and a reply is coming (`busy`),
  * whether its field is being typed in (`editing`), whether there is anything to send (`sendable`), and whether a
  * suggestion or a list of completions shows. Where messages are queued while a reply comes (`queueing`) Enter queues
  * rather than holding; where a queue shows above the field (`queued`), Up with the caret at the field's very start
  * (`atStart`) goes to it. */
case class ComposerState(touch: Boolean = false, enabled: Boolean = true, busy: Boolean = false, editing: Boolean = true,
                         sendable: Boolean = false, suggesting: Boolean = false, completing: Boolean = false,
                         queueing: Boolean = false, queued: Boolean = false, atStart: Boolean = false)

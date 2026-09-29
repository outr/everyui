package everyui.chat

/** What a [[Composer]] says, for a screen reader and on its controls, in English unless an app passes its own. */
case class ComposerWords(send: String = "Send", stop: String = "Stop", attach: String = "Attach files",
                         more: String = "More", remove: String = "Remove", drop: String = "Drop files to attach",
                         suggestion: String = "Suggestion", accept: String = "Tab or Right arrow accepts it",
                         tooLarge: String = "is too large to attach", notAccepted: String = "cannot be attached here",
                         unavailable: String = "unavailable")

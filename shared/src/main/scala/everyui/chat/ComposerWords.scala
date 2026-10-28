package everyui.chat

import everyui.i18n.Words

/** What a [[Composer]] says, for a screen reader and on its controls: by default in the language in use (Words'
  * "composer.send", … in its eleven languages), unless an app passes its own. */
case class ComposerWords(send: String = Words("composer.send"), stop: String = Words("composer.stop"),
                         attach: String = Words("composer.attach"), more: String = Words("composer.more"),
                         remove: String = Words("composer.remove"), drop: String = Words("composer.drop"),
                         suggestion: String = Words("composer.suggestion"), accept: String = Words("composer.accept"),
                         tooLarge: String = Words("composer.tooLarge"),
                         notAccepted: String = Words("composer.notAccepted"),
                         unavailable: String = Words("composer.unavailable"), add: String = Words("composer.add"),
                         upload: String = Words("composer.upload"), mention: String = Words("composer.mention"))

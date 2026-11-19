package everyui.chat

import everyui.component.Component
import everyui.font.MaterialIcon

/** One of the app's own chips in a [[Composer]]'s row of attachments (`Composer.extraChips`), after the files and drawn
  * as they are: something set on the draft rather than a file, such as where the message goes. `key` is what it is known
  * by, so a chip changed under the same key is changed in place. A press on it (a click, Enter, a remote's OK) is
  * `onPress`, handed the chip so that a menu can open at it; where `onRemove` is given, a × after it takes it off.
  * `tooltip` is what a tip on it says, else its label where that is cut short. A chip is the draft's, never a file:
  * nothing of it is sent, and the app keeps or clears it as messages go.
  *
  * {{{
  *   composer.extraChips @= List(ComposerChip("location", MaterialIcon.Place, "Lisbon",
  *     onPress = at => Menu.below(at, cities), onRemove = Some(() => location @= None)))
  * }}} */
case class ComposerChip(key: String, icon: MaterialIcon, label: String, tooltip: Option[String] = None,
                        onPress: Component => Unit = (_: Component) => (), onRemove: Option[() => Unit] = None)

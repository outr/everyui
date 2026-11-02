package everyui.chat

/** One of the choices a [[Composer]]'s autocomplete offers for the word being typed: `label` as the list shows it
  * ("Ada Lovelace"), a line of `detail` beside it where there is one ("@ada"), and `insert`, what takes the word's place
  * in the message, trigger and all ("@ada"); and a `picture` before the label where it has one (a site-absolute path
  * or an address: an avatar, one of the app's own emoji), fitted in a square a line tall. */
case class Completion(label: String, insert: String, detail: Option[String] = None, picture: Option[String] = None)

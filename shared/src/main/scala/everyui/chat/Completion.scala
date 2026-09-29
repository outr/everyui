package everyui.chat

/** One of the choices a [[Composer]]'s autocomplete offers for the word being typed: `label` as the list shows it
  * ("Ada Lovelace"), a line of `detail` beside it where there is one ("@ada"), and `insert`, what takes the word's place
  * in the message, trigger and all ("@ada"). */
case class Completion(label: String, insert: String, detail: Option[String] = None)

package everyui.text

import everyui.async.Task
import everyui.chat.Completion

/** Where a field's completions come from ([[everyui.component.TextInput]]'s and [[TextArea]]'s `completions`):
  * `complete` is handed the piece being typed (`CompletionToken`) and answers, as late as it likes, with what to
  * offer for it (`Completion`: its label, a line of detail, what goes in, a picture); an answer for a piece since
  * typed on is dropped, and one of nothing closes the list. With no `separators` the piece is the whole field (a
  * search box, a city); with some (",;" for a list of addresses, "," for tags) it is what the caret is in between
  * them, its leading spaces left out, so "ann@example.com, os" asks for "os". A piece shorter than `minimum` asks
  * nothing. The one chosen takes the piece's place, `after` following it (", " to start the next address) unless a
  * separator follows already. The list shows `rows` at most, and scrolls through the rest.
  * {{{
  *   to.completions @= Some(CompletionSource(typed => people.find(typed).map(_.map(p =>
  *     Completion(p.name + " <" + p.email + ">", p.email, picture = p.avatar))), separators = ",;", after = ", "))
  * }}} */
case class CompletionSource(complete: String => Task[Vector[Completion]], separators: String = "", after: String = "",
                            rows: Int = 6, minimum: Int = 1)

package everyui.monaco

import scala.concurrent.Future

/** Suggestions for a language (`MonacoLanguages.registerCompletionProvider`), asked for as the user types one of
  * `triggerCharacters`, or asks (Ctrl+Space). */
trait MonacoCompletionProvider {
  def triggerCharacters: List[String] = Nil
  def provideCompletionItems(params: MonacoCompletionParams): Future[MonacoCompletionList]
}

object MonacoCompletionProvider {
  /** A provider from a function. */
  def apply(triggers: String*)(f: MonacoCompletionParams => Future[MonacoCompletionList]): MonacoCompletionProvider =
    new MonacoCompletionProvider {
      override def triggerCharacters: List[String] = triggers.toList
      def provideCompletionItems(params: MonacoCompletionParams): Future[MonacoCompletionList] = f(params)
    }
}

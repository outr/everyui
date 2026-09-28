package everyui.monaco

import scala.concurrent.Future

/** Signature help for a language (`MonacoLanguages.registerSignatureHelpProvider`), opened by `triggerCharacters`
  * (typically `(` and `,`) and asked again on `retriggerCharacters`. */
trait MonacoSignatureHelpProvider {
  def triggerCharacters: List[String] = Nil
  def retriggerCharacters: List[String] = Nil
  def provideSignatureHelp(params: MonacoSignatureHelpParams): Future[Option[MonacoSignatureHelp]]
}

object MonacoSignatureHelpProvider {
  def apply(triggers: List[String], retriggers: List[String] = Nil)
           (f: MonacoSignatureHelpParams => Future[Option[MonacoSignatureHelp]]): MonacoSignatureHelpProvider =
    new MonacoSignatureHelpProvider {
      override def triggerCharacters: List[String] = triggers
      override def retriggerCharacters: List[String] = retriggers
      def provideSignatureHelp(params: MonacoSignatureHelpParams): Future[Option[MonacoSignatureHelp]] = f(params)
    }
}

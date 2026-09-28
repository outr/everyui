package everyui.monaco

import scala.concurrent.Future

/** The symbols of a document, for the outline and Go to Symbol (`MonacoLanguages.registerDocumentSymbolProvider`).
  * Asked with the model; the position is its start. */
trait MonacoDocumentSymbolProvider {
  def provideDocumentSymbols(params: MonacoProviderParams): Future[List[MonacoDocumentSymbol]]
}

object MonacoDocumentSymbolProvider {
  def apply(f: MonacoProviderParams => Future[List[MonacoDocumentSymbol]]): MonacoDocumentSymbolProvider =
    new MonacoDocumentSymbolProvider {
      def provideDocumentSymbols(params: MonacoProviderParams): Future[List[MonacoDocumentSymbol]] = f(params)
    }
}

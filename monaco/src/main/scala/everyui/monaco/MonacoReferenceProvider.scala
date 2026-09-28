package everyui.monaco

import scala.concurrent.Future

/** Find All References for a language (`MonacoLanguages.registerReferenceProvider`): every place the symbol at the
  * position is used. */
trait MonacoReferenceProvider {
  def provideReferences(params: MonacoReferenceParams): Future[List[MonacoLocation]]
}

object MonacoReferenceProvider {
  def apply(f: MonacoReferenceParams => Future[List[MonacoLocation]]): MonacoReferenceProvider = new MonacoReferenceProvider {
    def provideReferences(params: MonacoReferenceParams): Future[List[MonacoLocation]] = f(params)
  }
}

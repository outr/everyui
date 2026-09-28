package everyui.monaco

import scala.concurrent.Future

/** Hovers for a language (`MonacoLanguages.registerHoverProvider`): none for nothing to say. */
trait MonacoHoverProvider {
  def provideHover(params: MonacoProviderParams): Future[Option[MonacoHover]]
}

object MonacoHoverProvider {
  def apply(f: MonacoProviderParams => Future[Option[MonacoHover]]): MonacoHoverProvider = new MonacoHoverProvider {
    def provideHover(params: MonacoProviderParams): Future[Option[MonacoHover]] = f(params)
  }
}

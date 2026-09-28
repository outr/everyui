package everyui.monaco

import scala.concurrent.Future

/** Go-to-definition for a language (`MonacoLanguages.registerDefinitionProvider`). */
trait MonacoDefinitionProvider {
  def provideDefinition(params: MonacoProviderParams): Future[List[MonacoLocation]]
}

object MonacoDefinitionProvider {
  def apply(f: MonacoProviderParams => Future[List[MonacoLocation]]): MonacoDefinitionProvider = new MonacoDefinitionProvider {
    def provideDefinition(params: MonacoProviderParams): Future[List[MonacoLocation]] = f(params)
  }
}

package everyui.monaco

/** What a language provider is asked about: the model (`uri`, its language, its `value` as it was asked about) and
  * the position. */
case class MonacoProviderParams(uri: String, languageId: String, value: String, position: MonacoPosition)

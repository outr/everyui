package everyui.monaco

/** What a completion provider is asked about: the model, the position and why. */
case class MonacoCompletionParams(uri: String, languageId: String, value: String, position: MonacoPosition,
                                  context: MonacoCompletionContext)

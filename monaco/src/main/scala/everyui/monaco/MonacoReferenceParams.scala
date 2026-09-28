package everyui.monaco

/** What a reference provider is asked about: the model, the position, and whether the declaration counts. */
case class MonacoReferenceParams(uri: String, languageId: String, value: String, position: MonacoPosition,
                                 includeDeclaration: Boolean)

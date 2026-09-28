package everyui.monaco

/** What a signature help provider is asked about. */
case class MonacoSignatureHelpParams(uri: String, languageId: String, value: String, position: MonacoPosition,
                                     context: MonacoSignatureHelpContext)

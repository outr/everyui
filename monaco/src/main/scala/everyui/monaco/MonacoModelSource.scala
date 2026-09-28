package everyui.monaco

/** Another file's text and language, as the app gives it for a model Monaco needs (MonacoLanguages.registerModelResolver). */
case class MonacoModelSource(text: String, language: String = "")

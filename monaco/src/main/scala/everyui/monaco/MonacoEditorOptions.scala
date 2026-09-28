package everyui.monaco

import fabric.*

/** The editor's options, typed: Monaco's `IEditorOptions` for the ones an app reaches for, each named as Monaco names
  * it and left out when not set, so `MonacoController.updateOptions` changes only what it sets. The rest of Monaco's
  * several hundred go in `raw`, which wins over the typed fields. */
case class MonacoEditorOptions(
  // layout and what shows
  readOnly: Option[Boolean] = None,
  domReadOnly: Option[Boolean] = None,
  wordWrap: Option[MonacoWordWrap] = None,
  wordWrapColumn: Option[Int] = None,
  lineNumbers: Option[MonacoLineNumbersStyle] = None,
  lineNumbersMinChars: Option[Int] = None,
  glyphMargin: Option[Boolean] = None,
  folding: Option[Boolean] = None,
  foldingStrategy: Option[MonacoFoldingStrategy] = None,
  showFoldingControls: Option[MonacoShowFoldingControls] = None,
  renderWhitespace: Option[MonacoRenderWhitespace] = None,
  renderLineHighlight: Option[MonacoRenderLineHighlight] = None,
  renderFinalNewline: Option[MonacoRenderFinalNewline] = None,
  scrollBeyondLastLine: Option[Boolean] = None,
  scrollBeyondLastColumn: Option[Int] = None,
  smoothScrolling: Option[Boolean] = None,
  roundedSelection: Option[Boolean] = None,
  fixedOverflowWidgets: Option[Boolean] = None,
  ariaLabel: Option[String] = None,
  // the face
  fontFamily: Option[String] = None,
  fontSize: Option[Double] = None,
  fontWeight: Option[String] = None,
  fontLigatures: Option[Boolean] = None,
  letterSpacing: Option[Double] = None,
  lineHeight: Option[Double] = None,
  // indentation
  tabSize: Option[Int] = None,
  insertSpaces: Option[Boolean] = None,
  detectIndentation: Option[Boolean] = None,
  trimAutoWhitespace: Option[Boolean] = None,
  autoIndent: Option[MonacoAutoIndent] = None,
  // the cursor and the mouse
  cursorBlinking: Option[MonacoCursorBlinking] = None,
  cursorStyle: Option[MonacoCursorStyle] = None,
  cursorWidth: Option[Int] = None,
  cursorSmoothCaretAnimation: Option[Boolean] = None,
  cursorSurroundingLines: Option[Int] = None,
  mouseWheelZoom: Option[Boolean] = None,
  multiCursorModifier: Option[MonacoMultiCursorModifier] = None,
  // behaviour
  formatOnPaste: Option[Boolean] = None,
  formatOnType: Option[Boolean] = None,
  autoClosingBrackets: Option[MonacoAutoClosingStrategy] = None,
  autoClosingQuotes: Option[MonacoAutoClosingStrategy] = None,
  autoSurround: Option[String] = None,
  matchBrackets: Option[MonacoMatchBrackets] = None,
  bracketPairColorization: Option[Boolean] = None,
  linkedEditing: Option[Boolean] = None,
  dragAndDrop: Option[Boolean] = None,
  contextmenu: Option[Boolean] = None,
  copyWithSyntaxHighlighting: Option[Boolean] = None,
  // suggestions
  quickSuggestions: Option[Boolean] = None,
  quickSuggestionsDelay: Option[Int] = None,
  acceptSuggestionOnEnter: Option[MonacoAcceptSuggestion] = None,
  acceptSuggestionOnCommitCharacter: Option[Boolean] = None,
  tabCompletion: Option[MonacoTabCompletion] = None,
  snippetSuggestions: Option[MonacoSnippetSuggestions] = None,
  suggestOnTriggerCharacters: Option[Boolean] = None,
  wordBasedSuggestions: Option[Boolean] = None,
  parameterHints: Option[Boolean] = None,
  // scrolling
  mouseWheelScrollSensitivity: Option[Double] = None,
  fastScrollSensitivity: Option[Double] = None,
  scrollPredominantAxis: Option[Boolean] = None,
  // accessibility
  accessibilitySupport: Option[MonacoAccessibilitySupport] = None,
  accessibilityPageSize: Option[Int] = None,
  // the rest of what shows and what input does
  rulers: Option[List[Int]] = None,
  showUnused: Option[Boolean] = None,
  emptySelectionClipboard: Option[Boolean] = None,
  useTabStops: Option[Boolean] = None,
  columnSelection: Option[Boolean] = None,
  renderControlCharacters: Option[Boolean] = None,
  disableLayerHinting: Option[Boolean] = None,
  disableMonospaceOptimizations: Option[Boolean] = None,
  hideCursorInOverviewRuler: Option[Boolean] = None,
  tabFocusMode: Option[Boolean] = None,
  multiCursorPaste: Option[MonacoMultiCursorPaste] = None,
  wrappingIndent: Option[MonacoWrappingIndent] = None,
  wrappingStrategy: Option[MonacoWrappingStrategy] = None,
  // nested
  minimap: Option[MonacoMinimapOptions] = None,
  scrollbar: Option[MonacoScrollbarOptions] = None,
  padding: Option[MonacoPaddingOptions] = None,
  stickyScroll: Option[MonacoStickyScrollOptions] = None,
  hover: Option[MonacoHoverOptions] = None,
  // any other
  raw: Json = obj()
) extends MonacoOptionSet {
  // two booleans Monaco takes as objects
  override def toJson: Json = {
    val json = super.toJson
    def enabled(name: String): Option[(String, Json)] =
      json.get(name).filter(_.isBool).map(b => name -> obj("enabled" -> b))
    val fixed = List(enabled("bracketPairColorization"), enabled("parameterHints")).flatten
    if (fixed.isEmpty) json else Wire.merge(json, obj(fixed*))
  }

  /** These options with `other`'s set over them: layered defaults and overrides. Nested sets are replaced whole; `raw`
    * is merged. */
  def mergedWith(other: MonacoEditorOptions): MonacoEditorOptions = {
    val merged = productIterator.zip(other.productIterator).map {
      case (mine: Option[?], theirs: Option[?]) => theirs.orElse(mine)
      case (mine: Json, theirs: Json) => Wire.merge(mine, theirs)
      case (_, theirs) => theirs
    }.toArray
    summon[scala.deriving.Mirror.ProductOf[MonacoEditorOptions]].fromProduct(Tuple.fromArray(merged.asInstanceOf[Array[Object]]))
  }
}

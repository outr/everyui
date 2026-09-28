package everyui.monaco

import fabric.*

/** The scrollbars (`scrollbar`): Monaco's `IEditorScrollbarOptions`. */
case class MonacoScrollbarOptions(vertical: Option[MonacoScrollbarVisibility] = None,
                                  horizontal: Option[MonacoScrollbarVisibility] = None, arrowSize: Option[Int] = None,
                                  useShadows: Option[Boolean] = None, verticalHasArrows: Option[Boolean] = None,
                                  horizontalHasArrows: Option[Boolean] = None, handleMouseWheel: Option[Boolean] = None,
                                  alwaysConsumeMouseWheel: Option[Boolean] = None, horizontalScrollbarSize: Option[Int] = None,
                                  verticalScrollbarSize: Option[Int] = None, verticalSliderSize: Option[Int] = None,
                                  horizontalSliderSize: Option[Int] = None, scrollByPage: Option[Boolean] = None,
                                  ignoreHorizontalScrollbarInContentHeight: Option[Boolean] = None,
                                  raw: Json = obj()) extends MonacoOptionSet

package everyui.monaco

import fabric.*

/** The minimap (`minimap`): Monaco's `IEditorMinimapOptions`. */
case class MonacoMinimapOptions(enabled: Option[Boolean] = None, autohide: Option[Boolean] = None,
                                side: Option[MonacoMinimapSide] = None, size: Option[MonacoMinimapSize] = None,
                                showSlider: Option[MonacoMinimapSlider] = None, renderCharacters: Option[Boolean] = None,
                                maxColumn: Option[Int] = None, scale: Option[Double] = None,
                                showRegionSectionHeaders: Option[Boolean] = None, showMarkSectionHeaders: Option[Boolean] = None,
                                sectionHeaderFontSize: Option[Double] = None, sectionHeaderLetterSpacing: Option[Double] = None,
                                raw: Json = obj()) extends MonacoOptionSet

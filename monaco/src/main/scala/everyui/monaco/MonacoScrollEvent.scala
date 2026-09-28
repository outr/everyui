package everyui.monaco

import fabric.rw.*

/** The editor scrolled, or its scrollable size changed (`onDidScroll`). */
case class MonacoScrollEvent(scrollTop: Double, scrollLeft: Double, scrollHeight: Double, scrollWidth: Double,
                             scrollTopChanged: Boolean = false, scrollLeftChanged: Boolean = false,
                             scrollHeightChanged: Boolean = false, scrollWidthChanged: Boolean = false) derives RW

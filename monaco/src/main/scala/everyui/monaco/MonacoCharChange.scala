package everyui.monaco

import fabric.rw.*

/** Characters that changed within a changed region: where they were in the original, and where they are now. */
case class MonacoCharChange(originalRange: MonacoRange, modifiedRange: MonacoRange) derives RW

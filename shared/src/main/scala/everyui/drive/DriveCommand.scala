package everyui.drive

/** One line of the drive (`Drive.parse`): its verb, lower-cased ("tap", "key", "press"…), the numbers after it, and the
  * rest of the line as words (a key's name, what to type, a label), its quotes taken off. */
case class DriveCommand(verb: String, numbers: Vector[Double], words: String)

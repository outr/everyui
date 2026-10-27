package everyui.file

import everyui.async.Task

/** Where a file saved a piece at a time goes (`Files.saveStream`): each piece written after the one before. */
trait FileSink {
  /** `bytes` written after what was written before: true once they are, false where the file can take no more (it
    * failed, or the person let it go), after which nothing more is written. */
  def write(bytes: Array[Byte]): Task[Boolean]
}

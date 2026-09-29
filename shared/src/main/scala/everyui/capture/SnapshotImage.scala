package everyui.capture

import everyui.async.Task
import everyui.file.{Files, SaveResult}
import everyui.render.SnapshotAccess

/** A picture the app took of itself (`Snapshot`): a PNG's bytes, `width` by `height` pixels. The bytes are the file as
  * it is saved or sent: a multipart part (`FormPart.file("shot", "shot.png", image.png, image.mime)`) or a file for the
  * person to keep (`save`).
  *
  * {{{
  *   Snapshot.component(panel).map(_.foreach(image => image.save("panel.png").map(said).start())).start()
  * }}} */
case class SnapshotImage(png: Array[Byte], width: Int, height: Int) {
  /** Its type, as a part or a file goes. */
  def mime: String = "image/png"

  /** The picture saved where the person chooses, as `Files.save` saves bytes; best started from a click. */
  def save(name: String = "snapshot.png"): Task[SaveResult] = Files.save(png, name, Some(mime))

  /** An address an `ImageView` can show it from: an object URL on the web, a file of the app's own elsewhere. Made the
    * first time it is asked for; `Files.release(preview)` lets the web's go once nothing shows it. */
  lazy val preview: String = SnapshotAccess.preview(png)
}

package everyui.file

import everyui.async.Task

/** A file the person handed the app: chosen in the platform's picker (`Files.pick`) or dropped on a component
  * (`Files.dropTarget`). `size` is in bytes and `mime` its type ("image/png"; by its name where the platform says
  * nothing). `bytes` reads all of it, each time it is started, and completes on the thread that draws. `preview` is an
  * address an `ImageView` can show it from, for a picture where the platform has one cheaply.
  *
  * {{{
  *   Files.pick(List("image/\*", ".txt")).map(_.foreach(f => f.bytes.map(b => upload(f.name, b)).start())).start()
  * }}} */
case class PickedFile(name: String, size: Long, mime: String, bytes: Task[Array[Byte]], preview: Option[String] = None)

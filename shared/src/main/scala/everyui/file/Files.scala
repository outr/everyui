package everyui.file

import everyui.async.Task
import everyui.component.Component
import everyui.render.FileAccess

/** Files from the person, and files for them: chosen in the platform's own picker, dragged onto a component from
  * outside the app, or saved where they choose. The web takes them with a file input and the page's drag and drop, the
  * desktop with its file dialog and the window's, Android with the system's document picker and its drag and drop. A
  * Roku and an Apple TV have none of it, and say so (`pickable`, `droppable`, `savable` false), so an app hides what
  * would offer them.
  *
  * {{{
  *   if (Files.pickable) attach.event.click.attach(_ => Files.pick(List("image/\*")).map(add).start())
  *   Files.dropTarget(panel)(over => panel.borderWidth @= (if (over) 2.0 else 0.0))(add)
  *   Files.save(csv.getBytes("UTF-8"), "report.csv", Some("text/csv")).map(said).start()
  * }}} */
object Files {
  /** Whether this platform has a picker to choose files in. */
  def pickable: Boolean = FileAccess.pickable()

  /** Whether files can be dropped on components here. */
  def droppable: Boolean = FileAccess.droppable()

  /** The files chosen in the platform's picker, which offers those of `types` (the web's `accept` words: "image/\*",
    * "text/plain", ".md"; empty for any), several at once with `multiple`. Nothing chosen, or no picker, is an empty
    * list. */
  def pick(types: List[String] = Nil, multiple: Boolean = true): Task[List[PickedFile]] =
    Task.async[List[PickedFile]](done => FileAccess.pick(types, multiple, done))

  /** Done with a file's `preview` address: the web lets its object URL (and the file it holds) go; elsewhere nothing is
    * held. After this the address shows nothing. */
  def release(preview: String): Unit = FileAccess.release(preview)

  /** Files dropped on `target` from outside the app: `hover` hears true while files are held over it and false once
    * they leave or land, and `dropped` what landed. Nothing happens where files cannot be dropped. */
  def dropTarget(target: Component)(hover: Boolean => Unit)(dropped: List[PickedFile] => Unit): Unit =
    FileAccess.onDrop(target.node, hover, dropped)

  /** Whether this platform can save a file where the person chooses. */
  def savable: Boolean = FileAccess.savable()

  /** `bytes` saved as a file, the platform's way: the browser's save picker where it has one (Chromium's), otherwise a
    * download; the desktop's save dialog; Android's document creator. `name` is the name suggested and `mime` its type
    * (by the name's extension when None). Best started from a click, since a browser opens its picker only for one. */
  def save(bytes: Array[Byte], name: String, mime: Option[String] = None): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.save(bytes, name, mime.getOrElse(FileTypes.of(name)), done))

  /** What `url` answers with, saved as `save` saves bytes: fetched once the person has chosen where (a browser without
    * a picker downloads it itself). A relative URL is found against `Platform.origin()`, and on the web a URL of
    * another origin must allow the page to read it (CORS), or the browser without a picker may open it instead. */
  def saveUrl(url: String, name: String, mime: Option[String] = None): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.saveUrl(absolute(url), name, mime.getOrElse(FileTypes.of(name)), done))

  private def absolute(url: String): String = {
    val origin = everyui.render.Platform.origin()
    if (url.contains("://") || url.startsWith("blob:") || url.startsWith("data:") || origin.isEmpty) url
    else if (url.startsWith("//")) origin.substring(0, origin.indexOf(":") + 1) + url
    else origin.stripSuffix("/") + (if (url.startsWith("/")) url else "/" + url)
  }
}

package everyui.render

import everyui.async.Task
import everyui.component.types.PasteAction
import everyui.file.PickedFile
import javafx.scene.control.TextInputControl
import javafx.scene.image.PixelFormat
import javafx.scene.input.Clipboard

import scala.jdk.CollectionConverters.*

/** A field's pastes put to the app before they land (`InputView.pasted`). JavaFX's Ctrl+V (Cmd+V on a Mac), Shift+Insert
  * and the context menu's Paste all call the control's `paste`, which the fields made here override (`PastingTextField`,
  * `PastingPasswordField`, `PastingTextArea`) to come here first. Words put in the paste's place replace the selection
  * as the paste would, and JavaFX's undo takes them back as one edit. A clipboard with no words (a picture, files)
  * pastes as it would, and a field that is not editable (read-only, or out of use) takes no paste at all.
  *
  * Where the field takes a paste's pictures and files (`InputView.pastedFiles`, by `hookFiles`), the clipboard's files
  * (a file manager's copy) are handed over as dropped ones are, else its picture (a screenshot tool's, an image
  * viewer's), encoded as a PNG on a thread of its own with no name, for the field to number. Words beside them still
  * land as words, but for a file manager's, which are only the copied files' paths or addresses. */
object Pasting {
  private val key = "everyui.pasted"
  private val filesKey = "everyui.pastedFiles"

  /** `f` asked about each paste into `field`. */
  def hook(field: TextInputControl, f: String => PasteAction): Unit = field.getProperties.put(key, f)

  /** A paste's pictures and files handed to `f`, where `wanted` says the field takes them. */
  def hookFiles(field: TextInputControl, wanted: () => Boolean, f: List[PickedFile] => Unit): Unit =
    field.getProperties.put(filesKey, (wanted, f))

  /** A paste into `field`: `own` is the control's own paste, for one that lands as it is. */
  def paste(field: TextInputControl, own: () => Unit): Unit = {
    val clipboard = Clipboard.getSystemClipboard
    val words = if (clipboard.hasString) Option(clipboard.getString).getOrElse("") else ""
    val files = if (field.isEditable) carried(field, clipboard) else None
    // a file manager's words are the paths of the files it copied, which go as files
    val said = files.fold(words)(_ => if (onlyPaths(words, clipboard)) "" else words)
    Option(field.getProperties.get(key)) match {
      // a field not editable (read-only, or out of use) takes no paste, and its app is not asked about one
      case _ if !field.isEditable => ()
      case Some(f: Function1[?, ?]) if said.nonEmpty =>
        f.asInstanceOf[String => PasteAction](said) match {
          case PasteAction.Insert => if (said == words) own() else field.replaceSelection(said)
          case PasteAction.Ignore => ()
          case PasteAction.InsertText(text) => field.replaceSelection(text)
        }
      case _ if files.nonEmpty => if (said.nonEmpty) field.replaceSelection(said)
      case _ => own()
    }
    files.foreach(hand => hand())
  }

  // what hands the clipboard's files or picture to the field, where it takes them and the clipboard has any
  private def carried(field: TextInputControl, clipboard: Clipboard): Option[() => Unit] =
    Option(field.getProperties.get(filesKey)).collect { case (w: Function0[?], f: Function1[?, ?]) =>
      (w.asInstanceOf[() => Boolean], f.asInstanceOf[List[PickedFile] => Unit])
    }.filter(_._1()).flatMap { case (_, f) =>
      val listed = if (clipboard.hasFiles) clipboard.getFiles.asScala.toList.filter(_.isFile) else Nil
      if (listed.nonEmpty) Some(() => f(listed.map(FileAccess.picked)))
      else if (clipboard.hasImage) Option(clipboard.getImage).filter(i => i.getWidth >= 1.0 && i.getHeight >= 1.0)
        .map(image => () => picture(image, f))
      else None
    }

  // a picture as a PNG, encoded on a thread of its own and handed over on the one that draws
  private def picture(image: javafx.scene.image.Image, f: List[PickedFile] => Unit): Unit = {
    val width = image.getWidth.toInt
    val height = image.getHeight.toInt
    val argb = new Array[Int](width * height)
    image.getPixelReader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance, argb, 0, width)
    Platform.offThread(() => {
      val png = SnapshotAccess.encoded(width, height, argb)
      Platform.later(() => f(List(PickedFile("", png.length.toLong, "image/png", Task.pure(png),
        Some(FileAccess.source(png, "image/png"))))))
    })
  }

  // whether every line of `words` is the path or the address of one of the clipboard's files
  private def onlyPaths(words: String, clipboard: Clipboard): Boolean = clipboard.hasFiles && {
    val named = clipboard.getFiles.asScala.toList.flatMap(f => List(f.getAbsolutePath, f.toURI.toString,
      f.toPath.toUri.toString))
    val lines = words.split("[\r\n]+").map(_.trim).filter(_.nonEmpty)
    lines.forall(named.contains)
  }
}

package everyui.monaco

import everyui.component.Embed
import everyui.focus.Focus
import everyui.kit.CodeView
import everyui.render.Platform
import everyui.theme.Theme
import fabric.*

import scala.util.{Failure, Success}

/** Monaco's diff editor in a box the app lays out: the original against the modified, side by side or inline,
  * driven by `controller`. Made, sized, focused and let go of as MonacoEditor is; where no web engine runs it, the
  * modified text shows read-only. */
class MonacoDiffEditor(val controller: MonacoDiffController = MonacoDiffController(), owned: Boolean = false)
  extends Embed("monaco", Monaco.site, new CodeView(controller.modified())) {
  import Monaco.uiThread

  private var bridge: Option[MonacoBridge] = None
  private var diffId = ""
  private var making = false
  private var disposed = false

  if (live) {
    backgroundColor := Theme.surface()
    guestFocus = on => if (controller.isAttached) { if (on) controller.focus() else controller.blur() }
    follow(controller.onHandBack, (key: (String, Boolean)) => handBack(key._1, shift = key._2))
    onSize((width, height) => if (diffId.nonEmpty) layout(width, height) else make())
  }

  def made: Boolean = diffId.nonEmpty

  private def make(): Unit = if (!making && !disposed && diffId.isEmpty) {
    if (!Focus.showing(this)) Platform.after(0.05, () => make())
    else {
      making = true
      val to = Monaco.bridge(this)
      to.attach(this)
      bridge = Some(to)
      to.call("diff.create", Wire.merge(controller.createArgs, obj("containerId" -> str(id),
        "width" -> num(size.width()), "height" -> num(size.height())))).onComplete {
        case Success(created) =>
          making = false
          diffId = created.asString
          if (disposed) to.call("diff.dispose", obj("diffId" -> str(diffId)))
          else {
            controller.attach(to, diffId)
            layout(size.width(), size.height())
          }
        case Failure(t) =>
          making = false
          Platform.log("[monaco] the diff editor could not be made: " + t.getMessage)
      }
    }
  }

  private def layout(width: Double, height: Double): Unit =
    bridge.foreach(_.call("diff.layout", obj("diffId" -> str(diffId), "width" -> num(width), "height" -> num(height))))

  override def dispose(): Unit = {
    if (!disposed) {
      disposed = true
      if (owned) controller.dispose()
      else if (diffId.nonEmpty) {
        controller.detach()
        bridge.foreach(_.call("diff.dispose", obj("diffId" -> str(diffId))))
      }
      bridge.foreach(_.detach(this))
    }
    super.dispose()
  }

  override def kind: String = "MonacoDiffEditor"
}

object MonacoDiffEditor {
  /** A diff editor with a controller of its own, disposed with it. */
  def apply(original: String = "", modified: String = "", language: String = "plaintext", theme: String = "vs-dark",
            renderSideBySide: Boolean = true, readOnly: Boolean = false, ignoreTrimWhitespace: Boolean = false): MonacoDiffEditor =
    new MonacoDiffEditor(MonacoDiffController(original, modified, language, theme, renderSideBySide, readOnly,
      ignoreTrimWhitespace), owned = true)
}

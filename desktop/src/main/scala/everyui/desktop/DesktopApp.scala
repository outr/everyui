package everyui.desktop

import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import everyui.render.Host

import java.io.File

/** The desktop entry point: a JavaFX window whose scene root is the render root, then the shared `main`.
  *
  * {{{
  *   --size=1920x1080       the window's content size (default 1920x1080)
  *   --freeze=0.434         step the animation that far on a virtual clock and stop (tools/check.sh)
  *   --screenshot=out.png   with --freeze: save the frozen frame, then exit
  *   --assets=dir           where images/ and fonts/ are (default: assets)
  * }}}
  */
object DesktopApp:
  def main(args: Array[String]): Unit =
    def arg(name: String): Option[String] =
      args.collectFirst { case a if a.startsWith("--" + name + "=") => a.drop(name.length + 3) }

    val (width, height) = arg("size").map(_.split('x')).collect { case Array(w, h) => (w.toDouble, h.toDouble) }
      .getOrElse((1920.0, 1080.0))
    Host.freeze = arg("freeze").flatMap(_.toDoubleOption).getOrElse(-1.0)
    Host.screenshot = arg("screenshot").getOrElse("")
    Host.assets = new File(arg("assets").getOrElse("assets"))
    // every --name=value, for Platform.launchArg (`--demo=durable --socket=ws://…`)
    Host.args = args.collect { case a if a.startsWith("--") && a.contains('=') =>
      val eq = a.indexOf('='); a.substring(2, eq) -> a.substring(eq + 1) }.toMap

    javafx.application.Platform.startup(() => {
      val root = new Pane()
      // The render root is a viewport: content past its edges is clipped, as on every other backend.
      val clip = new Rectangle()
      clip.widthProperty.bind(root.widthProperty)
      clip.heightProperty.bind(root.heightProperty)
      root.setClip(clip)
      val scene = new Scene(root, width, height, Color.WHITE)
      Host.root = root
      Host.scene = scene

      val stage = new Stage()
      stage.setTitle("EveryUI")
      // the window / dock icon: the mark, from the shared assets (tools/make-icons.py)
      val icon = new File(Host.assets, "images/everyui-mark.png")
      if icon.exists then stage.getIcons.add(new javafx.scene.image.Image(icon.toURI.toString))
      stage.setScene(scene)
      stage.show()

      everyui.app.main()
    })

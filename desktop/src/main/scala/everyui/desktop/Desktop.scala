package everyui.desktop

import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import everyui.app.Presence
import everyui.render.{Host, WindowLeave}

import java.io.File

/** The desktop launcher an app's `main` calls: a JavaFX window whose scene root is the render root, then `run`.
  *
  * {{{
  *   --size=1920x1080       the window's content size (default 1920x1080)
  *   --freeze=0.434         step the animation that far on a virtual clock and stop (tools/check.sh)
  *   --screenshot=out.png   with --freeze: save the frozen frame, then exit
  *   --assets=dir           where images/ and fonts/ are (default: assets)
  *   --path=/somewhere      the path to start at, as a page's address would be
  * }}}
  *
  * Every other `--name=value` reaches the app as `Platform.launchArg(name)`. `icon` is an image under the assets. */
object Desktop {
  def launch(args: Array[String], title: String, icon: String)(run: () => Unit): Unit = {
    def arg(name: String): Option[String] =
      args.collectFirst { case a if a.startsWith("--" + name + "=") => a.drop(name.length + 3) }

    val (width, height) = arg("size").map(_.split('x')).collect { case Array(w, h) => (w.toDouble, h.toDouble) }
      .getOrElse((1920.0, 1080.0))
    Host.app = title
    Host.freeze = arg("freeze").flatMap(_.toDoubleOption).getOrElse(-1.0)
    Host.screenshot = arg("screenshot").getOrElse("")
    Host.assets = new File(arg("assets").getOrElse("assets"))
    Host.path = arg("path").getOrElse("")
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
      stage.setTitle(title)
      val iconFile = new File(Host.assets, icon)
      if (iconFile.exists) stage.getIcons.add(new javafx.scene.image.Image(iconFile.toURI.toString))
      stage.setScene(scene)
      WindowLeave.watch(stage)
      // a window minimised is one nobody is watching, as a page hidden is, and restored is somebody back; another
      // window in front of it, or its focus lost, is neither
      val presence = new Presence(() => everyui.render.Platform.away(), () => everyui.render.Platform.resumed())
      stage.iconifiedProperty.addListener((_, _, iconified) =>
        if (iconified.booleanValue) presence.hidden() else presence.shown())
      stage.show()

      run()
    })
  }
}

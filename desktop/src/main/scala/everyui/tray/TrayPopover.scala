package everyui.tray

import everyui.component.Component
import javafx.scene.Scene
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.Pane
import javafx.stage.{Screen, Stage, StageStyle}

/** What a tray icon's left click opens: a window of its own, with no frame, holding a component the app makes afresh
  * each time, beside the point the panel names (above it where that is in the screen's lower half, as a panel at the
  * foot puts it; to its left where it is in the right half), and kept on that screen. It takes the focus, and shuts as
  * it loses it, with Escape, or with another click on the icon; what it held is taken apart then. Keys in it reach the
  * app as the main window's do. */
class TrayPopover {
  private var stage: Option[Stage] = None
  private var held: Option[Component] = None
  // the click that opens it can come just as a focus lost shuts it (the panel took the focus): one within a moment of
  // shutting does not open it again at once
  private var shutAt = 0L

  def showing: Boolean = stage.exists(_.isShowing)
  private[tray] def scene: Option[Scene] = stage.filter(_.isShowing).map(_.getScene)

  def toggle(make: () => Component, x: Int, y: Int): Unit =
    if (showing) close() else if (System.currentTimeMillis() - shutAt > 300L) open(make(), x, y)

  private def open(c: Component, x: Int, y: Int): Unit = {
    val root = new Pane()
    c.position.left @= 0.0
    c.position.top @= 0.0
    root.getChildren.add(c.node.fx)
    val scene = new Scene(root, math.max(1.0, c.size.width()), math.max(1.0, c.size.height()),
      javafx.scene.paint.Color.web(everyui.theme.Theme.surface().toHex))
    val s = new Stage(StageStyle.UNDECORATED)
    s.setAlwaysOnTop(true)
    s.setTitle(everyui.render.Host.app)
    s.setScene(scene)
    everyui.render.Platform.hearKeysIn(scene)
    scene.addEventFilter(KeyEvent.KEY_PRESSED, (e: KeyEvent) => if (e.getCode == KeyCode.ESCAPE) {
      e.consume()
      close()
    })
    def place(): Unit = {
      val w = math.max(1.0, c.size.width())
      val h = math.max(1.0, c.size.height())
      s.setWidth(w)
      s.setHeight(h)
      val all = Screen.getScreens
      val shown = (0 until all.size).toList.map(all.get)
      val boxes = shown.map(sc => TrayScreen(sc.getBounds.getMinX, sc.getBounds.getMinY, sc.getBounds.getWidth,
        sc.getBounds.getHeight, sc.getOutputScaleX))
      val (at, px, py) = TrayScreen.locate(x, y, boxes, TrayScreen.plasmaWayland)
      val area = shown(at).getVisualBounds
      val left = if (px > area.getMinX + area.getWidth / 2.0) px - w else px
      val top = if (py > area.getMinY + area.getHeight / 2.0) py - h - 8.0 else py + 8.0
      s.setX(math.max(area.getMinX, math.min(area.getMaxX - w, left)))
      s.setY(math.max(area.getMinY, math.min(area.getMaxY - h, top)))
    }
    c.size.width.attach(_ => if (s.isShowing) place())
    c.size.height.attach(_ => if (s.isShowing) place())
    s.focusedProperty.addListener((_, was, now) => if (was.booleanValue && !now.booleanValue) close())
    stage = Some(s)
    held = Some(c)
    place()
    s.show()
    s.requestFocus()
  }

  /** Shut, and what it held taken apart. */
  def close(): Unit = {
    stage.foreach { s =>
      stage = None
      shutAt = System.currentTimeMillis()
      s.hide()
    }
    held.foreach(_.dispose())
    held = None
  }
}

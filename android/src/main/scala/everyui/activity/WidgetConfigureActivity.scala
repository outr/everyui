package everyui.activity

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import everyui.render.WidgetAccess
import everyui.widget.WidgetConfigure

/** A home-screen widget's configure screen (`everyui.widget.WidgetConfigure`), which the launcher opens as the widget
  * is placed and, from Android 12, from a long press on it; the app opens it with `Widgets.configure`. The manifest
  * runs it in a process of its own (":widgets"), so it builds its own screen whether or not the app is running in
  * its process; what it saves is a file the receiver reads (`WidgetAccess.save`).
  *
  * It answers the launcher as Android asks: cancelled until Done, which keeps the choice, draws the widget and answers
  * with its id; Cancel as a widget is placed takes it off the home screen again. */
class WidgetConfigureActivity extends EveryUIActivity {
  override protected def run(): Unit = {
    val id = getIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    setResult(Activity.RESULT_CANCELED, answer(id))
    WidgetAccess.start(this)
    // in the colours the widgets are drawn in, light or dark as the device is
    everyui.theme.Theme.follow(everyui.widget.Widgets.light, everyui.widget.Widgets.dark)
    WidgetAccess.kindOf(this, id) match {
      case Some(kind) => WidgetConfigure.show(kind, WidgetAccess.setup(this, id), {
        case Some(setup) =>
          WidgetAccess.save(this, id, setup)
          setResult(Activity.RESULT_OK, answer(id))
          WidgetAccess.redraw(this, id)
          finish()
        case None => finish()
      })
      case None =>
        everyui.render.Platform.log("widget " + id.toString + ": no kind to configure")
        finish()
    }
  }

  private def answer(id: Int): Intent = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
}

package everyui.render

import android.appwidget.{AppWidgetManager, AppWidgetProvider}
import android.content.{ComponentName, Context, Intent}
import android.os.Bundle

/** Where the launcher asks for the app's home-screen widgets (`everyui.widget.Widgets`): to draw them as they are
  * placed, every half hour, as the app asks (`Widgets.refresh`) and as one is resized; to run a tap's action; and to
  * forget one removed. The app's manifest declares it with its `appwidget-provider` and the meta-data
  * "everyui.widget.kind", the key of the kind it draws (android/AndroidManifest.xml has the example app's).
  *
  * It runs with no activity, often in a process started for it alone, so it has only its context: what it draws is
  * the app's `WidgetApp`'s (`WidgetAccess`). Each request holds the broadcast open (`goAsync`) until the content has
  * answered or the deadline has passed. */
class WidgetReceiver extends AppWidgetProvider {
  override def onReceive(context: Context, intent: Intent): Unit = {
    WidgetAccess.start(context)
    if (intent != null && intent.getAction == WidgetAccess.actionIntent) {
      val pending = goAsync()
      WidgetAccess.act(context, intent, () => pending.finish())
    }
    else super.onReceive(context, intent)
  }

  override def onUpdate(context: Context, manager: AppWidgetManager, ids: Array[Int]): Unit = {
    val pending = goAsync()
    WidgetAccess.update(context, new ComponentName(context, getClass), ids, () => pending.finish())
  }

  override def onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int,
                                         options: Bundle): Unit = {
    val pending = goAsync()
    WidgetAccess.update(context, new ComponentName(context, getClass), Array(id), () => pending.finish())
  }

  override def onDeleted(context: Context, ids: Array[Int]): Unit = ids.foreach(id => WidgetAccess.forget(context, id))
}

package everyui.activity

import android.app.Activity
import android.os.Bundle
import everyui.render.{Platform, WidgetAccess}

/** Where a tap on a home-screen widget's list's row goes (`everyui.widget.WidgetTap` in a `WidgetNode.Items`): Android
  * gives a list's rows one PendingIntent between them, its template, which each part fills in with its own tap, and
  * this is it. It shows nothing and is gone at once (the manifest's Theme.NoDisplay, in a task of its own, kept out of
  * the recent apps): an open tap opens the app at its path, as the launcher started this in the foreground, and an
  * action goes to the widget's receiver, which runs it and draws the widget again with the app left where it was
  * (`WidgetAccess.relay`). sbt-everyui declares it with the widgets' receivers. */
class WidgetTapActivity extends Activity {
  // overridePendingTransition, whose successor is Android 14's
  @annotation.nowarn("cat=deprecation")
  override protected def onCreate(saved: Bundle): Unit = {
    super.onCreate(saved)
    try WidgetAccess.relay(this, getIntent)
    catch { case t: Throwable => Platform.logError("widget: a row's tap", t) }
    // before it is resumed, as a window that shows nothing must be
    finish()
    overridePendingTransition(0, 0)
  }
}

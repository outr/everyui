package everyui.render

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}

/** Android's word that the device's time zone changed (ACTION_TIMEZONE_CHANGED), heard from the app's start: the zone
  * read again at once and everyui.date.LocalClock's spans forgotten, before `TimeZones` tells the app. */
object ZoneWatch {
  private var started = false

  /** The first moment after `millis` that `zone`'s offset changes, from java.time's rules (Android 8 on, kept out of
    * Platform so its class needs nothing newer); Long.MaxValue where it never does, `millis` where this cannot say. */
  def changeAfter(zone: String, millis: Long): Long =
    if (android.os.Build.VERSION.SDK_INT >= 26) {
      try {
        val next = java.time.ZoneId.of(zone).getRules.nextTransition(java.time.Instant.ofEpochMilli(millis))
        if (next == null) Long.MaxValue else next.toEpochSecond * 1000L
      }
      catch { case _: RuntimeException => millis }
    }
    else millis

  def start(context: Context): Unit = if (!started) {
    started = true
    val receiver = new BroadcastReceiver {
      override def onReceive(c: Context, intent: Intent): Unit = {
        Platform.zoneChanged()
        everyui.date.LocalClock.reset()
        everyui.date.TimeZones.check()
      }
    }
    val app = context.getApplicationContext
    val filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
    if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    else app.registerReceiver(receiver, filter)
  }
}

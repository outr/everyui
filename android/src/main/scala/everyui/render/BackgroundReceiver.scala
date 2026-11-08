package everyui.render

import android.content.{BroadcastReceiver, Context, Intent}

/** Where Android tells the app's `BackgroundApp` of what happened with the app closed: the device started
  * (BOOT_COMPLETED), the app updated in place (MY_PACKAGE_REPLACED), and an ongoing notice's action tapped or the
  * notice swiped away (EveryUI's own explicit intents). sbt-everyui declares it where the build names a
  * `BackgroundApp` (`everyuiBackground`). Each holds the broadcast open (`goAsync`) until the handler has answered or
  * its deadline has passed (`BackgroundAccess.receiverDeadline`). */
class BackgroundReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent): Unit = {
    val pending = goAsync()
    BackgroundAccess.received(context, intent, () => pending.finish())
  }
}

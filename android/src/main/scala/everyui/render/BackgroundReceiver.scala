package everyui.render

import android.content.{BroadcastReceiver, Context, Intent}

/** Where Android tells the app's `BackgroundApp` of what happened with the app closed: the device started
  * (BOOT_COMPLETED), the app updated in place (MY_PACKAGE_REPLACED), an ongoing notice's action tapped or the notice
  * swiped away, and a position for the visits (EveryUI's own explicit intents: `LocationAccess.passiveIntent`).
  * sbt-everyui declares it where the build names a `BackgroundApp` (`everyuiBackground`). Each holds the broadcast
  * open (`goAsync`) until the handler has answered or its deadline has passed (`BackgroundAccess.receiverDeadline`).
  * A push's copy button copies here, and runs nothing of the app's unless it is a background button too. */
class BackgroundReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent): Unit = {
    val action = Option(intent).filter(_.getAction == PushAccess.actionIntent).map(PushAccess.actionOf)
    if (!action.exists(a => PushAccess.copied(context, intent, a) && !a.background)) {
      val pending = goAsync()
      BackgroundAccess.received(context, intent, () => pending.finish())
    }
  }
}

package everyui.terminal.demo

import everyui.activity.EveryUIActivity

/** The demo on Android: `sbt terminalAndroid/packageApk`, xterm.js in a web view served from the APK. */
class TerminalDemoActivity extends EveryUIActivity {
  override protected def run(): Unit = TerminalDemo.run()
}

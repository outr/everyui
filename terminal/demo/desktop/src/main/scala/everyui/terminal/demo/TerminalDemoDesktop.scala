package everyui.terminal.demo

import everyui.desktop.Desktop
import everyui.render.{Host, Platform}

/** The demo in a desktop window, xterm.js in JavaFX's web view: `sbt terminalDesktop/run`. `--shot=out.png` saves the
  * window `--after` seconds (6 by default) after it opens, and exits. */
object TerminalDemoDesktop {
  def main(args: Array[String]): Unit =
    Desktop.launch(args :+ "--size=1280x800", "everyui-terminal", "images/everyui-mark.png") { () =>
      TerminalDemo.run()
      Host.args.get("shot").foreach { file =>
        Platform.after(Host.args.get("after").flatMap(_.toDoubleOption).getOrElse(6.0), () => {
          Host.screenshot = file
          Host.frozen()
        })
      }
    }
}

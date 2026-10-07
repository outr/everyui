package everyui.app

import everyui.app.screen.ScreenManager

/** A form whose content is put together once it is built, as NaboTV's last setup step is (DoneStep): a container's
  * children set, in one statement, to a spacer, a summary of rows (icons, words, a logo held in a box sized to it) in
  * the middle and a spacer, each spacer as tall as the summary leaves room for. Plain, the screen shows ("[ui] unwind:
  * shown"); with `throw=deep` a reaction to how far the children reach, heard as they are attached, throws, and the
  * failure is told with its backtrace ("[error] unwind: thrown at depth" and the frames under it) by the handler the
  * turn began in, the screen never showing (`UnwindScreen`). */
object UnwindDemo extends ScreenManager {
  lazy val screen: UnwindScreen = new UnwindScreen

  def run(): Unit = run(screen)
}

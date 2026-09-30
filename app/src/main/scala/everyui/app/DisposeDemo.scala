package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform

/** A screen made for one showing is taken apart when it is left, and nothing keeps it: home, then a theatre guide (a
  * dozen theatres, each with a tile following the clock and a rail of nine showtimes, the focus on the first), then a
  * calendar shown over home, which leaves the guide behind nothing and so disposes of it. It prints each step:
  *
  *   [ui] dispose: guide shown
  *   [ui] dispose: guide left
  *   [ui] dispose: settled
  *
  * (`quiz=0` stops once the guide is left, `theatres=<n>` sizes the guide)
  * after which nothing that made up the guide is reachable (`DisposeSpec` checks it on the desktop; on a Roku, the
  * guide's labels are gone from `sgnodes all`). */
object DisposeDemo extends ScreenManager {
  lazy val home: DisposePlain = new DisposePlain(this, "Home", "/")
  lazy val calendar: DisposePlain = new DisposePlain(this, "Calendar", "/calendar")
  lazy val quiz: DisposeQuiz = new DisposeQuiz(this)

  // how many theatres the guide lists: `theatres=<n>` on the launch, else a dozen
  private def theatres: Int = Platform.launchArg("theatres").toIntOption.getOrElse(DisposeGuide.Theatres)

  def run(): Unit = {
    run(home, calendar)
    keep(quiz)
    Platform.after(2.0, () => show(new DisposeGuide(this, theatres)).map { _ =>
      Platform.log("dispose: guide shown")
      Platform.after(2.0, () => showOver(calendar, home).map { _ =>
        Platform.log("dispose: guide left")
        // taken apart a second after it is out of sight, and collected two after that
        Platform.after(4.0, () => {
          Platform.log("dispose: settled")
          if (Platform.launchArg("quiz") != "0") quizzes()
        })
      }.startUnit())
    }.startUnit())
  }

  // the quiz shown, and ten questions asked, a board each, a second apart
  private def quizzes(): Unit = showOver(quiz, home).map { _ =>
    ask(1)
  }.startUnit()

  private def ask(n: Int): Unit = if (n <= 10) {
    quiz.ask(n)
    Platform.after(0.8, () => ask(n + 1))
  }
  else Platform.after(4.0, () => Platform.log("dispose: quiz done"))
}

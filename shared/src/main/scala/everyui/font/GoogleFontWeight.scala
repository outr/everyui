package everyui.font

import everyui.async.Task
import everyui.component.types.FontWeight
import everyui.render.Platform

case class GoogleFontWeight(font: GoogleFont, name: String) {
  /** youi fetches the face from Google Fonts; here it is bundled, so this waits for it to be usable. */
  def load(): Task[GoogleFontWeight] =
    Task.async[GoogleFontWeight](done => Platform.loadFont(font.family, FontWeight.fromName(name).value, () => done(this)))
}

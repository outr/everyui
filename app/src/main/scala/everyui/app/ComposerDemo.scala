package everyui.app

import everyui.async.Task
import everyui.chat.{Attachment, Completion, CompletionTrigger, Composer, ComposerPaste, Draft}
import everyui.component.{Button, ButtonStyle, Column, IconButton, TextView}
import everyui.component.types.WhiteSpace
import everyui.font.MaterialIcon
import everyui.keymap.Keymap
import everyui.kit.Toast
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A chat's composer with nothing behind it: each message sent is logged (`[ui] composer sent …`), a "reply" takes
  * three seconds (Stop ends it at once: `[ui] composer stopped`), and each reply leaves a suggestion for the next
  * message. Pictures and text files can be attached (5 MB at most), '@' mentions one of four people, and '/' at the
  * start offers commands. A model to choose sits before the field and a microphone after it. A paste longer than 1500
  * characters (a log, a stack trace) goes with the message as a text file named after its first line rather than into
  * the field (`[ui] composer pasted <n> chars as a file <name>`); `paste=upper` pastes a shorter one in capitals, and
  * `paste=ignore` lets none land. `theme=light` shows the light palette, `width=` sets the composer's width (a phone's
  * is about 400), and `disabled=1` starts it out of use. */
class ComposerDemo {
  private val replies: Var[Int] = Var(0)
  private val model: Var[Int] = Var(0)
  private val log = Column(6.0, padding = 16.0)()
  val composer: Composer = new Composer(draft => sent(draft), onStop = () => stopped(),
    onAcceptSuggestion = s => Platform.log("composer accepted " + s),
    complete = Some((trigger, prefix) => Task(ComposerDemo.completions(trigger, prefix))),
    triggers = List(CompletionTrigger('@'), CompletionTrigger('/', atStart = true)),
    accept = List("image/*", "text/*", ".md", ".txt", ".json"), maxSize = Some(5L * 1024L * 1024L))

  private val asked = Platform.launchArg("width").toDoubleOption
  composer.size.width := asked.getOrElse(math.min(760.0, ui.size.width() - 32.0))
  composer.position.left := (ui.size.width() - composer.size.width()) / 2.0
  composer.position.top := ui.size.height() - composer.size.height() - 16.0
  composer.placeholder @= "Message the team"
  private val choose = Button.bound("Model: " + ComposerDemo.models(model()), () => model @= (model() + 1) % ComposerDemo.models.length,
    ButtonStyle.Text, Some(MaterialIcon.AutoAwesome), 12.0)
  composer.leading.children += choose
  composer.trailing.children += new IconButton(MaterialIcon.Mic, "Dictate", () => Toast.show("Dictation is the app's"))
  composer.commands(Keymap.global)
  composer.pasted @= Some(text => pasted(text))

  log.children += TextView("Composer", 20.0)
  log.children += TextView.bound(if (composer.busy()) "Replying… (Stop ends it)" else "Replies: " + replies(), 13.0,
    Theme.muted())
  ui.children ++= List(log, composer)
  composer.focusField()
  composer.suggestion @= Some("Hello! What can you help me with today?")
  if (Platform.launchArg("disabled") == "1") composer.enabled @= false

  private def sent(draft: Draft): Unit = {
    val files = draft.attachments.map(a => a.name + " (" + a.mime + ", " + a.size.toString + " B)").mkString(", ")
    Platform.log("composer sent text=" + draft.text.replace("\n", "\\n") + " files=" + draft.attachments.length.toString +
      (if (files.isEmpty) "" else " " + files))
    log.children += line(draft.text + (if (files.isEmpty) "" else "  [" + files + "]"))
    composer.suggestion @= None
    composer.busy @= true
    val mine = replies() + 1
    Platform.after(3.0, () => if (composer.busy() && replies() == mine - 1) reply(mine))
  }

  // a long paste a file, as `ComposerPaste.longAsFile(1500)` makes it; a short one as `paste=` says
  private def pasted(text: String): ComposerPaste = {
    val mode = Platform.launchArg("paste")
    val long = text.length > 1500
    val made = if (long) ComposerPaste.Attach(Attachment.pasted(text))
      else if (mode == "upper") ComposerPaste.InsertText(text.toUpperCase)
      else if (mode == "ignore") ComposerPaste.Ignore
      else ComposerPaste.Insert
    Platform.log("composer pasted " + text.length.toString + " chars as " +
      (if (long) "a file " + Attachment.textName(text) else if (mode == "upper" || mode == "ignore") mode else "words"))
    made
  }

  private def stopped(): Unit = {
    Platform.log("composer stopped")
    composer.busy @= false
    replies @= replies() + 1
    log.children += line("(stopped)")
  }

  private def reply(n: Int): Unit = {
    composer.busy @= false
    replies @= n
    log.children += line("Reply " + n.toString)
    composer.suggestion @= Some(ComposerDemo.guesses((n - 1) % ComposerDemo.guesses.length))
  }

  private def line(words: String): TextView = {
    val t = new TextView
    t.whiteSpace @= WhiteSpace.Normal
    t.size.width := math.min(720.0, ui.size.width() - 32.0)
    t.font.size @= 14.0
    t.color := Theme.text()
    t.content @= words
    t
  }
}

object ComposerDemo {
  private val people = Vector(("Ada Lovelace", "ada"), ("Grace Hopper", "grace"), ("Linus Torvalds", "linus"),
    ("Margaret Hamilton", "margaret"))
  private val commands = Vector(("/help", "What can be asked"), ("/clear", "Start again"),
    ("/summarize", "The conversation so far, in short"), ("/translate", "The last reply in another language"))
  private val guesses = Vector("Can you give me an example?", "What would you change first?",
    "Summarize that in three bullet points.")
  private val models = Vector("Fast", "Careful", "Local")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    new ComposerDemo
  }

  /** The people whose name or handle has `prefix` in it after '@', or the commands starting with it after '/'. */
  def completions(trigger: Char, prefix: String): Vector[Completion] = {
    val p = prefix.toLowerCase
    if (trigger == '@') people.filter(x => x._1.toLowerCase.contains(p) || x._2.startsWith(p))
      .map(x => Completion(x._1, "@" + x._2, Some("@" + x._2)))
    else commands.filter(_._1.startsWith("/" + p)).map(c => Completion(c._1, c._1, Some(c._2)))
  }
}

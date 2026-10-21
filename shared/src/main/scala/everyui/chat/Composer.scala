package everyui.chat

import everyui.async.Task
import everyui.component.*
import everyui.component.types.{PasteAction, Semantics, WhiteSpace}
import everyui.event.{Key, KeyEvent}
import everyui.file.{Files, PickedFile}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.keymap.{Chord, Command, Keymap}
import everyui.kit.Toast
import everyui.layout.{FlowLayout, HorizontalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, ui}
import reactify.*

/** Where a chat's next message is written: a box of lines that grows with them up to `maxHeight` and then scrolls,
  * the files going with it as chips, the app's own controls around it (`leading`, `trailing`), and Send. It knows
  * nothing of where a message goes: `onSend` hears each [[Draft]], and the field is empty again.
  *
  *  - '''Keys''' ([[ComposerKeys]]): Enter sends and Shift+Enter starts a new line; where the keyboard is on the screen
  *    (`touch`) Enter starts a new line and the Send button sends. An input method's Enter chooses its words and never
  *    sends. While a reply is coming (`busy`) the field can still be written in, Enter does nothing (so nothing half
  *    written goes out after a reply the person has not read), and Send is Stop, heard by `onStop`.
  *  - '''Queued messages''' (`queueWhileBusy`, off by default): while a reply comes, Enter (or the Send beside Stop)
  *    puts the draft, words and files, at the end of `queue`, and the field is empty for the next. The queue shows
  *    over the field ([[DraftQueue]]), a chip for each in the order they go: the first line of its words and how many
  *    files, a handle to drag it into another place (or Alt+Up and Alt+Down), Edit (Enter or E) and Remove (Delete or
  *    Backspace). Edit takes it back into the field; what the field held goes into its place in the queue rather than
  *    being lost, and the edited message queued again goes back where it was. Up at the start of the field goes to the
  *    queue and Down from its last message, or Escape, comes back. A screen reader hears how many wait as it changes.
  *    Once `busy` falls and stays down for `queueSettle` (a fall that rises again sooner is a flicker), the composer
  *    sends the queue's next message through `onSend`, one at a time: from each `onSend` it waits for `busy` to rise
  *    and then fall again before the next, or, if it does not rise within `queueStartWait`, takes it that the message
  *    started no reply. A message sent at rest counts the same, so what is written while its reply starts is queued
  *    behind it. Stop pauses the queue (`queueAfterStop`): it waits, saying so with Resume, until it is resumed or
  *    another message is sent, which goes first. Paused, its line also has Send next (the next message only, the queue
  *    still paused), Clear queue (at once, unasked: what it takes off is in sight) and the app's `queueActions`; where
  *    they do not fit beside its words, Resume stays and the rest go, in order, into a ⋮ menu.
  *  - '''A suggestion''' (`suggestion`, the app's guess at the next message) shows in the accent colour in the empty
  *    field, in the placeholder's place. Tab or Right takes it (`onAcceptSuggestion` hears it and it is written in,
  *    the caret after it); typing puts it away while there is anything in the field, and it is back once the field is
  *    emptied again; Escape, taking it, or sending what was written puts it away until the next suggestion. Where
  *    there is no Tab or Right (a touch screen, a remote) it is a chip above the field instead, taken with a tap or OK.
  *    A screen reader is told a suggestion has come, and how to take it.
  *  - '''Files''': the attach button opens the platform's picker (`Files.pick`) and files dropped on the composer
  *    come too, each a chip to take off again. What `accept` names (the picker's types: "image/\*", ".txt"), `allow`
  *    agrees to and `maxSize` fits is added; anything else is refused with a word saying why. A picture's preview
  *    is let go (`Files.release`) once its file leaves the draft, taken off or sent: an app that shows a sent picture
  *    reads its `bytes`. A platform with no picker (a Roku, an Apple TV, a television) shows no button.
  *  - '''Pasting''' (`pasted`): the app decides what a paste becomes before it lands, words or a file: a paste of a
  *    log or a whole file can go with the message as a chip named after its first line (`ComposerPaste.longAsFile`),
  *    rather than flooding it. What `accept`, `allow` and `maxSize` refuse is refused as a dropped file is.
  *  - '''Completions''' (`complete`): typing one of `triggers` ('@' for a mention, '/' for a command at the start)
  *    asks the app for completions of what follows it, listed over the composer. Up and Down move through them,
  *    Enter or Tab puts the lit one in place of the word, and Escape closes them. None on a remote.
  *  - '''Out of use''' (`enabled` false: no connection, say): the field keeps its words but takes no typing, and Send,
  *    the attach button and the app's controls are dimmed and do nothing, a screen reader hearing that they are
  *    disabled. The focus still reaches the field, and Enter does nothing.
  *  - '''Narrow''' (under `compactWidth`, a phone): the controls are smaller, and where the app has controls of its
  *    own at the start (`leading`), they and the attach button fold into a "+" that opens them in a row above the
  *    field. `trailing` stays beside Send, so a microphone is always one tap away.
  *  - '''A remote''' (a Roku, an Apple TV): one line, written on the platform's keyboard, with Send beside it.
  *
  * {{{
  *   val composer = new Composer(draft => chat.send(draft.text, draft.attachments), onStop = () => chat.stop(),
  *     complete = Some((trigger, prefix) => people.find(prefix).map(_.map(p => Completion(p.name, "@" + p.handle)))),
  *     triggers = List(CompletionTrigger('@')), accept = List("image/\*", ".txt"), maxSize = Some(10L * 1024 * 1024))
  *   composer.size.width := ui.size.width() - 32.0
  *   composer.busy := chat.replying()
  *   composer.suggestion := chat.nextGuess()
  *   composer.trailing.children += micButton
  * }}} */
class Composer(onSend: Draft => Unit,
               onStop: () => Unit = () => (),
               onAcceptSuggestion: String => Unit = _ => (),
               complete: Option[(Char, String) => Task[Vector[Completion]]] = None,
               triggers: List[CompletionTrigger] = Nil,
               accept: List[String] = Nil,
               allow: Attachment => Boolean = _ => true,
               maxSize: Option[Long] = None,
               val words: ComposerWords = ComposerWords(),
               maxHeight: Double = 200.0,
               val touch: Boolean = Composer.touchScreen(),
               width: Double = 640.0) extends Container {
  /** What is written, both ways: the app can set it, or clear it. */
  val text: Var[String] = own(Var(""))
  /** The files going with it, both ways. */
  val attachments: Var[List[Attachment]] = own(Var(Nil))
  /** Whether a reply is coming: Send is Stop, and Enter sends nothing. */
  val busy: Var[Boolean] = own(Var(false))
  /** Whether it is in use at all. */
  val enabled: Var[Boolean] = own(Var(true))
  /** What the empty field says. */
  val placeholder: Var[String] = own(Var("Message"))
  /** The app's guess at the next message, shown in the empty field (or as a chip) until it is taken or put away. */
  val suggestion: Var[Option[String]] = own(Var(None))
  /** The app's own controls before the field (a model to choose), and after it, before Send (a microphone). */
  val leading: Container = Composer.slot()
  val trailing: Container = Composer.slot()
  /** Narrower than this, it is a phone's: smaller controls, and `leading` folded behind "+". */
  val compactWidth: Var[Double] = own(Var(480.0))
  /** What a paste into the field becomes, asked with the words on the clipboard before they land (`ComposerPaste`): they
    * land, other words land in their place, nothing does, or they go with the message as a file, a chip as a dropped
    * file is (`longAsFile` for a log or a stack trace). None, the default, lets every paste land. */
  val pasted: Var[Option[String => ComposerPaste]] = own(Var(None))
  /** Whether a message sent while a reply is coming waits in `queue` for its own turn, rather than Enter doing nothing.
    * Off, the default, the composer is as it always was. */
  val queueWhileBusy: Var[Boolean] = own(Var(false))
  /** The messages waiting, the next first: the app may read it, seed it (a reload keeping it) or clear it. */
  val queue: Var[List[Draft]] = own(Var(Nil))
  /** What Stop does to a queue: pauses it (for now the default) or lets it go on. */
  val queueAfterStop: Var[QueueAfterStop] = own(Var(QueueAfterStop.Pause))
  /** Whether the queue is paused, after Stop: it waits until it is resumed (`resume`), or another message is sent. */
  val queuePaused: Var[Boolean] = own(Var(false))
  /** How long `busy` must stay false before the next queued message goes, in seconds: a fall that rises again sooner
    * is a flicker within one reply, not its end. */
  val queueSettle: Var[Double] = own(Var(0.3))
  /** How long after `onSend` the composer waits for `busy` to rise, in seconds, before taking it that the message
    * started no reply at all (a message to a person). */
  val queueStartWait: Var[Double] = own(Var(3.0))
  /** The most room the queue's messages take before they scroll. */
  val queueMaxHeight: Var[Double] = own(Var(136.0))
  /** The app's own actions on a paused queue ("Stop running commands"), on its line after Resume, Send next and Clear
    * queue, in order; those that do not fit beside its words go into a ⋮ menu at its end. */
  val queueActions: Var[List[QueueAction]] = own(Var(Nil))

  /** Whether the controls are a phone's. */
  val compact: Val[Boolean] = own(Val(size.width() < compactWidth()))

  private val remote = !Platform.nativeInput() || Platform.remoteNavigation()
  private val pickable = !remote && Files.pickable
  private val points = 15.0
  private val gap = 6.0
  private val lineHeight = 40.0
  private val pad: Val[Double] = own(Val(if (compact()) 6.0 else 8.0))
  /** How round the card's corners are: 18 when compact, else 20. ChatView rounds its status bar to match. */
  val cornerRadius: Val[Double] = own(Val(if (compact()) 18.0 else 20.0))

  // the suggestion put away for this turn: by Escape, by taking it, or by sending what was written; typing only hides
  // it while the field has something in it (`suggesting`)
  private val dismissed: Var[Boolean] = own(Var(false))
  private val dropping: Var[Boolean] = own(Var(false))
  private val trayOpen: Var[Boolean] = own(Var(false))

  /** Whether there is something to send: words, or a file. */
  val sendable: Val[Boolean] = own(Val(text().trim.nonEmpty || attachments().nonEmpty))
  /** Whether the suggestion shows now. */
  val suggesting: Val[Boolean] = own(Val(enabled() && text().isEmpty && suggestion().exists(_.nonEmpty) && !dismissed()))

  private val completions: Var[Vector[Completion]] = own(Var(Vector.empty))
  private val lit: Var[Int] = own(Var(0))
  private var word: Option[TriggerWord] = None
  private var asked = 0
  private var popup: Option[CompletionPopup] = None
  // the app's controls made unfocusable while it is out of use, to be given their focus back
  private var parked: List[Component] = Nil
  // the attachments as they were, to see which left
  private var held: List[Attachment] = Nil
  // the queue's messages as they were, to see which left it
  private var waiting: List[Draft] = Nil
  // while a message moves between the field and the queue (an edit), its files are in neither for a moment
  private var shuffling = false
  // a message the composer sent whose reply has not yet been seen to start and end: the next waits for it
  private val awaiting: Var[Boolean] = own(Var(false))
  private var risen = false
  private var settles = 0
  private var waits = 0
  // the queued message the one being edited stood before, so that queued again it goes back into its place
  private var returning: Option[Draft] = None
  // what the queue said to a screen reader, newest first (for the specs: the desktop's announce says nothing)
  private[chat] var announced: List[String] = Nil
  private var queueMade = false

  /** The box of lines. */
  val field: ComposerField = new ComposerField(e => ComposerKeys.action(e, state(), Platform.keyComposing()), act,
    !remote, lineHeight, maxHeight, points)

  private val card = new Container
  private val ghost = new TextView {
    whiteSpace @= WhiteSpace.Normal
    font.size @= points
    color := Theme.accent()
  }
  private val attachButton = new IconButton(MaterialIcon.AttachFile, words.attach, () => pick())
  private val moreButton = new IconButton(MaterialIcon.Add, words.more, () => trayOpen @= !trayOpen())
  private val trayAttach = new Button(words.attach, () => pick(), ButtonStyle.Text, Some(MaterialIcon.AttachFile), 13.0)
  private val sendButton = new SendButton(words.send, words.stop)
  private val start = Row(4.0, middle = true)()
  private val end = Row(4.0, middle = true)()
  private val tray = Row(6.0, middle = true)()
  private val chips = new Container
  private val chip = new Container
  private val chipWords = new TextView {
    font.size @= 13.0
    color := Theme.accent()
  }
  private val shield = new Container
  private val veil = new Container

  /** Whether `leading` is folded behind "+". */
  private val crowded: Val[Boolean] = own(Val(compact() && leading.children().nonEmpty))
  /** Whether the suggestion is a chip rather than words in the field. */
  private val asChip: Boolean = touch || remote

  /** Whether what is sent now goes into the queue: while a reply comes, while one just asked for has not been seen to
    * start and end, and while messages already wait to go (unless the queue is paused). */
  val holding: Val[Boolean] =
    own(Val(queueWhileBusy() && (busy() || awaiting() || (queue().nonEmpty && !queuePaused()))))
  // the queue shows over the field, and the Send beside Stop while a reply comes
  private val queueShown: Val[Boolean] = own(Val(queueWhileBusy() && queue().nonEmpty))
  private val queueing: Val[Boolean] = own(Val(queueWhileBusy() && busy()))
  // whether the next queued message may go: the composer idle, nothing it sent still to start and end, not paused
  private val drainable: Val[Boolean] = own(Val(queueWhileBusy() && enabled() && !busy() && !awaiting() &&
    !queuePaused() && queue().nonEmpty))
  // whether Send next may send the queue's next message now: as a drain would, but paused or not
  private val nextReady: Val[Boolean] = own(Val(queueWhileBusy() && enabled() && !busy() && !awaiting() &&
    queue().nonEmpty))
  private lazy val queueView: DraftQueue = {
    queueMade = true
    val q = new DraftQueue(queue, queuePaused, queueMaxHeight, field, d => edit(d), d => unqueue(d), () => resume(),
      () => sendNext(), nextReady, () => clearQueue(), queueActions)
    q.position.left := pad()
    q.position.top := pad()
    q.size.width := card.size.width() - pad() * 2.0
    q
  }
  private lazy val queueButton: SendButton = {
    val b = new SendButton(ChatWords.addToQueue, words.stop)
    b.usable := enabled() && sendable()
    b.pressed.attach(_ => send())
    b
  }
  private val queueSpace: Val[Double] = own(Val(if (queueShown()) queueView.size.height() + gap else 0.0))

  private def state(): ComposerState = ComposerState(touch, enabled(), busy(), field.editing(), sendable(),
    suggesting() && !asChip, popup.nonEmpty, queueWhileBusy(), queueShown(),
    queueShown() && field.field.exists(_.caret == 0))

  size.width @= width

  // --- the field -------------------------------------------------------------------------------------------------
  field.value.attach(v => if (text() != v) text @= v)
  text.attach { v =>
    if (field.value() != v) field.value @= v
    if (complete.nonEmpty && !remote) Platform.later(() => lookup())
  }
  suggestion.attach(_ => dismissed @= false)
  field.hint := (if (suggesting() && !asChip) "" else placeholder())
  field.disabled := !enabled()
  field.pasted @= Some(words => pasted() match {
    case Some(f) => f(words) match {
      case ComposerPaste.AsText(action) => action
      case ComposerPaste.Attach(a) =>
        if (enabled()) attach(a)
        PasteAction.Ignore
    }
    case None => PasteAction.Insert
  })
  field.field.foreach { f =>
    f.disabled := !enabled()
    f.semantics := Semantics(label = if (suggesting() && !asChip)
      placeholder() + ". " + words.suggestion + ": " + suggestion().getOrElse("") + ". " + words.accept + "." else "")
  }
  suggesting.attach(on => if (on) Platform.announce(words.suggestion + ": " + suggestion().getOrElse("") + ". " +
    (if (asChip) "" else words.accept + ".")))
  field.focused.attach(f => if (!f) closeCompletions())
  // the words it shows behind the field, as tall as they need the field to be
  ghost.content := (if (suggesting() && !asChip) suggestion().getOrElse("") else "")
  ghost.visible := suggesting() && !asChip
  ghost.size.width := field.size.width() - 20.0
  ghost.position.left := field.position.left() + 10.0
  ghost.position.top := field.position.top() + 10.0
  field.size.height := {
    val wanted = math.max(field.lines(), if (suggesting() && !asChip) ghost.size.height() + 20.0 else 0.0)
    if (remote) lineHeight else math.max(lineHeight, math.min(maxHeight, wanted))
  }

  // --- the controls ----------------------------------------------------------------------------------------------
  attachButton.disabled := !enabled()
  moreButton.disabled := !enabled()
  trayAttach.enabled := enabled()
  moreButton.icon.icon := (if (trayOpen()) MaterialIcon.Close else MaterialIcon.Add)
  sendButton.usable := enabled() && (busy() || sendable())
  sendButton.stopping := busy()
  sendButton.pressed.attach(_ => if (busy()) stop() else send())
  start.opacity := (if (enabled()) 1.0 else 0.5)
  end.opacity := (if (enabled()) 1.0 else 0.5)
  tray.opacity := (if (enabled()) 1.0 else 0.5)
  field.opacity := (if (enabled()) 1.0 else 0.6)

  // --- the suggestion as a chip ----------------------------------------------------------------------------------
  private val spark = new MaterialIconView(MaterialIcon.AutoAwesome, 16.0)
  spark.color := Theme.accent()
  spark.position.left @= 10.0
  spark.position.middle := chip.size.middle()
  chipWords.content := suggestion().getOrElse("")
  chipWords.position.left @= 32.0
  chipWords.position.middle := chip.size.middle()
  chipWords.maxWidth := math.max(40.0, size.width() - 48.0)
  chip.focusable @= true
  chip.size.width := chipWords.size.width() + 44.0
  chip.size.height @= 32.0
  chip.radius @= 16.0
  chip.borderWidth := (if (chip.focusShown()) 2.0 else 1.0)
  chip.borderColor := (if (chip.focusShown()) Theme.accent() else Theme.border())
  chip.backgroundColor := (if (chip.focusShown() || chip.event.pointer.over()) Theme.controlLit() else Theme.raised())
  chip.semantics := Semantics.button(words.suggestion + ": " + suggestion().getOrElse(""))
  chip.children ++= List(spark, chipWords)
  chip.tapSlop @= true
  chip.event.click.attach { e =>
    e.consume()
    acceptSuggestion()
  }
  private val chipShown: Val[Boolean] = own(Val(asChip && suggesting()))

  // --- the card: the queue, tray, chips, then the line ---------------------------------------------------------------
  chips.layout @= Some(FlowLayout(6.0, 6.0))
  chips.size.width := card.size.width() - pad() * 2.0
  card.size.width := size.width()
  card.radius := cornerRadius()
  card.backgroundColor := Theme.raised()
  card.borderWidth := (if (dropping() || field.focused()) 2.0 else 1.0)
  card.borderColor := (if (dropping() || field.focused()) Theme.accent() else Theme.border())

  private val trayShown: Val[Boolean] = own(Val(crowded() && trayOpen()))
  private val chipsShown: Val[Boolean] = own(Val(attachments().nonEmpty))
  private val lineTop: Val[Double] = own(Val(pad() + queueSpace() +
    (if (trayShown()) tray.size.height() + gap else 0.0) + (if (chipsShown()) chips.size.height() + gap else 0.0)))
  private val line: Val[Double] = own(Val(math.max(field.size.height(), lineHeight)))

  tray.position.left := pad()
  tray.position.top := pad() + queueSpace()
  chips.position.left := pad()
  chips.position.top := pad() + queueSpace() + (if (trayShown()) tray.size.height() + gap else 0.0)
  start.position.left := pad()
  start.position.top := lineTop() + line() - (lineHeight + start.size.height()) / 2.0
  end.position.left := card.size.width() - pad() - end.size.width()
  end.position.top := lineTop() + line() - (lineHeight + end.size.height()) / 2.0
  field.position.left := pad() + (if (start.children().isEmpty) 4.0 else start.size.width() + 2.0)
  field.position.top := lineTop()
  field.size.width := end.position.left() - field.position.left() - 2.0
  card.size.height := lineTop() + line() + pad()
  card.position.top := (if (chipShown()) chip.size.height() + gap else 0.0)
  size.height := card.position.top() + card.size.height()

  // what covers the card: the shield keeps a pointer off what is out of use, the veil says where files are dropped
  shield.size.width := card.size.width()
  shield.size.height := card.size.height()
  // inside the card's edge, which stays lit while files are over it
  veil.position.left @= 2.0
  veil.position.top @= 2.0
  veil.size.width := card.size.width() - 4.0
  veil.size.height := card.size.height() - 4.0
  veil.radius := card.radius() - 2.0
  veil.backgroundColor := Theme.raised()
  private val tint = new Container
  tint.size.width := veil.size.width()
  tint.size.height := veil.size.height()
  tint.radius := veil.radius()
  tint.backgroundColor := Theme.accent().withAlpha(0.14)
  private val veilIcon = new MaterialIconView(MaterialIcon.FileUpload, 20.0)
  veilIcon.color := Theme.accent()
  private val veilWords = TextView.bound(words.drop, 14.0, Theme.accent())
  private val veilLine = Row(8.0, middle = true)(veilIcon, veilWords)
  veilLine.position.center := veil.size.center()
  veilLine.position.middle := veil.size.middle()
  veil.children ++= List(tint, veilLine)

  card.children ++= List(ghost, field, start, end)
  children += card

  // what is in the card follows what shows: a part out of sight is out of the tree, so the focus never lands on it
  chipShown.attach(on => if (on) children += chip else children -= chip)
  chipsShown.attach(on => if (on) card.children += chips else card.children -= chips)
  trayShown.attach(on => if (on) card.children += tray else card.children -= tray)
  dropping.attach(on => if (on) card.children += veil else card.children -= veil)
  enabled.attach { on =>
    if (on) card.children -= shield else card.children += shield
    inert(leading, !on)
    inert(trailing, !on)
  }
  crowded.attach(_ => arrange())
  trayOpen.attach(_ => arrange())
  leading.children.attach(_ => arrange())
  trailing.children.attach(_ => arrange())
  attachments.attach { list =>
    // what left the draft (taken off, sent, or cleared by the app) lets its picture go, unless it waits in the queue
    if (!shuffling) letGo(held)
    held = list
    val was = chips.children()
    chips.children.clear()
    was.foreach(_.dispose())
    chips.children ++= list.map(a => new AttachmentChip(a, words.remove, () => remove(a)))
  }
  arrange()

  // --- the queue -------------------------------------------------------------------------------------------------
  queueShown.attach { on =>
    // first in the card, so the focus reaches its messages before the field
    if (on) card.children @= queueView +: card.children()
    else if (queueMade) card.children -= queueView
  }
  queueing.attach(_ => arrange())
  queue.attach { q =>
    // a message that left the queue (sent, taken off, or cleared by the app) lets its files' pictures go
    if (!shuffling) letGo(waiting.flatMap(_.attachments))
    val before = waiting.length
    waiting = q
    if (q.isEmpty) queuePaused @= false
    if (queueWhileBusy() && q.length != before) say(if (queuePaused()) ChatWords.queuePaused + ", " +
      ChatWords.queued(q.length) else ChatWords.queued(q.length))
  }
  queuePaused.attach(on =>
    if (on && queueWhileBusy()) say(ChatWords.queuePaused + ", " + ChatWords.queued(queue().length)))
  // the reply to what it sent seen to start, and then to end; a rise also stops a next message about to go
  busy.attach { on =>
    if (on) {
      risen = true
      settles += 1
    }
    else if (awaiting() && risen) awaiting @= false
  }
  drainable.attach(on => if (on) settle())
  // the person clearing the field after an edit: what they write next is a message of its own, for the queue's end
  sendable.attach(on => if (!on) returning = None)

  // --- files dropped on it ---------------------------------------------------------------------------------------
  if (!remote) Files.dropTarget(this)(over => dropping @= over && enabled())(files => if (enabled()) add(files))

  // --- where the arrows go, for a remote and for the parts that are not the field --------------------------------------
  field.exitFocus(Key.ArrowRight, sendButton)
  sendButton.exitFocus(Key.ArrowLeft, field)
  chip.exitFocus(Key.ArrowDown, field)
  attachButton.exitFocus(Key.ArrowRight, field)
  moreButton.exitFocus(Key.ArrowRight, field)

  /** The field given the focus: where the person writes next. */
  def focusField(): Unit = field.focus()

  /** The draft sent, if there is one to send and the composer may: `onSend` hears it, and the field is empty. Where
    * messages are queued (`queueWhileBusy`) and the composer is `holding`, it goes into the queue instead: at its end,
    * or, for a message taken back out of it to edit, into the place it left. */
  def send(): Unit = if (enabled() && sendable() && (!busy() || queueWhileBusy())) {
    val draft = Draft(text(), attachments())
    val queued = holding()
    val back = returning
    returning = None
    closeCompletions()
    // into the queue before the field empties, so its files' pictures are kept for it
    if (queued) enqueue(draft, back)
    if (draft.text.nonEmpty) dismissed @= true
    text @= ""
    attachments @= Nil
    trayOpen @= false
    // back to the field from the Send button, to write the next
    field.focus()
    if (!queued) {
      if (queueWhileBusy()) {
        begin()
        // a message sent while the queue is paused goes first, and the queue goes on after it
        queuePaused @= false
      }
      onSend(draft)
    }
  }

  /** The reply asked to stop, while one is coming. A queue is then paused (`queueAfterStop`), or goes on. */
  def stop(): Unit = if (busy()) {
    if (queueWhileBusy() && queue().nonEmpty && queueAfterStop() == QueueAfterStop.Pause) queuePaused @= true
    onStop()
  }

  /** A paused queue sent on again: its next message goes once the composer is idle. */
  def resume(): Unit = queuePaused @= false

  /** The queue's next message sent now, as it would go once the composer is idle, the rest of the queue left as it was
    * (paused, it stays paused): only while nothing the composer sent is still to start and end, so never two at once. */
  def sendNext(): Unit = if (nextReady()) drain()

  /** Every queued message taken off, never to be sent. */
  def clearQueue(): Unit = if (enabled()) queue @= Nil

  /** A queued message taken back out of the queue into the field, to edit: what the field held, if anything, takes
    * its place in the queue rather than being lost, and sent again while the composer is holding, it goes back where
    * it was. */
  def edit(draft: Draft): Unit = if (enabled()) {
    val q = queue()
    val at = q.indexWhere(_ eq draft)
    if (at >= 0) {
      val current = if (sendable()) Some(Draft(text(), attachments())) else None
      val rest = q.filterNot(_ eq draft)
      closeCompletions()
      shuffling = true
      try {
        queue @= (current match {
          case Some(c) => rest.take(at) ++ (c :: rest.drop(at))
          case None => rest
        })
        text @= draft.text
        attachments @= draft.attachments
      }
      finally shuffling = false
      returning = current.orElse(if (at < rest.length) Some(rest(at)) else None)
      field.focus()
      if (!touch) field.editing @= true
      field.field.foreach(f => Platform.later(() => f.moveCaret(draft.text.length)))
    }
  }

  /** A queued message taken off the queue, never to be sent. */
  def unqueue(draft: Draft): Unit = if (enabled()) queue @= queue().filterNot(_ eq draft)

  // `draft` into the queue: before `before` where that still waits, else at the end
  private def enqueue(draft: Draft, before: Option[Draft]): Unit = {
    val q = queue()
    val at = before.map(b => q.indexWhere(_ eq b)).getOrElse(-1)
    queue @= (if (at < 0) q :+ draft else q.take(at) ++ (draft :: q.drop(at)))
  }

  // a message sent: the next waits until `busy` is seen to rise and fall, or, if it never rises, `queueStartWait`
  private def begin(): Unit = {
    risen = false
    awaiting @= true
    waits += 1
    val mine = waits
    Platform.after(queueStartWait(), () => if (mine == waits && awaiting() && !risen) awaiting @= false)
  }

  // the next queued message sent once the composer has stayed idle for `queueSettle`
  private def settle(): Unit = {
    settles += 1
    val mine = settles
    Platform.after(queueSettle(), () => if (mine == settles && drainable()) drain())
  }

  // the queue's next message sent, starting its own reply: one each time the composer is idle
  private def drain(): Unit = queue().headOption.foreach { next =>
    begin()
    queue @= queue().tail
    onSend(next)
  }

  // the files of `before` in neither the field nor the queue now let their pictures go
  private def letGo(before: List[Attachment]): Unit = {
    val queued = queue()
    before.filterNot(a => attachments().exists(_ eq a) || queued.exists(_.attachments.exists(_ eq a)))
      .foreach(a => a.preview.foreach(p => Files.release(p)))
  }

  // a change to the queue said to a screen reader
  private def say(said: String): Unit = {
    announced = said :: announced
    Platform.announce(said)
  }

  /** The suggestion written into the field, the caret after it. */
  def acceptSuggestion(): Unit = if (suggesting()) suggestion().foreach { s =>
    dismissed @= true
    text @= s
    field.focus()
    // a tap on the chip brings the keyboard up to go on writing
    if (touch) field.editing @= true
    field.field.foreach(f => Platform.later(() => f.moveCaret(s.length)))
    onAcceptSuggestion(s)
  }

  /** The suggestion put away until the next one. */
  def dismissSuggestion(): Unit = dismissed @= true

  /** The platform's picker, for files to go with the message. */
  def pick(): Unit = if (enabled() && pickable) {
    trayOpen @= false
    Files.pick(accept, multiple = true).map(files => add(files)).startUnit()
  }

  /** Files the person chose or dropped, each added where `accept`, `allow` and `maxSize` agree, and refused with a word
    * where they do not. */
  def add(files: List[PickedFile]): Unit = files.foreach(f => attach(Attachment.of(f)))

  /** One attachment added, where it is taken: whether it was. */
  def attach(a: Attachment): Boolean = {
    val fits = maxSize.forall(m => a.size <= m)
    val taken = everyui.file.FileTypes.accepts(accept, a.name, a.mime) && allow(a)
    if (!fits) Toast.show(a.name + " " + words.tooLarge, top = true)
    else if (!taken) Toast.show(a.name + " " + words.notAccepted, top = true)
    if (fits && taken) attachments @= attachments() :+ a
    fits && taken
  }

  /** One attachment taken off the message. */
  def remove(a: Attachment): Unit = if (enabled()) {
    attachments @= attachments().filterNot(_ eq a)
    field.focus()
  }

  /** The send and stop commands on `keymap`, by `sendChord` and `stopChord` (Primary+Enter and Primary+., which is Cmd+.
    * on a Mac), each in force while it can do something. */
  def commands(keymap: Keymap, id: String = "composer", sendChord: Chord = Composer.sendChord,
               stopChord: Chord = Composer.stopChord): Unit = {
    // made whole rather than by `add(...)(run)`, whose chords and by-name action a Roku cannot tell apart
    val sending = keymap.add(new Command(id + ".send", words.send, List(sendChord), () => send()))
    sending.enabled := enabled() && (!busy() || queueWhileBusy()) && sendable()
    val stopping = keymap.add(new Command(id + ".stop", words.stop, List(stopChord), () => stop()))
    stopping.enabled := busy()
  }

  // what a key the field was asked about does
  private def act(action: ComposerAction): Unit = action match {
    case ComposerAction.Send => send()
    case ComposerAction.Accept => acceptSuggestion()
    case ComposerAction.Dismiss => dismissSuggestion()
    case ComposerAction.Next => if (completions().nonEmpty) lit @= (lit() + 1) % completions().length
    case ComposerAction.Previous => if (completions().nonEmpty) lit @= (lit() + completions().length - 1) % completions().length
    case ComposerAction.Complete => choose(lit())
    case ComposerAction.Close => closeCompletions()
    case ComposerAction.ToQueue => if (queueShown()) queueView.focusLast()
    case _ => ()
  }

  // the controls on the line and in the tray, by how much room there is
  private def arrange(): Unit = {
    start.children.clear()
    end.children.clear()
    tray.children.clear()
    if (crowded()) {
      start.children += moreButton
      if (trayOpen()) {
        if (pickable) tray.children += trayAttach
        tray.children += leading
      }
    }
    else {
      if (pickable) start.children += attachButton
      if (leading.children().nonEmpty) start.children += leading
    }
    if (trailing.children().nonEmpty) end.children += trailing
    if (queueing()) end.children += queueButton
    end.children += sendButton
  }

  // the focusable parts under `slot` out of the focus's way while the composer is out of use, and back after
  private def inert(slot: Container, off: Boolean): Unit = {
    var pending: List[Component] = List(slot)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = c.childComponents.toList ::: pending.tail
      if (off && c.focusable()) {
        c.focusable @= false
        c.disabled @= true
        parked = c :: parked
      }
      else if (!off && parked.exists(_ eq c)) {
        c.focusable @= true
        c.disabled @= false
        parked = parked.filterNot(_ eq c)
      }
    }
  }

  // the completions for the word at the caret, asked of the app; an answer for a word since changed is dropped
  private def lookup(): Unit = complete.foreach { f =>
    val caret = field.field.map(_.caret).getOrElse(text().length)
    asked += 1
    val mine = asked
    TriggerWord.at(text(), caret, triggers) match {
      case Some(w) if enabled() && field.focused() =>
        word = Some(w)
        f(w.trigger, w.prefix).map(found => if (mine == asked) showCompletions(found)).startUnit()
      case _ => closeCompletions()
    }
  }

  private def showCompletions(found: Vector[Completion]): Unit = {
    drop()
    completions @= found.take(8)
    lit @= 0
    if (completions().nonEmpty) {
      val p = new CompletionPopup(completions(), lit, math.min(360.0, card.size.width() - pad() * 2.0))
      p.position.left := math.max(8.0, card.absoluteLeft + pad())
      p.position.top := math.max(8.0, card.absoluteTop - p.size.height() - gap)
      p.chosen.attach(i => choose(i))
      popup = Some(p)
      ui.children += p
    }
  }

  private def closeCompletions(): Unit = {
    asked += 1
    drop()
    word = None
    completions @= Vector.empty
  }

  private def drop(): Unit = {
    popup.foreach { p =>
      ui.children -= p
      p.dispose()
    }
    popup = None
  }

  // the completion at `i` in place of the word being typed
  private def choose(i: Int): Unit = {
    val chosen = if (i >= 0 && i < completions().length) Some(completions()(i)) else None
    val at = word
    closeCompletions()
    chosen.foreach { c =>
      at.foreach { w =>
        val (replaced, caret) = TriggerWord.replace(text(), w, c.insert)
        text @= replaced
        field.field.foreach(f => Platform.later(() => f.moveCaret(caret)))
        field.focus()
        // a click on the list took the platform's field out of typing
        field.editing @= true
      }
    }
  }

  override def dispose(): Unit = {
    closeCompletions()
    super.dispose()
  }

  override def kind: String = "Composer"
}

object Composer {
  /** Send, from anywhere its keymap is in force: Primary+Enter. */
  val sendChord: Chord = Chord("Enter", primary = true)
  /** Stop, while a reply is coming: Primary+. (Cmd+. on a Mac, Ctrl+. elsewhere). */
  val stopChord: Chord = Chord(".", primary = true)

  /** Whether the keyboard is on the screen, so Enter is a new line: a touch screen with no fine pointer (a phone, a
    * tablet). */
  def touchScreen(): Boolean = Platform.touchSupport() && !Platform.pointerSupport()

  private def slot(): Container = {
    val row = new Container
    row.layout @= Some(HorizontalLayout(4.0, middle = true))
    row
  }
}

package everyui.chat

import everyui.Color
import everyui.component.Component
import reactify.*

/** One thing in a conversation's transcript, in the order they happened: a message, a tool call, an agent's reasoning,
  * a note between them, or something of the app's own. `id` is stable for as long as the entry lives (the transcript
  * keeps its place, and anything it made for it, by the id), and what can change is reactive, so an entry is changed in
  * place (`reply.text @= reply.text() + chunk`) rather than replaced. `time` is when it happened (epoch milliseconds),
  * where that is known: it dates the transcript's day separators.
  *
  * {{{
  *   val reply = ChatEntry.message("m-42", helper, self = false, streaming = true, time = Some(now))
  *   entries @= entries() :+ reply
  *   reply.text @= reply.text() + chunk          // as it arrives
  *   reply.streaming @= false                    // once it is whole
  * }}} */
sealed abstract class ChatEntry {
  def id: String
  def time: Option[Long]

  /** The colour of a thin line down the entry's start edge, saying which thread or topic it is in: none (the default)
    * draws none, and entries one after another of the same colour draw one unbroken line (`Transcript.rails`). It can
    * be set or changed at any time, a message's thread known only after it arrives. Made the first time it is asked
    * for, so an entry never drawn and never given one costs nothing. */
  lazy val rail: Var[Option[Color]] = Var(None)
  /** What a screen reader says of the rail, after the entry (the topic's name): nothing by default. */
  lazy val railLabel: Var[Option[String]] = Var(None)
}

object ChatEntry {
  /** Something somebody said. `self` is the person using the app, whose messages sit at the end edge (right, in a
    * left-to-right language); everybody else's sit at the start, under their avatar and name. `text` grows while
    * `streaming`, and `meta` is an optional line under it from the app (the model, tokens, how long it took). */
  final class Message(val id: String, val author: Participant, val self: Boolean, val time: Option[Long] = None)
    extends ChatEntry {
    val text: Var[String] = Var("")
    val streaming: Var[Boolean] = Var(false)
    val meta: Var[Option[String]] = Var(None)
  }

  /** A tool call, as one line: what it does (`summary`) and how it stands. `details` makes what there is to see of it
    * (its input and output as text, an image, a table): made the first time the line is opened, and kept.
    *
    * `lead` is what goes first on the line (`ActivityLead`): by default the status mark alone; somebody's avatar, a
    * ring turning around it while the call runs and the status on its corner once it has; or an icon, with the status
    * on its corner. `body` is a short text under the summary, shown without opening the line (the message that was
    * sent, the command that ran): Markdown, drawn as the transcript draws a message's words, cut to the transcript's
    * `bodyLines` and shown whole once the line is opened. Empty by default. */
  final class Activity(val id: String, val details: Option[() => Component] = None, val time: Option[Long] = None)
    extends ChatEntry {
    val summary: Var[String] = Var("")
    val status: Var[ActivityStatus] = Var(ActivityStatus.Running)
    val expanded: Var[Boolean] = Var(false)
    val lead: Var[ActivityLead] = Var(ActivityLead.None)
    val body: Var[String] = Var("")
    // whether its body, as last drawn, was cut short: it then opens, to be read whole
    private[chat] val cut: Var[Boolean] = Var(false)

    /** Whether it opens: onto its details, or its body cut short. */
    def opens: Boolean = details.nonEmpty || cut()
  }

  /** An agent's reasoning as it goes, begun at `started` (epoch milliseconds); `done` once it has, with how long it took
    * (milliseconds). Closed it is one line with the time taken so far; opened, the words. */
  final class Thinking(val id: String, val started: Long) extends ChatEntry {
    val text: Var[String] = Var("")
    val done: Var[Option[Long]] = Var(None)
    val expanded: Var[Boolean] = Var(false)
    def time: Option[Long] = Some(started)
  }

  /** A note between the rest, centred and quiet: "Topic renamed: Budget", a date. */
  final class Marker(val id: String, val time: Option[Long] = None) extends ChatEntry {
    val text: Var[String] = Var("")
  }

  /** The app's own component in the flow: an approval with its buttons, a sign-in form, a choice of options. `make` is
    * called once, the first time the entry shows, and what it makes is kept (with whatever was typed or chosen in it)
    * until the entry leaves the transcript. It can take the focus. */
  final class Custom(val id: String, val make: () => Component, val time: Option[Long] = None) extends ChatEntry

  def message(id: String, author: Participant, self: Boolean, text: String = "", time: Option[Long] = None,
              streaming: Boolean = false, meta: Option[String] = None, rail: Option[Color] = None): Message = {
    val m = new Message(id, author, self, time)
    m.text @= text
    m.streaming @= streaming
    m.meta @= meta
    railed(m, rail)
  }

  def activity(id: String, summary: String, status: ActivityStatus = ActivityStatus.Running,
               details: Option[() => Component] = None, time: Option[Long] = None,
               lead: ActivityLead = ActivityLead.None, body: String = "", rail: Option[Color] = None): Activity = {
    val a = new Activity(id, details, time)
    a.summary @= summary
    a.status @= status
    a.lead @= lead
    a.body @= body
    railed(a, rail)
  }

  def thinking(id: String, started: Long, text: String = "", done: Option[Long] = None,
               rail: Option[Color] = None): Thinking = {
    val t = new Thinking(id, started)
    t.text @= text
    t.done @= done
    railed(t, rail)
  }

  def marker(id: String, text: String, time: Option[Long] = None, rail: Option[Color] = None): Marker = {
    val m = new Marker(id, time)
    m.text @= text
    railed(m, rail)
  }

  def custom(id: String, make: () => Component, time: Option[Long] = None, rail: Option[Color] = None): Custom =
    railed(new Custom(id, make, time), rail)

  // `e` with its rail, where it is given one (and the rail left unmade where it is not)
  private def railed[E <: ChatEntry](e: E, rail: Option[Color]): E = {
    if (rail.nonEmpty) e.rail @= rail
    e
  }

  /** A tool call's `details` as plain text: what went in, then what came out, each under a small heading, wrapped to the
    * width the transcript gives it. */
  def textDetails(input: String, output: String): () => Component = () => ActivityView.textDetails(input, output)
}

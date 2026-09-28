package everyui.monaco

import everyui.component.Embed
import fabric.*

/** An embed whose guest is played by the spec: every call it is sent is kept, and answered by `answer` (a value, or
  * `None` to leave it unanswered); `say` makes the guest say something. */
class FakeGuest(name: String, answer: (String, Json) => Option[Json]) extends Embed("monaco") {
  var calls: List[(String, Json)] = Nil
  var ids: List[String] = Nil
  private var listeners: List[String => Unit] = Nil

  override def runtime: String = name
  override def hear(f: String => Unit): Unit = listeners = listeners :+ f
  override def send(message: String): Unit = {
    val call = Wire.parse(message)
    val method = Wire.string(call, "method")
    val args = call.get("args").getOrElse(obj())
    calls = calls :+ (method -> args)
    ids = ids :+ Wire.string(call, "callId")
    answer(method, args).foreach(value => say("_return", obj("callId" -> call("callId"), "value" -> value)))
  }

  def say(kind: String, payload: Json): Unit = {
    val text = Wire.text(obj("type" -> str(kind), "payload" -> payload))
    listeners.foreach(f => f(text))
  }

  def methods: List[String] = calls.map(_._1)

  /** The `n`th call answered with `value`. */
  def reply(n: Int, value: Json): Unit = say("_return", obj("callId" -> str(ids(n)), "value" -> value))
}

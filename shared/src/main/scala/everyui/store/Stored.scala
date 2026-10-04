package everyui.store

import everyui.async.Task
import everyui.render.Platform
import reactify.*

/** Typed values in the [[Store]], and a `Var` kept there: what an app remembers for the person between launches —
  * how they sort a list, which tab they chose — in one declaration, the same on every backend.
  *
  * {{{
  * val sort: Var[String] = Stored("dashboard", "sort", "name")
  * }}}
  *
  * The value is read once, as the app starts, and written on every change after. Everything [[Store]] says holds
  * here: the read may come back empty, and the app draws the same screen from the default when it does. Anything
  * that must survive belongs on the server, never here.
  *
  * The text is the [[Codec]]'s. A value whose text is empty (an empty String) is kept by forgetting the document,
  * since no backend tells an empty document from none; it comes back as the default. */
object Stored {
  /** The value kept under `key` in `space`, or None where there is none, or none `Codec[T]` can read. */
  def get[T](space: String, key: String)(using codec: Codec[T]): Task[Option[T]] =
    Store.get(space, key).map(_.flatMap(text => codec.decode(text)))

  /** Keep `value` under `key` in `space`. */
  def put[T](space: String, key: String, value: T)(using codec: Codec[T]): Task[Unit] = {
    val text = codec.encode(value)
    if (text.isEmpty) Store.delete(space, key) else Store.put(space, key, text)
  }

  /** A Var holding `default`, kept under `key` in `space` from now on (see `connect`). */
  def apply[T](space: String, key: String, default: T)(using codec: Codec[T]): Var[T] = {
    val v = Var[T](default)
    connect[T](space, key, v)
    v
  }

  /** Keep `v` under `key` in `space`, starting now: the kept value is read and given to `v`, and every change to `v`
    * after is written.
    *
    *   - The read is asynchronous. If `v` changes before it answers — the app set it, or the person chose — that
    *     change stands, and the kept value is not applied over it.
    *   - The kept value given to `v` is not written back.
    *   - Nothing kept, or nothing `Codec[T]` can read, leaves `v` as it is.
    *   - Writes are coalesced within a turn: `v` set many times over in one pass is written once, as it ends, with
    *     the value it ended on. Nothing is delayed past that, so the last change is not lost to a page reloaded or an
    *     app closed straight after it. A value that changes every frame (a slider mid-drag) is written every frame;
    *     keep the value it settles on instead.
    *
    * `Store.delete(space, key)` forgets it (the next launch starts from the default) and `Store.clear(space)` the
    * whole space; `v` keeps what it holds until it next changes, which is written as before.
    *
    * Answers, once the read has, the kept value it gave `v`: None when there was none, it could not be read, or `v`
    * had already changed. The binding does not wait for the task to be started. */
  def connect[T](space: String, key: String, v: Var[T])(using codec: Codec[T]): Task[Option[T]] =
    bind[T](v, get[T](space, key), value => put[T](space, key, value).startUnit())

  /** `connect` over any read and write: the binding itself, which a spec drives with a read that answers when it
    * chooses. */
  private[store] def bind[T](v: Var[T], load: Task[Option[T]], save: T => Unit): Task[Option[T]] = {
    // the value the store is known to hold: the one read, or the last written. A change to it (the read value being
    // applied, whenever reactify runs the reaction) is not written, and every other change is.
    var kept: Option[T] = None
    var touched = false
    var pending = false
    v.attach { value =>
      if (!kept.contains(value)) {
        touched = true
        if (!pending) {
          pending = true
          Platform.later(() => {
            pending = false
            val now = v()
            if (!kept.contains(now)) {
              kept = Some(now)
              save(now)
            }
          })
        }
      }
    }
    // the read's answer, once it has come: a flag beside it, as `Some(None)` is None on a Roku, where a read that found
    // nothing was taken for one not come yet and `connect` never completed
    var answered = false
    var answer: Option[T] = None
    var waiting: List[Option[T] => Unit] = Nil
    load.map { found =>
      val applied = if (touched) None else found
      applied.foreach { value =>
        kept = Some(value)
        v @= value
      }
      answer = applied
      answered = true
      waiting.foreach(done => done(applied))
      waiting = Nil
    }.start()
    Task.async[Option[T]] { done =>
      if (answered) done(answer) else waiting = done :: waiting
    }
  }
}

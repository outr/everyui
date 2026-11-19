package everyui.store

import everyui.async.Task
import everyui.render.Platform

/** A small store of documents the device keeps between launches, in named spaces: what an app caches so a screen
  * can be drawn before the server has answered.
  *
  * It is deliberately the narrowest thing that serves that: a document under a key, fetched, kept, forgotten or
  * listed. No queries, no indexes, no transactions — because every backend owes an implementation of whatever is
  * declared here, and two of them (a Roku in BrightScript, an Apple television in Swift) would have to build by
  * hand whatever a richer surface promised. A key and a document they can each do in a few lines of file work.
  *
  * A value is a `String`, so a backend only ever moves text about and never learns how anything is encoded;
  * whatever writes a document decides that (`fabric`, usually) at the call site.
  *
  * **A read may always come back empty.** This is a cache and nothing more: a Roku keeps it where the box may clear
  * it, a browser where the viewer may clear it, and a device that has never run the app has none of it at all.
  * Every caller must draw the same screen when the document is missing — a feature that only works on the second
  * launch is a broken feature.
  *
  * Asynchronous because one backend leaves no choice: a browser's IndexedDB only answers later. The devices whose
  * files are read synchronously answer before the task is looked at, which costs them nothing. */
object Store {
  /** The document under `key` in `space`, or None where there is none. */
  def get(space: String, key: String): Task[Option[String]] =
    Task.async[Option[String]](done => Platform.storeGet(space, key, done))

  /** Keep `value` under `key` in `space`, replacing whatever was there.
    *
    * Where files are written synchronously (Android, the desktop) the document is written on the calling thread
    * before this returns: called on the drawing thread, a large one holds up the frames (a 16 MB document blocked a
    * phone's for 300 ms). Write anything large with `putOff`. */
  def put(space: String, key: String, value: String): Task[Unit] =
    Task.async[Unit](done => Platform.storePut(space, key, value, () => done(())))

  /** `put`, written on a thread that does not draw (`Platform.offThread`) where the platform has one, the task
    * completed back on the one that does: for a large document. Writes put off this way are made one after another in
    * the order asked for, but until the task completes a `get` may still read what was there, and a `put` of the same
    * key made meanwhile may be written over by this one: keep a key to one or the other. */
  def putOff(space: String, key: String, value: String): Task[Unit] =
    Task.async[Unit](done => Platform.offThread(() =>
      Platform.storePut(space, key, value, () => Platform.later(() => done(())))))

  /** Forget one document. Forgetting one that is not there is not an error. */
  def delete(space: String, key: String): Task[Unit] =
    Task.async[Unit](done => Platform.storeDelete(space, key, () => done(())))

  /** The keys `space` holds, in no particular order. */
  def keys(space: String): Task[List[String]] =
    Task.async[List[String]](done => Platform.storeKeys(space, done))

  /** Forget every document in `space`: what signing out does to whatever was cached for whoever signed in. */
  def clear(space: String): Task[Unit] =
    Task.async[Unit](done => Platform.storeClear(space, () => done(())))
}

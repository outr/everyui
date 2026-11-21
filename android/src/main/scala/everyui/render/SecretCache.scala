package everyui.render

import java.util.concurrent.ConcurrentHashMap

/** What [[Secrets]] keeps of its keystore in the process, so a secret costs the keystore once: the key, loaded the
  * first time it is needed and held until it fails, and each value as it was opened, by the sealed text it was opened
  * from, so a value sealed again since (by another of the app's processes) is opened again rather than answered from
  * here. Android's keystore answers each ask through the system's keystore service, slowly after the device wakes
  * (Voidcraft on a Pixel Fold: seconds on the main thread, at every read of its token); a value opened once is a map's
  * lookup after that. Safe from any thread: one load of the key however many ask at once, and a read of a value
  * already opened never waits on it. */
final class SecretCache[K <: AnyRef](keys: SecretCache.Keys[K]) {
  @volatile private var loaded: Option[K] = None
  private val loading = new Object
  // each name's (sealed text, value)
  private val opened = new ConcurrentHashMap[String, (String, String)]()
  // the names a warning has been said of (a key set of its own is Android 7's)
  private val told = new ConcurrentHashMap[String, java.lang.Boolean]()

  /** `name`'s value where it was opened from `locked` before. */
  def cached(name: String, locked: String): Option[String] =
    Option(opened.get(name)).collect { case (l, value) if l == locked => value }

  /** `name`'s value, sealed as `locked`: as it was opened before, or opened now with the key and kept. Text the key
    * cannot open (not its own, or damaged) is "" from then on, said once by the failure thrown here. */
  def read(name: String, locked: String): String = cached(name, locked).getOrElse {
    try {
      val value = withKey(keys.open(_, locked))
      opened.put(name, (locked, value))
      value
    }
    catch {
      case t: Throwable =>
        if (!keys.keyFault(t)) opened.put(name, (locked, ""))
        throw t
    }
  }

  /** `value` sealed with the key, to be kept under `name`, and kept here as what `name` holds. */
  def write(name: String, value: String): String = {
    val locked = withKey(keys.seal(_, value))
    opened.put(name, (locked, value))
    locked
  }

  /** `name` holds nothing now. */
  def forget(name: String): Unit = opened.remove(name)

  /** Whether `name` is asked of here for the first time (a warning said once a name). */
  def firstTime(name: String): Boolean = told.putIfAbsent(name, java.lang.Boolean.TRUE) == null

  private def key(): K = loaded.getOrElse(loading.synchronized {
    loaded.getOrElse {
      val made = keys.load()
      loaded = Some(made)
      made
    }
  })

  // `f` with the key; where the key is what failed, it is let go (deleted first where it can never be used again, the
  // lock screen having changed) and `f` tried once more with the key loaded, or made, anew
  private def withKey[A](f: K => A): A = {
    val held = key()
    try f(held)
    catch {
      case t: Throwable if keys.keyFault(t) =>
        loading.synchronized {
          if (loaded.exists(_ eq held)) {
            if (keys.unusable(t)) keys.discard()
            loaded = None
          }
        }
        f(key())
    }
  }
}

object SecretCache {
  /** What the cache asks of the store that holds the key: Android's keystore ([[Secrets]]), or a stand-in. */
  trait Keys[K] {
    /** The key, made where there is none. */
    def load(): K
    /** The key deleted, so the next load makes a new one. */
    def discard(): Unit
    /** The value `locked` holds, opened with `key`. */
    def open(key: K, locked: String): String
    /** `value` sealed with `key`. */
    def seal(key: K, value: String): String
    /** Whether `failure` was the key's rather than the sealed text's: the key is loaded again and tried once more. */
    def keyFault(failure: Throwable): Boolean
    /** Whether `failure` says the key can never be used again: it is discarded for a new one. */
    def unusable(failure: Throwable): Boolean
  }
}

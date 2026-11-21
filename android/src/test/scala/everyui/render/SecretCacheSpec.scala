package everyui.render

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The keystore is asked once a process, and each secret opened once ([[SecretCache]]): Voidcraft's token read went to
  * Android's keystore at every call, on the main thread, and held it for seconds after a Pixel Fold woke from standby
  * (0.1.39). A stand-in keystore here counts what is asked of it: text sealed by key `n` reads "n:value". */
class SecretCacheSpec extends AnyWordSpec with Matchers {
  private final class Key(val n: Int)
  private final class KeyFailed extends RuntimeException("the key failed")
  private final class KeyGone extends RuntimeException("the key can never be used again")
  private final class BadText extends RuntimeException("not this key's text")

  private class Store extends SecretCache.Keys[Key] {
    val loads = new AtomicInteger()
    val opens = new AtomicInteger()
    val seals = new AtomicInteger()
    var discards = 0
    var current = 1                  // the key the store holds
    var failNext: Option[RuntimeException] = None
    def load(): Key = {
      loads.incrementAndGet()
      new Key(current)
    }
    def discard(): Unit = {
      discards += 1
      current += 1
    }
    private def fail(): Unit = failNext.foreach { t =>
      failNext = None
      throw t
    }
    def open(key: Key, locked: String): String = {
      opens.incrementAndGet()
      fail()
      locked.split(":", 2) match {
        case Array(n, value) if n == key.n.toString => value
        case _ => throw new BadText
      }
    }
    def seal(key: Key, value: String): String = {
      seals.incrementAndGet()
      fail()
      s"${key.n}:$value"
    }
    def keyFault(failure: Throwable): Boolean = !failure.isInstanceOf[BadText]
    def unusable(failure: Throwable): Boolean = failure.isInstanceOf[KeyGone]
  }

  "A secret" should {
    "be opened with the key the first time it is read, and from the cache after" in {
      val store = new Store
      val cache = new SecretCache(store)
      (1 to 100).foreach(_ => cache.read("token", "1:abc") shouldBe "abc")
      store.loads.get shouldBe 1
      store.opens.get shouldBe 1
      cache.cached("token", "1:abc") shouldBe Some("abc")
    }
    "load the key once for every secret" in {
      val store = new Store
      val cache = new SecretCache(store)
      cache.read("a", "1:x") shouldBe "x"
      cache.read("b", "1:y") shouldBe "y"
      cache.write("c", "z") shouldBe "1:z"
      store.loads.get shouldBe 1
    }
    "be what was kept, once kept, without opening it again" in {
      val store = new Store
      val cache = new SecretCache(store)
      cache.read("token", "1:old") shouldBe "old"
      val locked = cache.write("token", "new")
      cache.cached("token", locked) shouldBe Some("new")
      cache.read("token", locked) shouldBe "new"
      store.opens.get shouldBe 1
    }
    "be opened again where the store holds other text for it (sealed by another process)" in {
      val store = new Store
      val cache = new SecretCache(store)
      cache.read("token", "1:abc")
      cache.cached("token", "1:def") shouldBe None
      cache.read("token", "1:def") shouldBe "def"
      store.opens.get shouldBe 2
    }
    "be nothing once forgotten" in {
      val store = new Store
      val cache = new SecretCache(store)
      cache.read("token", "1:abc")
      cache.forget("token")
      cache.cached("token", "1:abc") shouldBe None
      cache.read("token", "1:abc") shouldBe "abc"
      store.opens.get shouldBe 2
    }
    "be nothing, said once, where its text is not the key's, without asking the keystore again" in {
      val store = new Store
      val cache = new SecretCache(store)
      a[BadText] should be thrownBy cache.read("token", "7:abc")
      cache.read("token", "7:abc") shouldBe ""
      store.opens.get shouldBe 1
      store.loads.get shouldBe 1
    }
  }

  "The key" should {
    "be loaded again, and the work tried once more, where it fails" in {
      val store = new Store
      val cache = new SecretCache(store)
      cache.read("a", "1:x")
      store.failNext = Some(new KeyFailed)
      cache.read("b", "1:y") shouldBe "y"
      store.loads.get shouldBe 2
      store.discards shouldBe 0
      cache.read("c", "1:z") shouldBe "z"
      store.loads.get shouldBe 2
    }
    "be discarded and made anew where it can never be used again (the lock screen changed)" in {
      val store = new Store
      val cache = new SecretCache(store)
      cache.read("a", "1:x")
      store.failNext = Some(new KeyGone)
      cache.write("token", "abc") shouldBe "2:abc"
      store.discards shouldBe 1
      store.loads.get shouldBe 2
      // what the lost key sealed cannot be opened, and is nothing
      a[BadText] should be thrownBy cache.read("b", "1:y")
      cache.read("token", "2:abc") shouldBe "abc"
    }
    "not be read as a secret that is not there, where it fails twice" in {
      val store = new Store {
        override def open(key: Key, locked: String): String = {
          opens.incrementAndGet()
          throw new KeyFailed
        }
      }
      val cache = new SecretCache(store)
      a[KeyFailed] should be thrownBy cache.read("token", "1:abc")
      store.opens.get shouldBe 2
      cache.cached("token", "1:abc") shouldBe None
    }
    "be loaded once however many threads ask at once" in {
      val store = new Store {
        override def load(): Key = {
          Thread.sleep(50)
          super.load()
        }
      }
      val cache = new SecretCache(store)
      val pool = Executors.newFixedThreadPool(8)
      val start = new CountDownLatch(1)
      val done = new CountDownLatch(8)
      (1 to 8).foreach { i =>
        pool.execute(() => {
          start.await()
          cache.read("s" + i, "1:v" + i)
          done.countDown()
        })
      }
      start.countDown()
      done.await(10, TimeUnit.SECONDS) shouldBe true
      pool.shutdown()
      store.loads.get shouldBe 1
      store.opens.get shouldBe 8
    }
  }

  "The main thread's warning" should {
    "be said once a name" in {
      val cache = new SecretCache(new Store)
      cache.firstTime("token") shouldBe true
      cache.firstTime("token") shouldBe false
      cache.firstTime("other") shouldBe true
    }
  }
}

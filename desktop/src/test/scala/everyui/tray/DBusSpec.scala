package everyui.tray

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CompletableFuture, TimeUnit}

/** D-Bus spoken by hand, on a bus of the spec's own: values through the wire and back, a connection said Hello to, a
  * method served and called across connections, an error for one not served, and a signal heard through a match. */
class DBusSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private lazy val bus = new PrivateBus

  override def afterAll(): Unit = bus.stop()

  private def waitFor[T](f: CompletableFuture[T]): T = f.get(5, TimeUnit.SECONDS)

  "The wire" should {
    "carry each kind of value there and back" in {
      val sig = "ybnqiuxtdsoga{sv}(ias)vay"
      val values = List(7.toByte, true, -3, 65000, -70000, 123456, -5L, 9L, 2.5, "héllo", DBusPath("/a/b"),
        DBusSignature("a(iiay)"), Vector("k" -> DBusVariant("i", 4), "t" -> DBusVariant("s", "x")),
        DBusStruct(List(1, Vector("a", "bc"))), DBusVariant("as", Vector("z")), Vector(1.toByte, 2.toByte))
      val m = DBusMessage(DBusMessage.Call, 3, "/p", "i.f", "M", destination = "d.e", signature = sig, body = values)
      val back = DBusWire.decode(DBusWire.encode(m))
      back.body shouldBe values
      (back.path, back.interface, back.member, back.destination, back.serial) shouldBe ("/p", "i.f", "M", "d.e", 3)
      DBusWire.length(DBusWire.encode(m).take(16)) shouldBe DBusWire.encode(m).length
    }
  }

  "A connection" should {
    "be said Hello to, and be among the bus's names" in {
      val c = bus.connect()
      try {
        c.uniqueName should startWith (":")
        val names = c.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "ListNames")
        names.get.body.head.asInstanceOf[Vector[String]] should contain (c.uniqueName)
      } finally c.close()
    }
    "answer a method it serves, and an error for one it does not" in {
      val (a, b) = (bus.connect(), bus.connect())
      try {
        a.serve("/echo") { m =>
          if (m.member == "Echo") Some(("s", List("you said " + m.body.head))) else None
        }
        val said = new CompletableFuture[DBusMessage]()
        b.call(a.uniqueName, "/echo", "test.Echo", "Echo", "s", List("hi"))(m => said.complete(m))
        waitFor(said).body shouldBe List("you said hi")
        val wrong = new CompletableFuture[DBusMessage]()
        b.call(a.uniqueName, "/echo", "test.Echo", "Nothing")(m => wrong.complete(m))
        waitFor(wrong).errorName shouldBe "org.freedesktop.DBus.Error.UnknownMethod"
      } finally {
        a.close()
        b.close()
      }
    }
    "hear a signal it matches" in {
      val (a, b) = (bus.connect(), bus.connect())
      try {
        val heard = new CompletableFuture[DBusMessage]()
        b.onSignal(m => if (m.member == "Changed") heard.complete(m))
        b.ask(DBusConnection.Bus, DBusConnection.BusPath, DBusConnection.Bus, "AddMatch", "s",
          List("type='signal',interface='test.Thing'"))
        a.signal("/thing", "test.Thing", "Changed", "u", List(42))
        val m = waitFor(heard)
        (m.body, m.sender) shouldBe (List(42), a.uniqueName)
      } finally {
        a.close()
        b.close()
      }
    }
  }
}

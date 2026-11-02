package everyui.push

import everyui.{FxSpec, Permission}

/** Push on the desktop, which has none: said up front, the permission refused and a subscription answered at once as
  * unsupported, nothing to close; and a tap, as a platform reports one, heard on `opened`, its tag with it. */
class PushSpec extends FxSpec {
  private def answer[T](start: (T => Unit) => Unit): T = {
    var result: Option[T] = None
    fx(start(r => result = Some(r)))
    result.get
  }

  "Push on the desktop" should {
    "say there is none" in {
      Push.supported shouldBe false
      answer[Permission](done => Push.permission.map(done).start()) shouldBe Permission.Refused
      answer[Boolean](done => Push.permit().map(done).start()) shouldBe false
    }

    "answer a subscription at once as unsupported, and an unsubscription at once" in {
      answer[PushAnswer](done => Push.subscribe("key").map(done).start()) shouldBe PushAnswer.PushUnsupported
      answer[Unit](done => Push.unsubscribe().map(done).start()) shouldBe (())
      // and nothing shown to close
      fx(Push.close("c1"))
    }

    "hear a tap on opened" in {
      var heard: List[PushMessage] = Nil
      fx(Push.opened.attach(m => heard = heard :+ m))
      val tapped = PushMessage("Build done", "voidcraft#42 passed", Some("/runs/42"), tag = Some("runs"))
      fx(Push.tapped(tapped))
      heard shouldBe List(tapped)
    }
  }
}

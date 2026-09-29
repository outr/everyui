package everyui.passkey

import everyui.FxSpec

/** Passkeys on the desktop, which has none: said up front, and every request answered at once as unsupported, so an
  * app offers another way to sign in. */
class PasskeySpec extends FxSpec {
  private def answer(start: (PasskeyResult => Unit) => Unit): PasskeyResult = {
    var result: Option[PasskeyResult] = None
    fx(start(r => result = Some(r)))
    result.get
  }

  "Passkeys on the desktop" should {
    "say there are none" in {
      Passkeys.supported shouldBe false
      var autofill: Option[Boolean] = None
      fx(Passkeys.autofillable.map(a => autofill = Some(a)).start())
      autofill shouldBe Some(false)
    }

    "answer every request at once as unsupported" in {
      answer(done => Passkeys.create("{}").map(done).start()) shouldBe PasskeyResult.PasskeyUnsupported
      answer(done => Passkeys.get("{}").map(done).start()) shouldBe PasskeyResult.PasskeyUnsupported
      answer(done => Passkeys.autofill("{}").map(done).start()) shouldBe PasskeyResult.PasskeyUnsupported
    }
  }
}

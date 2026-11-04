package everyui.auth

import everyui.FxSpec
import everyui.async.Task
import everyui.render.SignInAccess

import java.net.{HttpURLConnection, URI}

/** Signing in with another site on the desktop: the redirect to 127.0.0.1 at a port of this sign-in's own, listened
  * for until it comes or the time runs out. A browser stands in for the system's, so nothing opens on the screen. */
class SignInSpec extends FxSpec {
  private val callback = SignInCallback(loopback = "/signin")

  /** What the browser stand-in is sent to, and what it then asks for of the redirect. */
  private def browsing(follow: String => List[String]): Unit = SignInAccess.opener = url => {
    val t = new Thread(() => follow(url).foreach { address =>
      val c = new URI(address).toURL.openConnection().asInstanceOf[HttpURLConnection]
      c.getResponseCode
      c.disconnect()
    })
    t.setDaemon(true)
    t.start()
  }

  private def signedIn(minutes: Double)(authorize: String => Task[String]): Option[String] = {
    var result: Option[Option[String]] = None
    fx(SignIn.signInWith(callback, minutes)(authorize).map(r => result = Some(r)).start())
    eventually(result.nonEmpty) shouldBe true
    result.get
  }

  "Signing in on the desktop" should {
    "hand the app a loopback redirect, and answer with the address it ends at" in {
      var redirect = ""
      browsing(url => {
        val back = new URI(url).getQuery.stripPrefix("redirect_uri=")
        List(back.stripSuffix("/signin") + "/favicon.ico", back + "?code=abc&state=s1")
      })
      val ended = signedIn(1.0) { r =>
        redirect = r
        Task.pure("https://provider.example.com/authorize?redirect_uri=" + r)
      }
      redirect should fullyMatch regex """http://127\.0\.0\.1:\d+/signin"""
      ended shouldBe Some(redirect + "?code=abc&state=s1")
    }
    "take a port for each sign-in, let go once it is done" in {
      var redirects = List.empty[String]
      browsing(url => List(new URI(url).getQuery.stripPrefix("redirect_uri=") + "?code=x"))
      (1 to 2).foreach(_ => signedIn(1.0) { r =>
        redirects = redirects :+ r
        Task.pure("https://provider.example.com/authorize?redirect_uri=" + r)
      }.nonEmpty shouldBe true)
      redirects.distinct.length shouldBe 2
      val port = new URI(redirects.head).getPort
      val c = new URI("http://127.0.0.1:" + port.toString + "/signin?code=late").toURL.openConnection()
      an[java.io.IOException] should be thrownBy c.getInputStream
    }
    "be None where no redirect comes in time, or there is no address to sign in at" in {
      browsing(_ => Nil)
      signedIn(0.01)(r => Task.pure("https://provider.example.com/authorize?redirect_uri=" + r)) shouldBe None
      signedIn(1.0)(_ => Task.pure("")) shouldBe None
    }
  }

  override def afterAll(): Unit = {
    SignInAccess.opener = url => everyui.render.Platform.openUrl(url)
    super.afterAll()
  }
}

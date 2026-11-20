package everyui.network

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spice.http.client.OkHttpClientImplementation

/** What Spice makes of a request before it goes (its URL parsed) is made inside the task: a URL it cannot parse fails
  * the task, for `attempt` to see, rather than throwing from the call. On webOS 3 (an ES 5.1 link) every URL's parse
  * threw before Spice 1.10.13, and the throw left the app's `attempt` and the net demo's check with nothing said. */
class SpiceCallSpec extends AnyWordSpec with Matchers {
  SpiceHttp.use(OkHttpClientImplementation)

  "SpiceHttp.call" should {
    "fail its task, not throw, where the URL does not parse" in {
      val task = SpiceHttp.call(SpiceHttp.client(OkHttpClientImplementation), HttpCall("GET", "not a url"))
      task.attempt.sync().isFailure shouldBe true
    }
  }
}

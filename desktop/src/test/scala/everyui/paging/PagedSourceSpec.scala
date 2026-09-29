package everyui.paging

import everyui.async.Task
import everyui.table.Sorting
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A server's rows a page at a time: the count from the first answer, pages asked for as the view nears them and no
  * more than so many at once, a page passed before its turn never asked for, an answer to an outdated query dropped,
  * a bounded cache, errors kept until a retry, and load-more by cursor. */
class PagedSourceSpec extends AnyWordSpec with Matchers {
  case class Row(id: String, name: String)

  /** A server of `total` rows that answers when the spec says: what it was asked, in order, and the answers owed. */
  class Server(total: Int, cursors: Boolean = false, counts: Boolean = true) {
    var asked: Vector[PageRequest] = Vector.empty
    var owed: Vector[(PageRequest, Either[String, Page[Row]] => Unit)] = Vector.empty

    def fetch(request: PageRequest): Task[Either[String, Page[Row]]] = Task.async { done =>
      asked = asked :+ request
      owed = owed :+ (request, done)
    }

    def page(request: PageRequest): Page[Row] = {
      val end = math.min(total, request.offset + request.limit)
      val desc = request.sort.exists(!_.ascending)
      val rows = (request.offset until end).map { i =>
        val n = if (desc) total - 1 - i else i
        Row("r" + n, "row " + n)
      }.toVector
      Page(rows, if (counts) Some(total) else None, if (cursors && end < total) Some("after" + end) else None)
    }

    /** The oldest answer owed, given (or `failing` with a message). */
    def answer(failing: Option[String] = None): PageRequest = answerAt(0, failing)

    def answerAt(at: Int, failing: Option[String] = None): PageRequest = {
      val (request, done) = owed(at)
      owed = owed.patch(at, Nil, 1)
      done(failing.map(m => Left(m)).getOrElse(Right(page(request))))
      request
    }

    def answerAll(): Unit = while (owed.nonEmpty) answer()
  }

  private def source(server: Server, paging: Paging = Paging.Anywhere, maxPages: Int = 20): PagedSource[Row] =
    new PagedSource[Row](_.id, pageSize = 100, paging = paging, maxPages = maxPages)(server.fetch)

  "A paged source" should {
    "know the count after the first page, and hold placeholders until their page comes" in {
      val server = new Server(1900000)
      val s = source(server)
      s.searching() shouldBe true
      s.count shouldBe 100
      s.showing(0, 20)
      server.asked.map(_.offset) shouldBe Vector(0)
      s.loading() shouldBe true
      server.answer()
      s.searching() shouldBe false
      s.loading() shouldBe false
      s.total() shouldBe Some(1900000)
      s.count shouldBe 1900000
      s.at(5).map(_.id) shouldBe Some("r5")
      s.at(150) shouldBe None
      s.keyAt(150) shouldBe PagedSource.placeKey(150)
      s.indexOf(PagedSource.placeKey(150)) shouldBe 150
      s.indexOf("r42") shouldBe 42
      s.indexOf("r150") shouldBe -1
    }
    "fetch the page in view first, then those it nears, no more than two at once" in {
      val server = new Server(1900000)
      val s = source(server)
      s.showing(0, 20)
      server.answer()
      s.showing(1030, 1040)
      // rows 980 to 1090 are wanted: page 10 in view, then page 9 near it
      server.asked.drop(1).map(_.offset) shouldBe Vector(1000, 900)
      s.showing(1080, 1110)
      server.owed.size shouldBe 2
      server.answer()
      server.asked.drop(1).map(_.offset) shouldBe Vector(1000, 900, 1100)
    }
    "never ask for a page the view has left before its turn" in {
      val server = new Server(1900000)
      val s = source(server)
      s.concurrent @= 1
      s.showing(0, 20)
      server.answer()
      s.showing(5000, 5030)
      server.asked.map(_.offset) shouldBe Vector(0, 5000)
      // the view jumps far away while page 50 is out: page 49, queued, is dropped
      s.showing(900000, 900030)
      server.answer()
      server.answer()
      server.asked.map(_.offset) shouldBe Vector(0, 5000, 900000, 899900)
      s.at(4950) shouldBe None
      s.at(5000).map(_.id) shouldBe Some("r5000")
      s.at(900010).map(_.id) shouldBe Some("r900010")
    }
    "start again on a new sort or query, dropping an answer to the one before" in {
      val server = new Server(1000)
      val s = source(server)
      var resets = 0
      s.reset.attach(_ => resets += 1)
      s.showing(0, 20)
      server.answer()
      s.showing(500, 520)
      s.sort @= Some(Sorting("name", ascending = false))
      resets shouldBe 1
      s.searching() shouldBe true
      s.total() shouldBe None
      s.count shouldBe 100
      s.at(0) shouldBe None
      // the new query's first page is asked for at once, sorted; the old page 5 comes late and is dropped
      server.asked.last shouldBe PageRequest(0, 100, Some(Sorting("name", ascending = false)), None)
      server.answerAt(0).offset shouldBe 500
      server.answerAt(0).offset shouldBe 400
      s.at(500) shouldBe None
      s.searching() shouldBe true
      server.answer()
      s.at(0).map(_.id) shouldBe Some("r999")
      s.invalidate()
      resets shouldBe 2
      s.at(0) shouldBe None
    }
    "keep no more than its bound of pages, letting go of those out of view longest" in {
      val server = new Server(1900000)
      val s = source(server, maxPages = 5)
      s.showing(0, 20)
      server.answerAll()
      (1 to 60).foreach { step =>
        s.showing(step * 30000, step * 30000 + 30)
        server.answerAll()
        s.kept should be <= 5 * 100
      }
      s.here.size shouldBe s.kept
      s.at(0) shouldBe None
      s.indexOf("r0") shouldBe -1
      s.at(60 * 30000).map(_.id) shouldBe Some("r1800000")
      // a page let go is asked for again when it shows again
      s.showing(0, 20)
      server.asked.last.offset shouldBe 0
      server.answerAll()
      s.at(0).map(_.id) shouldBe Some("r0")
    }
    "keep a failed page's error until a retry asks for it again" in {
      val server = new Server(1000)
      val s = source(server)
      s.showing(0, 20)
      server.answer(failing = Some("The server is down"))
      s.error() shouldBe Some("The server is down")
      s.searching() shouldBe true
      s.showing(0, 20)
      server.owed shouldBe empty
      s.retry()
      s.error() shouldBe None
      server.asked.map(_.offset) shouldBe Vector(0, 0)
      server.answer()
      s.count shouldBe 1000
    }
    "load more in order by cursor, a page of placeholders after what has come" in {
      val server = new Server(250, cursors = true, counts = false)
      val s = source(server, paging = Paging.InOrder)
      s.showing(0, 20)
      server.answer()
      s.count shouldBe 200
      s.total() shouldBe None
      // page 2 cannot be reached before page 1 says where it starts
      s.showing(150, 199)
      server.asked.map(_.cursor) shouldBe Vector(None, Some("after100"))
      server.answer()
      s.count shouldBe 300
      s.showing(250, 299)
      server.asked.last shouldBe PageRequest(200, 100, None, Some("after200"))
      server.answer()
      // the last page has no cursor after it: the rows end
      s.count shouldBe 250
      s.at(249).map(_.id) shouldBe Some("r249")
    }
    "load more by offset where the server gives no cursor, ending at a short page" in {
      val server = new Server(150, counts = false)
      val s = source(server, paging = Paging.InOrder)
      s.showing(0, 20)
      server.answer()
      s.count shouldBe 200
      s.showing(120, 160)
      server.asked.last shouldBe PageRequest(100, 100, None, None)
      server.answer()
      s.count shouldBe 150
    }
    "put a changed row in place of its own" in {
      val server = new Server(1000)
      val s = source(server)
      var changes = 0
      s.showing(0, 20)
      server.answer()
      s.changed.attach(_ => changes += 1)
      s.replace(Row("r7", "renamed"))
      s.at(7).map(_.name) shouldBe Some("renamed")
      changes shouldBe 1
      s.replace(Row("r700", "not here"))
      changes shouldBe 1
    }
    "read what stands for a place, and nothing else" in {
      PagedSource.placeOf(PagedSource.placeKey(1234567)) shouldBe Some(1234567)
      PagedSource.placeOf("1234") shouldBe None
      PagedSource.isPlace("r5") shouldBe false
    }
  }
}

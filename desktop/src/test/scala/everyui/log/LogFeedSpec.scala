package everyui.log

import everyui.log.LogWire.given
import everyui.FxSpec
import fabric.rw.*
import rapid.Task

/** A server's log streamed into a view, over a transport faked in the spec: the server's backlog (`LogBacklog`)
  * kept, replayed after a seq and answering `LogSince`; a new session's backlog shown before what arrived while it was
  * asked for; a resumed session's replay; what the server no longer keeps marked; a server that started again; and
  * the wire types through their codecs. */
class LogFeedSpec extends FxSpec {
  private def texts(v: LogView): Vector[String] = fx(shown(v))

  // (on the FX thread)
  private def shown(v: LogView): Vector[String] = {
    v.settle()
    (0 until v.list.count).toVector.flatMap(i => v.list.itemAt(i)).map(_.text)
  }

  // a server, and a client asking it through a transport that answers when the spec says
  private class Wire(keep: Int) {
    var backlog = new LogBacklog(keep)
    var asked: List[Long] = Nil
    var answer: Option[rapid.task.Completable[LogBatch]] = None
    // answering at once, rather than when the spec says
    var auto = false
    def ask(after: Long): Task[LogBatch] = if (auto) {
      asked = after :: asked
      Task(backlog.since("demo", after))
    } else {
      asked = after :: asked
      val c = Task.completable[LogBatch]
      answer = Some(c)
      c
    }
    def answerNow(): Unit = answer.foreach(c => c.success(backlog.since("demo", asked.head)))
    // the server sends an event: kept, and its seq given
    def send(feed: LogFeed, text: String, deliver: Boolean = true): Long = {
      val seq = backlog.append("demo", LogEvent.of(text, Some(LogLevel.Info))).sync()
      if (deliver) fx(feed.event(seq, LogEvent.of(text, Some(LogLevel.Info))))
      seq
    }
  }

  "A log feed" should {
    "show a new session's backlog before what arrived meanwhile, each once" in {
      val v = fx(new LogView())
      val wire = new Wire(100)
      val feed = new LogFeed(v, after => wire.ask(after))
      (1 to 5).foreach(i => wire.send(feed, "before " + i, deliver = false))
      fx(feed.connected(false))
      wire.asked shouldBe List(0L)
      // live while the backlog is asked for: held
      wire.send(feed, "live 6")
      texts(v) shouldBe Vector()
      wire.answerNow()
      eventually(v.lastSeq == 6L) shouldBe true
      texts(v) shouldBe Vector("before 1", "before 2", "before 3", "before 4", "before 5", "live 6")
      // a resumed session: the server replays from its log what was missed, and a seq shown already is not shown again
      fx(feed.connected(true))
      wire.send(feed, "live 7")
      wire.backlog.replay("demo", 5L).sync().foreach { case (seq, e) => fx(feed.event(seq, e)) }
      texts(v) shouldBe Vector("before 1", "before 2", "before 3", "before 4", "before 5", "live 6", "live 7")
      fx(v.dispose())
    }

    "mark what the server no longer keeps, after a drop and on a new session" in {
      val v = fx(new LogView())
      val wire = new Wire(10)
      val feed = new LogFeed(v, after => wire.ask(after))
      fx(feed.connected(false))
      wire.answerNow()
      (1 to 3).foreach(i => wire.send(feed, "line " + i))
      eventually(v.lastSeq == 3L) shouldBe true
      // dropped while 20 more were sent: the replay reaches back 10, so 10 are missed
      (4 to 23).foreach(i => wire.send(feed, "line " + i, deliver = false))
      fx(feed.connected(true))
      wire.backlog.replay("demo", 3L).sync().foreach { case (seq, e) => fx(feed.event(seq, e)) }
      val after = texts(v)
      after.take(4) shouldBe Vector("line 1", "line 2", "line 3", LogWords.missed(10L))
      after.drop(4) shouldBe (14 to 23).map(i => "line " + i).toVector
      // a session the server let go: what it asks for after its last is past what is kept
      (24 to 40).foreach(i => wire.send(feed, "line " + i, deliver = false))
      fx(feed.connected(false))
      wire.asked.head shouldBe 23L
      wire.answerNow()
      eventually(v.lastSeq == 40L) shouldBe true
      texts(v).drop(14) shouldBe Vector(LogWords.missed(7L)) ++ (31 to 40).map(i => "line " + i)
      fx(v.dispose())
    }

    "mark a server that started again, and show its lines from 1" in {
      val v = fx(new LogView())
      val wire = new Wire(100)
      val feed = new LogFeed(v, after => wire.ask(after))
      fx(feed.connected(false))
      wire.answerNow()
      (1 to 3).foreach(i => wire.send(feed, "old " + i))
      eventually(v.lastSeq == 3L) shouldBe true
      // started again: a new log, numbered from 1
      Thread.sleep(2L)
      wire.backlog = new LogBacklog(100)
      wire.send(feed, "new 1", deliver = false)
      wire.auto = true
      fx(feed.connected(false))
      eventually(shown(v).lastOption.contains("new 1")) shouldBe true
      texts(v) shouldBe Vector("old 1", "old 2", "old 3", LogWords.restarted, "new 1")
      fx(v.dispose())
    }

    "keep a server's log: numbered, timed, replayed after a seq, the oldest let go" in {
      val logs = new LogBacklog(3)
      (1 to 5).foreach(i => logs.append("a", LogEvent(0L, 0L, "", "a" + i)).sync() shouldBe i.toLong)
      logs.append("b", LogEvent.of("b1")).sync() shouldBe 1L
      logs.replay("a", 0L).sync().map(_._1) shouldBe List(3L, 4L, 5L)
      logs.replay("a", 4L).sync().map(_._2.text) shouldBe List("a5")
      logs.replay("a", 4L).sync().head._2.at should be > 0L
      val batch = logs.since("a", 1L)
      batch.first shouldBe 3L
      batch.last shouldBe 5L
      batch.events.map(_.seq) shouldBe List(3L, 4L, 5L)
      logs.answer("a", LogSince(4L, "a").json).sync().as[LogBatch].events.map(_.text) shouldBe List("a5")
    }

    "write and read its wire types" in {
      val e = LogEvent(7L, 1790000000000L, "WARN", "\u001b[33mslow\u001b[0m\nsecond line", "loader")
      e.json.as[LogEvent] shouldBe e
      e.logLevel shouldBe Some(LogLevel.Warn)
      LogEvent(1L, 0L, "", "x").logLevel shouldBe None
      val b = LogBatch(List(e, e.copy(seq = 8L)), 7L, 8L, "123")
      b.json.as[LogBatch] shouldBe b
      LogSince(5L, "demo").json.as[LogSince] shouldBe LogSince(5L, "demo")
      LogSubscriber("demo").json.as[LogSubscriber] shouldBe LogSubscriber("demo")
    }
  }
}

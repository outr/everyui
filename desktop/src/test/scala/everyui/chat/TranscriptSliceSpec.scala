package everyui.chat

import everyui.focus.Focus
import everyui.list.VirtualList
import everyui.{FxSpec, ui}
import reactify.*

/** A conversation's first screen made a slice at a time (`VirtualList.slice`, Android's way): the newest entry first,
  * drawn at the bottom, the rest of the screen in the frames after, the bottom held where it is as each comes, every
  * entry in view made once and none at a size it is not shown at; and made whole at once where rows are not sliced
  * (every other platform). Made as an app makes one: the view made, sized and then put on the screen. */
class TranscriptSliceSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private def history(n: Int): Vector[ChatEntry] = (0 until n).toVector.map { i =>
    if (i % 3 == 2) ChatEntry.message("m" + i, me, self = true, "Thanks, go ahead (" + i + ").")
    else ChatEntry.message("m" + i, ada, self = false, "## Step " + i + "\n\nThe socket drops after a minute idle:" +
      "\n\n" +
      "- a heartbeat every 25 s\n- a backoff up to 30 s\n\n```scala\nval delay = math.min(max, base * 2.0)\n```\n\n" +
      "I ran the tests again and they pass.")
  }

  // a phone-sized view over `n` entries, each message's words counted by the width they were made for
  // (with the rows that showed an entry and the newest one's bottom gap as the turn that showed it ended)
  private def open(n: Int): (ChatView, scala.collection.mutable.ArrayBuffer[(String, Double)], Int, Double) = {
    val made = scala.collection.mutable.ArrayBuffer.empty[(String, Double)]
    val chat = fx {
      ui.children.clear()
      val c = new ChatView(Var(history(n)), render = (text, widest) => {
        made += ((text().linesIterator.next(), widest()))
        MessageMarkdown.render(text, widest)
      })
      c.size.width @= 412.0
      c.size.height @= 870.0
      ui.children += c
      (c, handed(c), bottomGap(c))
    }
    (chat._1, made, chat._2, chat._3)
  }

  private def close(chat: ChatView): Unit = fx {
    ui.children.clear()
    chat.dispose()
    Focus.focused @= None
  }

  // the rows that show an entry, and the newest entry's row's bottom, against the list's height
  private def handed(chat: ChatView): Int = chat.transcript.list.rows.count(r => r.visible() && r.item().nonEmpty)
  private def bottomGap(chat: ChatView): Double = {
    val list = chat.transcript.list
    list.rowAt(list.count - 1).filter(_.visible()).map(r => list.size.height() - r.position.top() - r.size.height())
      .getOrElse(-1.0)
  }
  private def filled(chat: ChatView): Boolean = {
    val list = chat.transcript.list
    val shown = list.indexAt(list.offset()) to list.indexAt(list.offset() + list.size.height())
    shown.forall(i => list.rowAt(i).exists(_.item().nonEmpty))
  }

  "A conversation whose rows are sliced" should {
    "make its newest entry first, at the bottom, and the rest of the screen in the frames after, the bottom held" in {
      fx(VirtualList.slice @= Some(0.0))
      try {
        val (chat, made, atFirst, gapFirst) = open(60)
        try {
          // the turn it was shown in: the newest entry alone, its bottom at the list's
          atFirst shouldBe 1
          gapFirst shouldBe 0.0 +- 1.0
          made.head._1 shouldBe "Thanks, go ahead (59)."
          // then a row a frame, the bottom held each turn, until the screen is full
          var turns = 0
          var gaps = Vector.empty[Double]
          while (!fx(filled(chat)) && turns < 2000) {
            Thread.sleep(2L)
            gaps = gaps :+ fx(bottomGap(chat))
            turns += 1
          }
          fx(filled(chat)) shouldBe true
          gaps.forall(g => math.abs(g) <= 1.0) shouldBe true
          fx(handed(chat)) should be > 2
          // the overscan made too, and each entry's words made once, at the width it is shown at
          eventually {
            val list = chat.transcript.list
            handed(chat) > list.indexAt(list.offset() + list.size.height()) - list.indexAt(list.offset()) + 1
          } shouldBe true
          made.map(_._1).distinct.size shouldBe made.size
          made.map(_._2).distinct shouldBe Seq(fx(chat.transcript.widest()) - Transcript.padX * 2.0)
        } finally close(chat)
      } finally fx(VirtualList.slice @= None)
    }
  }

  "A conversation whose rows are not sliced" should {
    "make its whole first screen in the turn it is shown, each entry only at the width it is shown at" in {
      fx(VirtualList.slice.get) shouldBe None
      val (chat, made, _, _) = open(60)
      try {
        fx(filled(chat)) shouldBe true
        fx(bottomGap(chat)) shouldBe 0.0 +- 1.0
        made.map(_._2).distinct shouldBe Seq(fx(chat.transcript.widest()) - Transcript.padX * 2.0)
      } finally close(chat)
    }
  }
}

package everyui.trickplay

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A BIF's index read from its bytes (BifIndex), and the frame for a place in the video, at its edges. */
class BifIndexSpec extends AnyWordSpec with Matchers {
  private val frames = (0 until 10).map(Bifs.frame)
  private val bif = Bifs(frames, interval = 2000)

  "A BIF's index" should {
    "be read from its header and entries" in {
      val ix = BifIndex.parse(bif).get
      ix.count shouldBe 10
      ix.unitMs shouldBe 2000.0
      ix.times shouldBe (0 until 10).toVector
      ix.offsets.size shouldBe 11
      ix.start(0) shouldBe 64 + 8 * 11
      (0 until 10).foreach { i =>
        bif.slice(ix.start(i), ix.start(i) + ix.length(i)).toSeq shouldBe frames(i).toSeq
      }
    }
    "say how much of the file holds the index, from the header alone" in {
      BifIndex.indexEnd(bif.take(64)) shouldBe 64 + 8 * 11
      BifIndex.parse(bif.take(64)) shouldBe None
      BifIndex.parse(bif.take(64 + 8 * 11)).map(_.count) shouldBe Some(10)
    }
    "take an interval of 0 as a second" in {
      BifIndex.parse(Bifs(frames, interval = 0)).get.unitMs shouldBe 1000.0
    }
    "refuse what is not a BIF, or runs backwards" in {
      BifIndex.indexEnd("not a bif at all".getBytes("UTF-8")) shouldBe -1
      BifIndex.parse(new Array[Byte](200)) shouldBe None
      val backwards = bif.clone()
      // frame 3's offset past frame 4's
      backwards(64 + 8 * 3 + 4) = 0xff.toByte
      backwards(64 + 8 * 3 + 5) = 0x7f.toByte
      BifIndex.parse(backwards) shouldBe None
    }
    "read its words little-endian and unsigned past 2^31 as negative" in {
      BifIndex.word(Array(1, 2, 3, 4).map(_.toByte), 0) shouldBe 0x04030201
      BifIndex.word(Array(0xff, 0xff, 0xff, 0xff).map(_.toByte), 0) shouldBe -1
    }
    "find the frame for a place: the last one not past it" in {
      val ix = BifIndex.parse(bif).get
      ix.frameAt(0.0) shouldBe 0
      ix.frameAt(1.999) shouldBe 0
      ix.frameAt(2.0) shouldBe 1
      ix.frameAt(9.0) shouldBe 4
      ix.frameAt(18.0) shouldBe 9
    }
    "keep to its first and last frames past its ends" in {
      val ix = BifIndex.parse(bif).get
      ix.frameAt(-5.0) shouldBe 0
      ix.frameAt(10000.0) shouldBe 9
    }
    "follow timestamps that are not evenly spaced" in {
      val ix = BifIndex.parse(Bifs(frames.take(4), interval = 1000, times = Some(Seq(5, 6, 20, 21)))).get
      ix.frameAt(0.0) shouldBe 0
      ix.frameAt(6.5) shouldBe 1
      ix.frameAt(19.9) shouldBe 1
      ix.frameAt(20.0) shouldBe 2
      ix.frameAt(99.0) shouldBe 3
    }
    "have no frame where it has none" in {
      val ix = BifIndex.parse(Bifs(Nil)).get
      ix.count shouldBe 0
      ix.frameAt(3.0) shouldBe -1
    }
  }
}

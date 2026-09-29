package everyui.store

import fabric.rw.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Each codec's text, read back as it went in, and text it cannot read answered with None. */
class CodecSpec extends AnyWordSpec with Matchers {
  private def roundTrip[T](value: T)(using codec: Codec[T]): Option[T] = codec.decode(codec.encode(value))

  "The plain codecs" should {
    "keep a String as it is, empty or not" in {
      Codec[String].encode("-name") shouldBe "-name"
      roundTrip("-name") shouldBe Some("-name")
      roundTrip("") shouldBe Some("")
    }
    "keep numbers, and refuse text that is not one" in {
      roundTrip(42) shouldBe Some(42)
      roundTrip(-7L) shouldBe Some(-7L)
      roundTrip(Long.MaxValue) shouldBe Some(Long.MaxValue)
      roundTrip(1.5) shouldBe Some(1.5)
      Codec[Int].decode("forty") shouldBe None
      Codec[Int].decode("99999999999") shouldBe None
      Codec[Double].decode("") shouldBe None
    }
    "keep a Boolean as true or false, and nothing else" in {
      Codec[Boolean].encode(true) shouldBe "true"
      roundTrip(false) shouldBe Some(false)
      Codec[Boolean].decode("yes") shouldBe None
    }
    "tell None, Some of an empty String and Some(None) apart" in {
      roundTrip(Option.empty[String]) shouldBe Some(None)
      roundTrip(Option("")) shouldBe Some(Some(""))
      roundTrip(Option("none")) shouldBe Some(Some("none"))
      roundTrip(Option(Option.empty[Int])) shouldBe Some(Some(None))
      roundTrip(Option(Option(3))) shouldBe Some(Some(Some(3)))
      Codec[Option[Int]].decode("some:x") shouldBe None
      Codec[Option[Int]].decode("3") shouldBe None
    }
  }

  "A named codec" should {
    "keep an enum by name, and read a name no case has as None" in {
      val codec = Codec.named(CodecSpec.Sort.values.toList)(_.toString)
      codec.encode(CodecSpec.Sort.Newest) shouldBe "Newest"
      codec.decode("Oldest") shouldBe Some(CodecSpec.Sort.Oldest)
      codec.decode("Alphabetical") shouldBe None
    }
  }

  "A codec of two functions" should {
    "write and read by them" in {
      val codec = Codec.from[List[Int]](_.mkString(","), t => Some(t.split(",").toList.filter(_.nonEmpty).map(_.toInt)))
      codec.decode(codec.encode(List(1, 2, 3))) shouldBe Some(List(1, 2, 3))
    }
  }

  "A fabric codec" should {
    "keep a case class as compact JSON, and read JSON its RW cannot as None" in {
      val codec = FabricCodec[CodecSpec.Layout]
      val layout = CodecSpec.Layout("wide", List(3, 1))
      codec.encode(layout) shouldBe """{"name":"wide","widths":[3,1]}"""
      codec.decode(codec.encode(layout)) shouldBe Some(layout)
      codec.decode("""{"title":"wide"}""") shouldBe None
      codec.decode("not json") shouldBe None
    }
  }
}

object CodecSpec {
  enum Sort {
    case Newest, Oldest
  }

  case class Layout(name: String, widths: List[Int])

  object Layout {
    given rw: RW[Layout] = RW.gen
  }
}

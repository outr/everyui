package everyui.compiler

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Long lines of a Roku program spread over several (RokuEmitter.wrap): Roku's static analysis warns of every line of
  * 1,000 characters or more, and a large app's channel had them by the hundred (tables of words, emoji, a picture as
  * base64, a long string put together in Scala). What each line means must be as it was. */
class WrapSpec extends AnyWordSpec with Matchers {
  private def longest(code: String): Int = code.split("\n").map(_.length).max

  "RokuEmitter.wrap" should {
    "leave a program with no long line as it is" in {
      val code = "function f()\nreturn [1,2,3]\nend function"
      RokuEmitter.wrap(code, 40) shouldBe code
    }
    "spread an array literal over lines after its commas, filled to the limit" in {
      val items = (1 to 40).map(i => "Tuple2(\"key" + i + "\",\"value " + i + "\")")
      val line = "x=_mapFromPairs([" + items.mkString(",") + "])"
      val wrapped = RokuEmitter.wrap(line, 120)
      longest(wrapped) should be <= 120
      wrapped.split("\n").length should be > 1
      wrapped.replace("\n", "") shouldBe line
      // never inside a call's parentheses
      wrapped.split("\n").foreach(l => l should not endWith "\"key1\",")
    }
    "spread an associative array literal, and one after `return`" in {
      val fields = (1 to 30).map(i => "field" + i + ": \"" + ("v" * 10) + "\"").mkString(",")
      val line = "return { " + fields + " }"
      val wrapped = RokuEmitter.wrap(line, 100)
      longest(wrapped) should be <= 100
      wrapped.replace("\n", "") shouldBe line
    }
    "not break an index or a call's arguments" in {
      val line = "x=f(" + (1 to 60).map(i => "a[" + i + "]").mkString(",") + ")"
      RokuEmitter.wrap(line, 80) shouldBe line
    }
    "leave a single-line if as it is" in {
      val line = "if ok then x=[" + (1 to 60).map(_.toString).mkString(",") + "]"
      RokuEmitter.wrap(line, 40) shouldBe line
    }
    "not begin a line with a call and then a method call" in {
      val line = "x=[" + (1 to 30).map(i => "Foo_new(" + i + ").bar(" + i + ")").mkString(",") + "]"
      val wrapped = RokuEmitter.wrap(line, 60)
      wrapped.replace("\n", "") shouldBe line
      wrapped.split("\n").drop(1).foreach(l => l should not startWith "Foo_new")
    }
    "put a long string together from pieces, doubled quotes and surrogate pairs kept whole" in {
      val text = ("ab\"\"c😀" * 40)
      val line = "x=\"" + text + "\""
      val wrapped = RokuEmitter.wrap(line, 50)
      wrapped should startWith ("x=RtCat([")
      longest(wrapped) should be <= 50
      val pieces = "\"((?:[^\"]|\"\")*)\"".r.findAllMatchIn(wrapped).map(_.group(1)).toList
      pieces.mkString shouldBe text
      pieces.foreach { p =>
        p.replace("\"\"", "") should not contain '"'
        Character.isHighSurrogate(p.last) shouldBe false
      }
    }
    "split a long sum into statements adding up from the left" in {
      val terms = (1 to 40).map(i => "_str(\"part " + i + "\")")
      val nested = terms.tail.foldLeft(terms.head)((acc, t) => "(" + acc + " + " + t + ")")
      val wrapped = RokuEmitter.wrap("return " + nested, 120)
      longest(wrapped) should be <= 120
      val lines = wrapped.split("\n").toList
      lines.head should startWith ("__cat=_str(\"part 1\") + _str(\"part 2\")")
      lines.tail.init.foreach(l => l should startWith ("__cat=__cat + "))
      lines.last shouldBe "return __cat"
      // every term, in order, once
      "part \\d+".r.findAllIn(wrapped).toList shouldBe (1 to 40).map(i => "part " + i).toList
    }
    "split a long sum given to a name, a field or an index" in {
      val sum = (1 to 40).map(i => "\"" + ("w" * 20) + i + "\"").mkString(" + ")
      val wrapped = RokuEmitter.wrap("ga[\"__mv_X_large\"]=" + sum, 200)
      longest(wrapped) should be <= 200
      wrapped.split("\n").last shouldBe "ga[\"__mv_X_large\"]=__cat"
    }
    "leave a sum with another operator outside its brackets" in {
      val line = "x=" + (1 to 60).map(i => "a" + i).mkString(" + ") + " * 2"
      RokuEmitter.wrap(line, 80) shouldBe line
    }
  }
}

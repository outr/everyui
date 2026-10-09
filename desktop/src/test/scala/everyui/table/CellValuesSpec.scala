package everyui.table

import everyui.component.types.TextAlign
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What a table's cells are worth to a sort, and the table as text: numbers through the separators, currencies, signs,
  * percentages and units written around them; dates in their common forms; the orders a column sorts in; and the rows
  * as tab-separated lines, CSV and Markdown, quoted and escaped where a cell needs it. */
class CellValuesSpec extends AnyWordSpec with Matchers {
  import CellValues.*

  "A cell's number" should {
    "read through thousands separators, decimal commas, currencies, signs, percentages and units" in {
      number("1,234.5") shouldBe Some(1234.5)
      number("1.234,5") shouldBe Some(1234.5)
      number("1 234 567") shouldBe Some(1234567.0)
      number("1,234") shouldBe Some(1234.0)
      number("12,5") shouldBe Some(12.5)
      number("$1,299.00") shouldBe Some(1299.0)
      number("€1.149,50") shouldBe Some(1149.5)
      number("-€3.50") shouldBe Some(-3.5)
      number("−7") shouldBe Some(-7.0)
      number("(42)") shouldBe Some(-42.0)
      number("(3.0%)") shouldBe Some(-3.0)
      number("+4.5%") shouldBe Some(4.5)
      number("12 ms") shouldBe Some(12.0)
      number("2.5k") shouldBe Some(2500.0)
      number("1.5 GB") shouldBe Some(1.5e9)
      number("980 MB") shouldBe Some(9.8e8)
      number("US$ 12") shouldBe Some(12.0)
      number("1'000") shouldBe Some(1000.0)
    }
    "not take dates, times, versions or words for numbers" in {
      number("2026-10-09") shouldBe None
      number("12:30") shouldBe None
      number("1.2.3") shouldBe None
      number("Item 12 of 40") shouldBe None
      number("free") shouldBe None
      number("") shouldBe None
      number("1,23,4") shouldBe None
    }
  }

  "A cell's date" should {
    "read ISO's dates and times, the slashed and dotted orders and English month names, to the same second" in {
      val day = date("2026-10-09").get
      day shouldBe 20735L * 86400L
      date("2026/10/09") shouldBe Some(day)
      date("10/9/2026") shouldBe Some(day)
      date("09.10.2026") shouldBe Some(day)
      date("Oct 9, 2026") shouldBe Some(day)
      date("9 October 2026") shouldBe Some(day)
      date("Fri, 9 Oct 2026") shouldBe Some(day)
      date("2026-10-09T14:30:00Z") shouldBe Some(day + 14 * 3600 + 30 * 60)
      date("2026-10-09T14:30:00.250+02:00") shouldBe Some(day + 12 * 3600 + 30 * 60)
      date("2026-10-09 2:30 pm") shouldBe Some(day + 14 * 3600 + 30 * 60)
      date("13/10/2026") shouldBe date("2026-10-13")
      date("October 2026") shouldBe date("2026-10-01")
      date("12/28/25") shouldBe date("2025-12-28")
    }
    "not take numbers, impossible days or words for dates" in {
      date("2026") shouldBe None
      date("2026-02-30") shouldBe None
      date("Marketing 2026") shouldBe None
      date("3.1%") shouldBe None
      date("Tuesday plans") shouldBe None
    }
  }

  "A column" should {
    "be found to hold dates, numbers or words by every cell that says something" in {
      infer(Vector("2026-10-09", "Oct 1, 2025", "—", "")) shouldBe ColumnKind.Date
      infer(Vector("$1,299.00", "€1.149,50", "n/a", "£849")) shouldBe ColumnKind.Number
      infer(Vector("12", "free", "3")) shouldBe ColumnKind.Text
      infer(Vector("", "-")) shouldBe ColumnKind.Text
    }
    "sort numbers by value and dates by time, up and down, with nothing at the end both ways and ties as they came" in {
      val numbers = Vector("10", "$9.50", "—", "1,000", "-2", "9.5")
      order(numbers, ColumnKind.Number, ascending = true) shouldBe Vector(4, 1, 5, 0, 3, 2)
      order(numbers, ColumnKind.Number, ascending = false) shouldBe Vector(3, 0, 1, 5, 4, 2)
      val dates = Vector("Mar 3, 2026", "12/28/2025", "2026-06-30", "", "9 October 2026", "2025-07-01")
      order(dates, ColumnKind.Date, ascending = true) shouldBe Vector(5, 1, 0, 2, 4, 3)
      order(dates, ColumnKind.Date, ascending = false) shouldBe Vector(4, 2, 0, 1, 5, 3)
    }
    "sort words as the language orders them, whatever their case" in {
      val words = Vector("banana", "Apple", "cherry", "apple", "Éclair", "")
      val up = order(words, ColumnKind.Text, ascending = true).map(words)
      up.take(2).map(_.toLowerCase) shouldBe Vector("apple", "apple")
      up.indexOf("Éclair") should be > up.indexOf("cherry")
      up.last shouldBe ""
      order(words, ColumnKind.Text, ascending = false).map(words).head shouldBe "Éclair"
    }
  }

  "A table as text" should {
    val header = Vector("Name", "Notes")
    val rows = Vector(Vector("Ada", "says \"hi\""), Vector("Grace, Admiral", "two\nlines"), Vector("Alan", "tab\there"),
      Vector("\"quoted\"", "a | bar"))
    "be tab-separated lines under the header, a cell with a tab, a line break or a leading quote quoted" in {
      TableText.tsv(header, rows) shouldBe
        "Name\tNotes\nAda\tsays \"hi\"\nGrace, Admiral\t\"two\nlines\"\nAlan\t\"tab\there\"\n\"\"\"quoted\"\"\"\ta | bar"
    }
    "be CSV as RFC 4180 has it: CR LF after each line, a comma, quote or line break quoted, quotes doubled" in {
      TableText.csv(header, rows) shouldBe
        "Name,Notes\r\nAda,\"says \"\"hi\"\"\"\r\n\"Grace, Admiral\",\"two\nlines\"\r\nAlan,tab\there\r\n" +
          "\"\"\"quoted\"\"\",a | bar\r\n"
    }
    "be a Markdown table aligned as its columns are, bars escaped and line breaks <br>" in {
      TableText.markdown(header, rows, Vector(TextAlign.Start, TextAlign.End)) shouldBe
        "| Name | Notes |\n|---|---:|\n| Ada | says \"hi\" |\n| Grace, Admiral | two<br>lines |\n| Alan | tab\there |\n" +
          "| \"quoted\" | a \\| bar |\n"
    }
  }
}

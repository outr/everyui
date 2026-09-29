package everyui.app

import everyui.async.Task
import everyui.paging.{Page, PageRequest}
import everyui.render.Platform

/** The paged table demo's made-up server: 1,902,345 entities, none of them held, each made from its number as it is
  * asked for, in whichever order a column sorts them, after `latency` seconds. Words narrow the entities to a quarter
  * of them for each letter typed (and so to none past ten). With `flaky` on, every seventh request fails. A status
  * changed is kept. */
object EntityServer {
  val total: Int = 1902345
  val statuses: List[String] = List("Active", "Inactive", "Pending", "Review")
  /** How long an answer takes, in seconds. */
  var latency: Double = 0.15
  var flaky: Boolean = false

  // two-letter syllables in alphabetical order, so a name made from a number's digits sorts as the number does
  private val syllables = Vector("ba", "da", "fe", "ga", "ki", "la", "lo", "ma", "mi", "na", "no", "ra", "ri", "sa", "se",
    "ta", "to", "va", "ve", "zo")
  private val suffixes = Vector("", " LLC", " Trust", "", " Estate", " Inc", "")
  private val counties = Vector("Adams", "Big Horn", "Campbell", "Carbon", "Converse", "Crook", "Fremont", "Goshen",
    "Hot Springs", "Johnson", "Laramie", "Lincoln", "Natrona", "Niobrara", "Park", "Platte", "Sheridan", "Sublette",
    "Sweetwater", "Teton", "Uinta", "Washakie", "Weston")
  private var edited: Map[String, String] = Map.empty
  private var asked = 0

  /** How many entities `words` find. */
  def matching(words: String): Int = {
    val w = words.trim
    if (w.isEmpty) total else total >> math.min(30, w.length * 2)
  }

  /** A page for `request`, among the entities `words` find, after `latency`. */
  def fetch(words: String, request: PageRequest): Task[Either[String, Page[DemoEntity]]] = Task.async { done =>
    asked += 1
    val failing = flaky && asked % 7 == 0
    Platform.after(latency, () => done(
      if (failing) Left("The server did not answer (a made-up failure, one request in seven)")
      else Right(page(words, request))))
  }

  /** `entity`'s status set to `status` on the server, after `latency`: the entity as it now is. */
  def save(entity: DemoEntity, status: String): Task[DemoEntity] = Task.async { done =>
    Platform.after(latency, () => {
      edited = edited.updated(entity.id, status)
      done(entity.copy(status = status))
    })
  }

  def page(words: String, request: PageRequest): Page[DemoEntity] = {
    val m = matching(words)
    val end = math.min(m, request.offset + request.limit)
    val rows = (request.offset until end).map(i => entity(numberAt(i, m, request), m)).toVector
    Page(rows, Some(m))
  }

  // the number of the entity at place `i` of `m`, in the order the request sorts them
  private def numberAt(i: Int, m: Int, request: PageRequest): Int = {
    val place = if (request.sort.exists(!_.ascending)) m - 1 - i else i
    request.sort.map(_.column) match {
      case Some("name") => place
      case Some("records") => unrank(3001, place, m)
      case Some("county") => unrank(5003, place, m)
      case Some("updated") => unrank(7001, place, m)
      case _ => unrank(9011, place, m)
    }
  }

  private def entity(n: Int, m: Int): DemoEntity = {
    val id = "E" + (1000000 + n).toString
    val name = word(n / 400) + " " + word(n % 400) + suffixes(n % suffixes.size)
    DemoEntity(id, name, edited.getOrElse(id, statuses((n * 7 + 3) % statuses.size)),
      (rank(3001, n, m) * 4000L / m).toInt, counties((rank(5003, n, m) * counties.size / m).toInt),
      16000 + (rank(7001, n, m) * 4000L / m).toInt, n % 13 == 0)
  }

  // `n`'s syllables, most significant first, capitalised
  private def word(n: Int): String = {
    var digits = List.empty[String]
    var rest = n
    var count = 0
    while (count < 3 || rest > 0) {
      digits = syllables(rest % 20) :: digits
      rest /= 20
      count += 1
    }
    val w = digits.mkString
    w.substring(0, 1).toUpperCase + w.substring(1)
  }

  /** `days` since 1970 as a date. */
  def date(days: Int): String = {
    val z = days + 719468
    val era = z / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + (if (month <= 2) 1 else 0)
    f"$year%04d-$month%02d-$d%02d"
  }

  // a shuffle of 0 until m, and its undoing: what place `n` has by a column, and whose place `r` is
  private def rank(a: Int, n: Int, m: Int): Long = (coprime(a, m) * n + 7L) % m

  private def unrank(a: Int, r: Int, m: Int): Int = {
    val back = inverse(coprime(a, m), m)
    ((back * (((r - 7L) % m + m) % m)) % m).toInt
  }

  private def coprime(a: Int, m: Int): Long = {
    var c = a.toLong % math.max(1, m)
    if (c == 0L) c = 1L
    while (gcd(c, m.toLong) != 1L) c += 1L
    c
  }

  private def gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

  private def inverse(a: Long, m: Int): Long = {
    var (r0, r1) = (m.toLong, a)
    var (t0, t1) = (0L, 1L)
    while (r1 != 0L) {
      val q = r0 / r1
      val r = r0 - q * r1
      r0 = r1
      r1 = r
      val t = t0 - q * t1
      t0 = t1
      t1 = t
    }
    ((t0 % m) + m) % m
  }
}

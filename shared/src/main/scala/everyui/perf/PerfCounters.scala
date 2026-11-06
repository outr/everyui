package everyui.perf

import everyui.render.Platform

/** Counts of the work behind a frame, for finding what a change of screen costs: off by default, and nothing but a
  * test of `on` where it is off. Turned on (`on = true`, or `perfCounters=1` at launch), it counts, until `reset()`:
  *
  *  - layout: each `LayoutPass` flush, and the most in one frame; each container placed (`placeNow`), by its kind, and
  *    each placed again in the same frame;
  *  - words: each `TextView` fit, and those whose words, limits and room were all as at its last (what a cache of fits
  *    would have saved);
  *  - reads: each size read from the platform (the web's box reads), and those after a write in the same frame, which
  *    make the browser lay the page out there and then;
  *  - children: components a list made (`bindMaking`, `bind`) against those it kept, and parts `replaceMaking` put in
  *    new against those it kept;
  *  - writes: each style written to the page (position, size, colours, opacity, display, the words), and those that
  *    wrote what was there already.
  *
  * A frame is from the first count after one is drawn to the next drawn (`Platform.nextFrame`). `report` is the counts
  * as lines, `json` as an object; on the web `PerfCounters.expose()` gives them as `window.everyuiCounters()` (and
  * `everyuiCountersJson()`, `everyuiCountersReset()`, and `everyuiCountersOn()` to start counting). */
object PerfCounters {
  /** Whether it counts. */
  var on: Boolean = Platform.launchArg("perfCounters") == "1"

  private var flushes = 0
  private var flushesInFrame = 0
  private var mostFlushes = 0
  private var framesFlushedMore = 0
  private var frames = 0
  private var placed = 0
  private var placedAgain = 0
  private var placedBy: Map[String, Int] = Map.empty
  private var againBy: Map[String, Int] = Map.empty
  private var fits = 0
  private var fitsSame = 0
  private var reads = 0
  private var forcedReads = 0
  private var made = 0
  private var kept = 0
  private var partsNew = 0
  private var partsKept = 0
  private var writes = 0
  private var writesSame = 0
  // the frame being counted, and whether anything has been written in it since the page was last laid out
  private[everyui] var frame = 0
  private var open = false
  private var dirty = false

  private def touch(): Unit = if (!open) {
    open = true
    frame += 1
    frames += 1
    Platform.nextFrame(() => close())
  }

  private def close(): Unit = {
    open = false
    dirty = false
    if (flushesInFrame > 1) framesFlushedMore += 1
    flushesInFrame = 0
  }

  /** A `LayoutPass` flush began. */
  def flushed(): Unit = if (on) {
    touch()
    flushes += 1
    flushesInFrame += 1
    if (flushesInFrame > mostFlushes) mostFlushes = flushesInFrame
  }

  /** A container of `kind` was placed, last placed in frame `last`: placed again where that is this one. The frame
    * it is placed in, for its next. */
  def placedOne(kind: String, last: Int): Int = {
    touch()
    // by its kind alone, not what it shows (a menu's row by its label)
    val name = if (kind.contains("(")) kind.substring(0, kind.indexOf("(")) else kind
    placed += 1
    placedBy = placedBy + (name -> (placedBy.getOrElse(name, 0) + 1))
    if (last == frame) {
      placedAgain += 1
      againBy = againBy + (name -> (againBy.getOrElse(name, 0) + 1))
    }
    frame
  }

  /** A `TextView` fit its words; `same` where all it fit by was as at its last. */
  def fit(same: Boolean): Unit = if (on) {
    touch()
    fits += 1
    if (same) fitsSame += 1
  }

  /** A size read from the platform. */
  def read(): Unit = if (on) {
    touch()
    reads += 1
    if (dirty) {
      forcedReads += 1
      // the page laid out for the read: clean until the next write
      dirty = false
    }
  }

  /** A style written to the page; `same` where it was what was there already. */
  def wrote(same: Boolean): Unit = if (on) {
    touch()
    writes += 1
    if (same) writesSame += 1 else dirty = true
  }

  /** A list's children followed its values: `newly` made, `still` kept. */
  def listed(newly: Int, still: Int): Unit = if (on) {
    made += newly
    kept += still
  }

  /** `replaceMaking` put in `newly` parts new and kept `still`. */
  def replaced(newly: Int, still: Int): Unit = if (on) {
    partsNew += newly
    partsKept += still
  }

  /** Every count back to nothing. */
  def reset(): Unit = {
    flushes = 0
    mostFlushes = 0
    framesFlushedMore = 0
    frames = 0
    placed = 0
    placedAgain = 0
    placedBy = Map.empty
    againBy = Map.empty
    fits = 0
    fitsSame = 0
    reads = 0
    forcedReads = 0
    made = 0
    kept = 0
    partsNew = 0
    partsKept = 0
    writes = 0
    writesSame = 0
  }

  /** The counts by name. */
  def counts: List[(String, Int)] = List(
    "frames" -> frames, "flushes" -> flushes, "mostFlushesInAFrame" -> mostFlushes,
    "framesFlushedMoreThanOnce" -> framesFlushedMore, "placed" -> placed, "placedAgainInAFrame" -> placedAgain,
    "fits" -> fits, "fitsUnchanged" -> fitsSame, "reads" -> reads, "readsAfterAWrite" -> forcedReads,
    "listMade" -> made, "listKept" -> kept, "replacedNew" -> partsNew, "replacedKept" -> partsKept,
    "writes" -> writes, "writesUnchanged" -> writesSame)

  // the kinds most placed (and placed again), the most first
  private def most(by: Map[String, Int]): List[(String, Int)] = by.toList.sortBy(e => -e._2).take(12)

  /** The counts as lines, with the kinds of container most placed and most placed again in a frame. */
  def report: String = (counts.map(c => c._1 + ": " + c._2) ++
    List("placed by kind: " + most(placedBy).map(e => e._1 + " " + e._2).mkString(", "),
      "placed again by kind: " + most(againBy).map(e => e._1 + " " + e._2).mkString(", "))).mkString("\n")

  /** The counts as a JSON object, with `placedBy` and `placedAgainBy`. */
  def json: String = {
    def quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    def obj(entries: List[(String, Int)]): String =
      "{" + entries.map(e => quote(e._1) + ":" + e._2.toString).mkString(",") + "}"
    "{" + counts.map(c => quote(c._1) + ":" + c._2.toString).mkString(",") + ",\"placedBy\":" + obj(most(placedBy)) +
      ",\"placedAgainBy\":" + obj(most(againBy)) + "}"
  }

  /** The counts to a check outside the app: `window.everyuiCounters()` as lines, `everyuiCountersJson()` as an
    * object, `everyuiCountersReset()` and `everyuiCountersOn()` (which answer ""), on the web; nothing elsewhere. */
  def expose(): Unit = {
    Platform.expose("everyuiCounters", () => report, json = false)
    Platform.expose("everyuiCountersJson", () => json, json = true)
    Platform.expose("everyuiCountersReset", () => {
      reset()
      ""
    }, json = false)
    Platform.expose("everyuiCountersOn", () => {
      on = true
      ""
    }, json = false)
  }
}

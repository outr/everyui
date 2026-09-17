package everyui.image

/** A QR code for some text, as its modules: `dark(x, y)` for each of `size` by `size`, with no quiet zone. Bytes
  * (printable ASCII, as a link is) at error correction level M, the smallest version from 1 to 10 that holds them
  * (about 210 at most), and the mask that best evens them out.
  *
  * After Project Nayuki's QR Code generator (MIT), in arithmetic rather than bit operations, which a Roku's
  * BrightScript lacks. */
class QrCode(val size: Int, modules: Array[Int]) {
  def dark(x: Int, y: Int): Boolean = modules(y * size + x) == 1
}

object QrCode {
  // Per version (1 to 10), at level M: error correction codewords in each block, and how many blocks
  private val eccPerBlock = List(10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
  private val blocks = List(1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

  // Powers of two in GF(256) (primitive polynomial 0x11D), and their logarithms
  private lazy val exp: Array[Int] = {
    val table = (0 until 256).map(_ => 0).toArray
    var x = 1
    (0 until 255).foreach { i =>
      table(i) = x
      x = x * 2
      if (x >= 256) x = xor(x, 0x11D)
    }
    table
  }
  private lazy val log: Array[Int] = {
    val table = (0 until 256).map(_ => 0).toArray
    (0 until 255).foreach(i => table(exp(i)) = i)
    table
  }

  /** The code for `text`, or None when it is too long for version 10. */
  def encode(text: String): Option[QrCode] = {
    val bytes = ascii(text)
    val version = (1 to 10).find(v => bytes.size * 8 + 4 + countBits(v) <= dataCodewords(v) * 8)
    version.map { v =>
      val codewords = interleave(v, data(v, bytes))
      val size = v * 4 + 17
      val modules = (0 until size * size).map(_ => 0).toArray
      val function = (0 until size * size).map(_ => 0).toArray
      val grid = new Grid(size, modules, function)
      drawFunctionPatterns(grid, v)
      drawCodewords(grid, codewords)
      // the mask that leaves the fewest confusable patterns
      var best = 0
      var bestPenalty = -1
      (0 until 8).foreach { mask =>
        applyMask(grid, mask)
        drawFormatBits(grid, mask)
        val p = penalty(grid)
        if (bestPenalty < 0 || p < bestPenalty) {
          best = mask
          bestPenalty = p
        }
        applyMask(grid, mask)
      }
      applyMask(grid, best)
      drawFormatBits(grid, best)
      new QrCode(size, modules)
    }
  }

  /** The modules while they are drawn, and which of them are the code's fixed patterns. */
  private class Grid(val size: Int, val modules: Array[Int], val function: Array[Int]) {
    def set(x: Int, y: Int, dark: Boolean): Unit = modules(y * size + x) = if (dark) 1 else 0
    def fixed(x: Int, y: Int, dark: Boolean): Unit = {
      set(x, y, dark)
      function(y * size + x) = 1
    }
    def isFixed(x: Int, y: Int): Boolean = function(y * size + x) == 1
    def get(x: Int, y: Int): Int = modules(y * size + x)
  }

  // ---- arithmetic in place of bit operations ----

  private def pow2(n: Int): Int = {
    var p = 1
    (0 until n).foreach(_ => p = p * 2)
    p
  }

  private def bit(x: Int, i: Int): Boolean = (x / pow2(i)) % 2 == 1

  private def xor(a: Int, b: Int): Int = {
    var result = 0
    var place = 1
    var x = a
    var y = b
    while (x > 0 || y > 0) {
      if (x % 2 != y % 2) result = result + place
      x = x / 2
      y = y / 2
      place = place * 2
    }
    result
  }

  private def multiply(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp((log(a) + log(b)) % 255)

  // ---- the data ----

  /** Printable ASCII, from the space on: a character's code is 32 more than where it is here. */
  private val printable: String = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"

  /** The text's bytes: a link is printable ASCII (anything else in one is escaped), and any other character is a
    * question mark. */
  private def ascii(text: String): List[Int] = (0 until text.length).toList.map { i =>
    val at = printable.indexOf(text.substring(i, i + 1))
    if (at < 0) 63 else at + 32
  }

  private def countBits(version: Int): Int = if (version <= 9) 8 else 16

  private def rawModules(version: Int): Int = {
    var result = (16 * version + 128) * version + 64
    if (version >= 2) {
      val align = version / 7 + 2
      result = result - ((25 * align - 10) * align - 55)
      if (version >= 7) result = result - 36
    }
    result
  }

  private def dataCodewords(version: Int): Int =
    rawModules(version) / 8 - eccPerBlock(version - 1) * blocks(version - 1)

  /** The mode, the length, the bytes, a terminator and padding, as codewords. */
  private def data(version: Int, bytes: List[Int]): List[Int] = {
    val bits = new BitBuffer
    bits.add(4, 4)
    bits.add(bytes.size, countBits(version))
    bytes.foreach(b => bits.add(b, 8))
    val capacity = dataCodewords(version) * 8
    bits.add(0, math.min(4, capacity - bits.length))
    bits.add(0, (8 - bits.length % 8) % 8)
    var pad = 0xEC
    while (bits.length < capacity) {
      bits.add(pad, 8)
      pad = if (pad == 0xEC) 0x11 else 0xEC
    }
    bits.bytes
  }

  private class BitBuffer {
    var all: List[Int] = Nil
    def length: Int = all.size
    def add(value: Int, count: Int): Unit = (0 until count).foreach { i =>
      all = all :+ (if (bit(value, count - 1 - i)) 1 else 0)
    }
    def bytes: List[Int] = (0 until all.size / 8).toList.map { i =>
      var value = 0
      all.drop(i * 8).take(8).foreach(b => value = value * 2 + b)
      value
    }
  }

  /** The data split into blocks, each with its error correction, read across the blocks in turn. */
  private def interleave(version: Int, data: List[Int]): List[Int] = {
    val count = blocks(version - 1)
    val ecc = eccPerBlock(version - 1)
    val raw = rawModules(version) / 8
    val shortCount = count - raw % count
    val shortLength = raw / count
    val divisor = generator(ecc)
    var at = 0
    val parts = (0 until count).toList.map { i =>
      val length = shortLength - ecc + (if (i < shortCount) 0 else 1)
      val block = data.drop(at).take(length)
      at = at + length
      (block, remainder(block, divisor))
    }
    val longest = parts.map(_._1.size).max
    val interleaved = (0 until longest).toList.flatMap(i => parts.filter(_._1.size > i).map(_._1(i))) ++
      (0 until ecc).toList.flatMap(i => parts.map(_._2(i)))
    interleaved
  }

  /** The Reed-Solomon generator polynomial of `degree`, highest power first, its leading 1 left out. */
  private def generator(degree: Int): List[Int] = {
    val result = (0 until degree).map(i => if (i == degree - 1) 1 else 0).toArray
    var root = 1
    (0 until degree).foreach { _ =>
      (0 until degree).foreach { j =>
        result(j) = multiply(result(j), root)
        if (j + 1 < degree) result(j) = xor(result(j), result(j + 1))
      }
      root = multiply(root, 2)
    }
    result.toList
  }

  private def remainder(data: List[Int], divisor: List[Int]): List[Int] = {
    val n = divisor.size
    val result = (0 until n).map(_ => 0).toArray
    data.foreach { b =>
      val factor = xor(b, result(0))
      (0 until n - 1).foreach(i => result(i) = result(i + 1))
      result(n - 1) = 0
      divisor.zipWithIndex.foreach { case (coefficient, i) =>
        result(i) = xor(result(i), multiply(coefficient, factor))
      }
    }
    result.toList
  }

  // ---- the modules ----

  private def drawFunctionPatterns(grid: Grid, version: Int): Unit = {
    val size = grid.size
    (0 until size).foreach { i =>
      grid.fixed(6, i, i % 2 == 0)
      grid.fixed(i, 6, i % 2 == 0)
    }
    drawFinder(grid, 3, 3)
    drawFinder(grid, size - 4, 3)
    drawFinder(grid, 3, size - 4)
    val positions = alignmentPositions(version, size)
    val last = positions.size - 1
    positions.zipWithIndex.foreach { case (x, i) =>
      positions.zipWithIndex.foreach { case (y, j) =>
        val corner = (i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)
        if (!corner) (0 until 5).foreach { dy =>
          (0 until 5).foreach { dx =>
            grid.fixed(x + dx - 2, y + dy - 2, math.max(math.abs(dx - 2), math.abs(dy - 2)) != 1)
          }
        }
      }
    }
    drawFormatBits(grid, 0)
    if (version >= 7) {
      var rem = version
      (0 until 12).foreach(_ => rem = xor(rem * 2, (rem / 2048) * 0x1F25))
      val bits = version * 4096 + rem
      (0 until 18).foreach { i =>
        val on = bit(bits, i)
        val a = size - 11 + i % 3
        val b = i / 3
        grid.fixed(a, b, on)
        grid.fixed(b, a, on)
      }
    }
  }

  private def drawFinder(grid: Grid, x: Int, y: Int): Unit = (0 until 9).foreach { dy =>
    (0 until 9).foreach { dx =>
      val distance = math.max(math.abs(dx - 4), math.abs(dy - 4))
      val xx = x + dx - 4
      val yy = y + dy - 4
      if (xx >= 0 && xx < grid.size && yy >= 0 && yy < grid.size) grid.fixed(xx, yy, distance != 2 && distance != 4)
    }
  }

  private def alignmentPositions(version: Int, size: Int): List[Int] =
    if (version == 1) Nil
    else {
      val count = version / 7 + 2
      val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
      ((0 until count - 1).toList.map(i => size - 7 - i * step) :+ 6).reverse
    }

  /** The error correction level (M) and the mask, twice over. */
  private def drawFormatBits(grid: Grid, mask: Int): Unit = {
    val size = grid.size
    val data = mask
    var rem = data
    (0 until 10).foreach(_ => rem = xor(rem * 2, (rem / 512) * 0x537))
    val bits = xor(data * 1024 + rem, 0x5412)
    (0 until 6).foreach(i => grid.fixed(8, i, bit(bits, i)))
    grid.fixed(8, 7, bit(bits, 6))
    grid.fixed(8, 8, bit(bits, 7))
    grid.fixed(7, 8, bit(bits, 8))
    (9 until 15).foreach(i => grid.fixed(14 - i, 8, bit(bits, i)))
    (0 until 8).foreach(i => grid.fixed(size - 1 - i, 8, bit(bits, i)))
    (8 until 15).foreach(i => grid.fixed(8, size - 15 + i, bit(bits, i)))
    grid.fixed(8, size - 8, true)
  }

  /** The codewords' bits, in pairs of columns zigzagging up and down from the right, round the fixed patterns. */
  private def drawCodewords(grid: Grid, data: List[Int]): Unit = {
    val size = grid.size
    val bits = data.flatMap(b => (0 until 8).toList.map(i => bit(b, 7 - i)))
    var i = 0
    var right = size - 1
    while (right >= 1) {
      if (right == 6) right = 5
      val upward = ((right + 1) / 2) % 2 == 0
      (0 until size).foreach { vert =>
        (0 until 2).foreach { j =>
          val x = right - j
          val y = if (upward) size - 1 - vert else vert
          if (!grid.isFixed(x, y) && i < bits.size) {
            grid.set(x, y, bits(i))
            i = i + 1
          }
        }
      }
      right = right - 2
    }
  }

  /** Flip the data modules the mask picks (twice undoes it). */
  private def applyMask(grid: Grid, mask: Int): Unit = (0 until grid.size).foreach { y =>
    (0 until grid.size).foreach { x =>
      val flip =
        if (mask == 0) (x + y) % 2 == 0
        else if (mask == 1) y % 2 == 0
        else if (mask == 2) x % 3 == 0
        else if (mask == 3) (x + y) % 3 == 0
        else if (mask == 4) (x / 3 + y / 2) % 2 == 0
        else if (mask == 5) x * y % 2 + x * y % 3 == 0
        else if (mask == 6) (x * y % 2 + x * y % 3) % 2 == 0
        else ((x + y) % 2 + x * y % 3) % 2 == 0
      if (flip && !grid.isFixed(x, y)) grid.set(x, y, grid.get(x, y) == 0)
    }
  }

  /** How hard the modules are to read: long runs, solid squares, and an uneven share of dark. */
  private def penalty(grid: Grid): Int = {
    val size = grid.size
    var result = 0
    var dark = 0
    (0 until size).foreach { a =>
      var runRow = 1
      var runColumn = 1
      (0 until size).foreach { b =>
        dark = dark + grid.get(b, a)
        if (b > 0) {
          if (grid.get(b, a) == grid.get(b - 1, a)) {
            runRow = runRow + 1
            if (runRow == 5) result = result + 3 else if (runRow > 5) result = result + 1
          } else runRow = 1
          if (grid.get(a, b) == grid.get(a, b - 1)) {
            runColumn = runColumn + 1
            if (runColumn == 5) result = result + 3 else if (runColumn > 5) result = result + 1
          } else runColumn = 1
          if (a > 0) {
            val c = grid.get(b, a)
            if (c == grid.get(b - 1, a) && c == grid.get(b, a - 1) && c == grid.get(b - 1, a - 1)) result = result + 3
          }
        }
      }
    }
    val total = size * size
    val k = (math.abs(dark * 20 - total * 10) + total - 1) / total - 1
    result + math.max(0, k) * 10
  }
}

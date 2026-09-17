package reactifytest

/** Wider than a BrightScript function takes (32 parameters): 34 fields, and a method of 33, so the emitter's packing
  * of the rest into one array is exercised on both sides of a call. */
case class Wide(a0: Int, a1: Int, a2: Int, a3: Int, a4: Int, a5: Int, a6: Int, a7: Int,
                a8: Int, a9: Int, a10: Int, a11: Int, a12: Int, a13: Int, a14: Int, a15: Int,
                a16: Int, a17: Int, a18: Int, a19: Int, a20: Int, a21: Int, a22: Int, a23: Int,
                a24: Int, a25: Int, a26: Int, a27: Int, a28: Int, a29: Int, a30: Int, a31: Int,
                a32: Int, a33: Int) {
  def weigh(b0: Int, b1: Int, b2: Int, b3: Int, b4: Int, b5: Int, b6: Int, b7: Int,
            b8: Int, b9: Int, b10: Int, b11: Int, b12: Int, b13: Int, b14: Int, b15: Int,
            b16: Int, b17: Int, b18: Int, b19: Int, b20: Int, b21: Int, b22: Int, b23: Int,
            b24: Int, b25: Int, b26: Int, b27: Int, b28: Int, b29: Int, b30: Int, b31: Int,
            b32: Int): Int =
    a0 + a33 * 100 + b0 * 10000 + b32 * 1000000
}

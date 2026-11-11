// everyui — the Swift runtime for Scala emitted by everyui's compiler (Swift spike).
//
// The emitted Swift follows the same object model as the Roku channel, so this file follows roku's
// ScalaRuntime.brs: one dynamic value type `V`; an instance is a field dictionary carrying `_cls` and its
// method slots; a closure is `{ fn, env }`; methods take the receiver as an explicit first argument; and
// the Scala stdlib shims (equality and identity, Option, Try, Set, object-keyed Map, ThreadLocal,
// AtomicBoolean, Promise) sit on top.
//
// Memory: Swift frees by reference counting and never collects cycles, and reactify's dependency graph is
// cyclic by design. Every reference the emitted code holds lives in an `Obj`'s fields or an `Arr`'s items,
// so the object graph is fully visible: `rtCollectCycles(roots)` marks what is reachable from the roots and
// clears every other object's fields, breaking its cycles. It must only run where no emitted code is on the
// stack — between frames, or between calls into the program — since a Swift local is not a root.
//
// Every instance sits on an intrusive doubly-linked list (unowned links, unlinked in `deinit`, so acyclic
// garbage still goes the moment reference counting drops it) and carries a mark epoch; a collection is one
// traversal from the roots stamping the epoch, then one walk of the list clearing whatever it did not stamp.
// No allocation, no hashing and no weak loads per object, so the cost is a few hundred nanoseconds each.

import Foundation

// MARK: - values

@dynamicCallable
enum V {
    case vNull
    case vBool(Bool)
    case vInt(Int)
    case vDbl(Double)
    case vStr(String)
    case vObj(Obj)
    case vArr(Arr)
    case vFn(Fn)
    /** A platform object behind a `@native` facade (a UIKit view): opaque, compared by identity. */
    case vRef(AnyObject)

    subscript(_ key: String) -> V {
        get {
            if case .vObj(let o) = self { return o.fields[key] ?? .vNull }
            return .vNull
        }
        nonmutating set {
            if case .vObj(let o) = self { o.fields[key] = newValue }
            else { rtFatal("cannot set field '\(key)' on \(rtKindName(self))") }
        }
    }

    subscript(_ index: V) -> V {
        get {
            switch (self, index) {
            case (.vArr(let a), .vInt(let i)): return i >= 0 && i < a.items.count ? a.items[i] : .vNull
            case (.vObj(let o), _): return o.fields[rtS(index)] ?? .vNull
            default: return .vNull
            }
        }
        nonmutating set {
            switch (self, index) {
            case (.vArr(let a), .vInt(let i)):
                while a.items.count <= i { a.items.append(.vNull) }
                a.items[i] = newValue
            case (.vObj(let o), _): o.fields[rtS(index)] = newValue
            default: rtFatal("cannot index-assign \(rtKindName(self))")
            }
        }
    }

    func dynamicallyCall(withArguments args: [V]) throws -> V {
        if case .vFn(let f) = self { return try f.call(args) }
        throw RtError(v: rtException("RuntimeException", "not a function: \(rtKindName(self))"))
    }

    /** A Boolean used as a condition. */
    var rtTrue: Bool {
        if case .vBool(let b) = self { return b }
        return false
    }
}

final class Obj {
    var fields: [String: V]
    var mark: UInt32 = 0
    unowned(unsafe) var prev: Obj
    unowned(unsafe) var next: Obj

    init(_ fields: [String: V]) {
        self.fields = fields
        // link before the list's sentinel
        let tail = Rt.sentinel.prev
        prev = tail
        next = Rt.sentinel
        tail.next = self
        Rt.sentinel.prev = self
        Rt.live += 1
        Rt.allocated += 1
        Rt.allocatedTotal += 1
    }

    /** The list's sentinel: links to itself, never marked, never collected. */
    private init(sentinel: ()) {
        fields = [:]
        prev = unsafeBitCast(0, to: Obj.self)   // overwritten below
        next = unsafeBitCast(0, to: Obj.self)
        prev = self
        next = self
    }
    static func makeSentinel() -> Obj { Obj(sentinel: ()) }

    deinit {
        prev.next = next
        next.prev = prev
        Rt.live -= 1
    }
}

final class Arr {
    var items: [V]
    var mark: UInt32 = 0
    init(_ items: [V]) { self.items = items }
}

final class Fn {
    let call: ([V]) throws -> V
    init(_ call: @escaping ([V]) throws -> V) { self.call = call }
}

/** A Scala exception in flight: the exception object itself, so a `catch` can match its `_cls`. */
struct RtError: Error { let v: V }

enum Rt {
    /** Instances alive right now. */
    static var live = 0
    /** Instances created since the last collection (the frame driver's trigger), and ever. */
    static var allocated = 0
    static var allocatedTotal = 0
    /** The list of every instance, for the cycle collector. */
    static let sentinel: Obj = Obj.makeSentinel()
    /** The current mark. */
    static var epoch: UInt32 = 0
    /** Silences `println` (the memory probe runs the suite repeatedly). */
    static var quiet = false
}

/** The global table (BrightScript's GetGlobalAA): object singletons and their caches live here. A root. */
let ga: V = .vObj(Obj(["_cls": .vStr("Global")]))

func rtFatal(_ message: String) -> Never { fatalError("everyui runtime: " + message) }

func rtKindName(_ v: V) -> String {
    switch v {
    case .vNull: return "nil"
    case .vBool: return "b"
    case .vInt, .vDbl: return "n"
    case .vStr: return "s"
    case .vObj: return "aa"
    case .vArr: return "arr"
    case .vFn: return "fn"
    case .vRef: return "ref"
    }
}

func rtToInt(_ v: V) -> V {
    switch v {
    case .vInt: return v
    case .vDbl(let d): return .vInt(d.isFinite ? Int(d) : 0)
    case .vStr(let s): return .vInt(Int(s) ?? 0)
    case .vBool(let b): return .vInt(b ? 1 : 0)
    default: return .vInt(0)
    }
}
func rtToDbl(_ v: V) -> V {
    switch v {
    case .vDbl: return v
    case .vInt(let i): return .vDbl(Double(i))
    case .vStr(let s): return .vDbl(Double(s) ?? 0)
    default: return .vDbl(0)
    }
}
/// A number narrowed to a Short (`toShort`) or a Byte (`toByte`) as the JVM does: the low 16 or 8 bits read as signed, a
/// fraction first truncated toward zero and held to an Int's range (NaN is 0), a String first read as a whole number.
func _toShort(_ v: V) -> V { .vInt(Int(Int16(truncatingIfNeeded: _whole(v)))) }
func _toByte(_ v: V) -> V { .vInt(Int(Int8(truncatingIfNeeded: _whole(v)))) }
private func _whole(_ v: V) -> Int {
    switch v {
    case .vDbl(let d): return d.isNaN ? 0 : Int(max(-2147483648.0, min(2147483647.0, d.rounded(.towardZero))))
    case .vInt(let n): return n
    default:
        if case .vInt(let n) = rtToInt(v) { return n }
        return 0
    }
}
/// Bit operations the JVM's way: `&`, `|`, `^` and `~` on whole numbers, and the shifts on an Int's 32 bits or (the L
/// forms) a Long's 64, the count taken as the JVM takes it.
private func _bits(_ v: V) -> Int {
    switch v {
    case .vInt(let n): return n
    case .vDbl(let d): return d.isNaN ? 0 : Int(d.rounded(.towardZero))
    default: return 0
    }
}
func _band(_ a: V, _ b: V) -> V { .vInt(_bits(a) & _bits(b)) }
func _bor(_ a: V, _ b: V) -> V { .vInt(_bits(a) | _bits(b)) }
func _xor(_ a: V, _ b: V) -> V { .vInt(_bits(a) ^ _bits(b)) }
func _bnot(_ a: V) -> V { .vInt(~_bits(a)) }
func _shl(_ a: V, _ n: V) -> V { .vInt(Int(Int32(truncatingIfNeeded: _bits(a)) << Int32(_bits(n) & 31))) }
func _shr(_ a: V, _ n: V) -> V { .vInt(Int(Int32(truncatingIfNeeded: _bits(a)) >> Int32(_bits(n) & 31))) }
func _ushr(_ a: V, _ n: V) -> V {
    .vInt(Int(Int32(bitPattern: UInt32(truncatingIfNeeded: _bits(a)) >> UInt32(_bits(n) & 31))))
}
func _shlL(_ a: V, _ n: V) -> V { .vInt(Int(Int64(_bits(a)) << Int64(_bits(n) & 63))) }
func _shrL(_ a: V, _ n: V) -> V { .vInt(Int(Int64(_bits(a)) >> Int64(_bits(n) & 63))) }
func _ushrL(_ a: V, _ n: V) -> V {
    .vInt(Int(Int64(bitPattern: UInt64(bitPattern: Int64(_bits(a))) >> UInt64(_bits(n) & 63))))
}
/// A whole number's hex digits, a negative one's as its unsigned 32 bits (`Integer.toHexString`) or 64 (`Long`'s).
func _hexOf(_ a: V) -> V { .vStr(String(UInt32(truncatingIfNeeded: _bits(a)), radix: 16)) }
func _hexOfL(_ a: V) -> V { .vStr(String(UInt64(bitPattern: Int64(_bits(a))), radix: 16)) }
/// A charset by its name (a Charset is its name here): "latin1", "ascii", or "utf8" for UTF-8 and any other.
private func _charset(_ cs: V) -> String {
    let n = rtS(cs).uppercased().replacingOccurrences(of: "-", with: "").replacingOccurrences(of: "_", with: "")
    if ["ISO88591", "LATIN1", "ISOLATIN1", "L1"].contains(n) { return "latin1" }
    if ["USASCII", "ASCII"].contains(n) { return "ascii" }
    return "utf8"
}
/// A String's bytes in a charset (`getBytes`), each a Byte from -128 to 127; a character the charset cannot hold is "?".
func _strBytes(_ s: V, _ cs: V) -> V {
    let text = rtS(s)
    let bytes: [UInt8]
    switch _charset(cs) {
    case "latin1": bytes = text.unicodeScalars.map { $0.value < 256 ? UInt8($0.value) : 63 }
    case "ascii": bytes = text.unicodeScalars.map { $0.value < 128 ? UInt8($0.value) : 63 }
    default: bytes = Array(text.utf8)
    }
    return rtArr(bytes.map { .vInt(Int(Int8(bitPattern: $0))) })
}
/// Text from its bytes in a charset (`new String(bytes, charset)`): an ill-formed UTF-8 sequence, or a byte US-ASCII has
/// no place for, is U+FFFD.
func _bytesStr(_ b: V, _ cs: V) -> V {
    let bytes = rtItems(b).map { UInt8(truncatingIfNeeded: Int(rtDouble($0))) }
    switch _charset(cs) {
    case "latin1": return .vStr(String(String.UnicodeScalarView(bytes.map { Unicode.Scalar($0) })))
    case "ascii": return .vStr(String(String.UnicodeScalarView(bytes.map { $0 < 128 ? Unicode.Scalar($0) : "\u{FFFD}" })))
    default: return .vStr(String(decoding: bytes, as: UTF8.self))
    }
}
/// A Char's code (`c.toInt`): a Char is a one-character String here.
func _chCode(_ c: V) -> V { .vInt(Int(rtS(c).utf16.first ?? 0)) }
/// A Char of a code (or a Char already), and a Char's lower and upper case.
func _chOf(_ c: V) -> V {
    if case .vStr = c { return c }
    guard case .vInt(let n) = c, let u = UnicodeScalar(UInt32(truncatingIfNeeded: n)) else { return .vStr("") }
    return .vStr(String(Character(u)))
}
func _chLower(_ c: V) -> V { rtLower(_chOf(c)) }
func _chUpper(_ c: V) -> V { rtUpper(_chOf(c)) }
/// A String read as a whole number (`toIntOption`), or None (null) when the whole of it, trimmed, is not one.
func _strToIntOpt(_ s: V) -> V {
    if let n = Int(rtS(s).trimmingCharacters(in: .whitespaces)) { return .vInt(n) }
    return .vNull
}
/// A String read as a number (`toDoubleOption`), or None (null) when the whole of it, trimmed, is not one.
func _strToDoubleOpt(_ s: V) -> V {
    if let d = Double(rtS(s).trimmingCharacters(in: .whitespaces)) { return .vDbl(d) }
    return .vNull
}

// MARK: - construction helpers the emitter writes

func rtObj(_ fields: [String: V]) -> V { .vObj(Obj(fields)) }
func rtNew(_ cls: String) -> V { .vObj(Obj(["_cls": .vStr(cls)])) }
func rtArr(_ items: [V]) -> V { .vArr(Arr(items)) }

/** A lambda: the emitted `closureN(env, args)` and its captured environment. */
func rtClosure(_ f: @escaping (V, V) throws -> V, _ env: V) -> V {
    rtObj(["fn": .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1)) }), "env": env])
}

@inline(__always) func rtArg(_ a: [V], _ i: Int) -> V { i < a.count ? a[i] : .vNull }

/** A method slot: an emitted function as a callable value, whatever its arity. */
func rtFn(_ f: @escaping () throws -> V) -> V { .vFn(Fn { _ in try f() }) }
func rtFn(_ f: @escaping (V) throws -> V) -> V { .vFn(Fn { a in try f(rtArg(a, 0)) }) }
func rtFn(_ f: @escaping (V, V) throws -> V) -> V { .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1)) }) }
func rtFn(_ f: @escaping (V, V, V) throws -> V) -> V {
    .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2)) })
}
func rtFn(_ f: @escaping (V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3)) })
}
func rtFn(_ f: @escaping (V, V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3), rtArg(a, 4)) })
}
func rtFn(_ f: @escaping (V, V, V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3), rtArg(a, 4), rtArg(a, 5)) })
}
func rtFn(_ f: @escaping (V, V, V, V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3), rtArg(a, 4), rtArg(a, 5), rtArg(a, 6)) })
}
func rtFn(_ f: @escaping (V, V, V, V, V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in
        try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3), rtArg(a, 4), rtArg(a, 5), rtArg(a, 6), rtArg(a, 7))
    })
}
func rtFn(_ f: @escaping (V, V, V, V, V, V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in
        try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3), rtArg(a, 4), rtArg(a, 5), rtArg(a, 6), rtArg(a, 7),
              rtArg(a, 8))
    })
}
func rtFn(_ f: @escaping (V, V, V, V, V, V, V, V, V, V) throws -> V) -> V {
    .vFn(Fn { a in
        try f(rtArg(a, 0), rtArg(a, 1), rtArg(a, 2), rtArg(a, 3), rtArg(a, 4), rtArg(a, 5), rtArg(a, 6), rtArg(a, 7),
              rtArg(a, 8), rtArg(a, 9))
    })
}

/** A field write as an expression (a `var` setter in value position). */
func rtSet(_ o: V, _ key: String, _ v: V) -> V { o[key] = v; return .vNull }
/// A field of an object written by its name (an object of the object model's var, from outside it).
func _setField(_ o: V, _ k: V, _ v: V) -> V { o[rtS(k)] = v; return .vNull }

func Map(_ o: V = .vNull) -> V { if case .vNull = o { return _mapEmpty() }; return o }

func rtHas(_ o: V, _ key: String) -> Bool {
    if case .vObj(let obj) = o { return obj.fields[key] != nil }
    return false
}

func rtItems(_ xs: V) -> [V] {
    if case .vArr(let a) = xs { return a.items }
    return []
}

func rtCount(_ xs: V) -> V {
    switch xs {
    case .vArr(let a): return .vInt(a.items.count)
    case .vObj(let o): return .vInt(o.fields.count)
    default: return .vInt(0)
    }
}

// MARK: - operators (Scala semantics on dynamic values)

@inline(__always) func rtD(_ v: V) -> Double? {
    switch v {
    case .vInt(let i): return Double(i)
    case .vDbl(let d): return d
    default: return nil
    }
}

extension V {
    static func + (l: V, r: V) -> V {
        switch (l, r) {
        case (.vInt(let a), .vInt(let b)): return .vInt(a &+ b)
        case (.vStr(let a), _): return .vStr(a + rtS(r))
        case (_, .vStr(let b)): return .vStr(rtS(l) + b)
        default:
            if let a = rtD(l), let b = rtD(r) { return .vDbl(a + b) }
            rtFatal("\(rtKindName(l)) + \(rtKindName(r))")
        }
    }
    static func - (l: V, r: V) -> V {
        if case .vInt(let a) = l, case .vInt(let b) = r { return .vInt(a &- b) }
        if let a = rtD(l), let b = rtD(r) { return .vDbl(a - b) }
        rtFatal("\(rtKindName(l)) - \(rtKindName(r))")
    }
    static func * (l: V, r: V) -> V {
        if case .vInt(let a) = l, case .vInt(let b) = r { return .vInt(a &* b) }
        if let a = rtD(l), let b = rtD(r) { return .vDbl(a * b) }
        rtFatal("\(rtKindName(l)) * \(rtKindName(r))")
    }
    static func / (l: V, r: V) -> V {
        if case .vInt(let a) = l, case .vInt(let b) = r { return .vInt(a / b) }
        if let a = rtD(l), let b = rtD(r) { return .vDbl(a / b) }
        rtFatal("\(rtKindName(l)) / \(rtKindName(r))")
    }
    static func % (l: V, r: V) -> V {
        if case .vInt(let a) = l, case .vInt(let b) = r { return .vInt(a % b) }
        if let a = rtD(l), let b = rtD(r) { return .vDbl(a.truncatingRemainder(dividingBy: b)) }
        rtFatal("\(rtKindName(l)) % \(rtKindName(r))")
    }
    static func < (l: V, r: V) -> V { .vBool(rtCompare(l, r) < 0) }
    static func > (l: V, r: V) -> V { .vBool(rtCompare(l, r) > 0) }
    static func <= (l: V, r: V) -> V { .vBool(rtCompare(l, r) <= 0) }
    static func >= (l: V, r: V) -> V { .vBool(rtCompare(l, r) >= 0) }
    static func == (l: V, r: V) -> V { .vBool(rtPrimEq(l, r)) }
    static func != (l: V, r: V) -> V { .vBool(!rtPrimEq(l, r)) }
    static func && (l: V, r: @autoclosure () throws -> V) rethrows -> V {
        l.rtTrue ? .vBool(try r().rtTrue) : .vBool(false)
    }
    static func || (l: V, r: @autoclosure () throws -> V) rethrows -> V {
        l.rtTrue ? .vBool(true) : .vBool(try r().rtTrue)
    }
    static prefix func ! (v: V) -> V { .vBool(!v.rtTrue) }
    static prefix func - (v: V) -> V {
        switch v {
        case .vInt(let i): return .vInt(0 &- i)
        case .vDbl(let d): return .vDbl(-d)
        default: rtFatal("-\(rtKindName(v))")
        }
    }
}

func rtCompare(_ l: V, _ r: V) -> Int {
    if case .vStr(let a) = l, case .vStr(let b) = r { return a < b ? -1 : (a > b ? 1 : 0) }
    if case .vInt(let a) = l, case .vInt(let b) = r { return a < b ? -1 : (a > b ? 1 : 0) }
    if let a = rtD(l), let b = rtD(r) { return a < b ? -1 : (a > b ? 1 : 0) }
    if case .vBool(let a) = l, case .vBool(let b) = r { return a == b ? 0 : (a ? 1 : -1) }
    // tuples, element by element (Ordering.Tuple2 and on: a sortBy keyed by a pair, Access's reading order)
    if case .vObj = l, case .vObj = r, rtHas(l, "_1"), rtHas(r, "_1") {
        for k in ["_1", "_2", "_3", "_4", "_5", "_6"] where rtHas(l, k) && rtHas(r, k) {
            let c = rtCompare(l[k], r[k])
            if c != 0 { return c }
        }
        return 0
    }
    rtFatal("cannot compare \(rtKindName(l)) with \(rtKindName(r))")
}

/** `=` in the Roku model: primitives by value, references by identity. */
func rtPrimEq(_ l: V, _ r: V) -> Bool {
    switch (l, r) {
    case (.vNull, .vNull): return true
    case (.vNull, _), (_, .vNull): return false
    case (.vBool(let a), .vBool(let b)): return a == b
    case (.vInt(let a), .vInt(let b)): return a == b
    case (.vStr(let a), .vStr(let b)): return a == b
    case (.vObj(let a), .vObj(let b)): return a === b
    case (.vArr(let a), .vArr(let b)): return a === b
    case (.vFn(let a), .vFn(let b)): return a === b
    case (.vRef(let a), .vRef(let b)): return a === b
    default:
        if let a = rtD(l), let b = rtD(r) { return a == b }
        return false
    }
}

// MARK: - strings

func rtS(_ v: V) -> String {
    switch v {
    case .vNull: return ""
    case .vStr(let s): return s
    case .vBool(let b): return b ? "true" : "false"
    case .vInt(let i): return String(i)
    case .vDbl(let d): return rtDoubleString(d)
    case .vObj: return rtObjString(v)
    case .vArr: return rtArrString(v)
    case .vFn: return "<function>"
    case .vRef(let o): return "<\(type(of: o))>"
    }
}

/** Scala's Double#toString for the common cases: `3.0`, `0.5`, `1.0E10`. */
func rtDoubleString(_ d: Double) -> String {
    if d.isNaN { return "NaN" }
    if d.isInfinite { return d > 0 ? "Infinity" : "-Infinity" }
    if d == d.rounded(), abs(d) < 1e7 { return String(Int(d)) + ".0" }
    return String(d)
}

func rtObjString(_ o: V) -> String {
    if case .vFn = o["toString"] {
        if let s = try? o["toString"](o) { return rtS(s) }
    }
    if rtHas(o, "_fields") && rtHas(o, "_cls") {
        return rtS(o["_cls"]) + "(" + rtItems(o["_fields"]).map { rtS(o[rtS($0)]) }.joined(separator: ",") + ")"
    }
    if rtHas(o, "message") { return rtS(o["message"]) }
    if rtHas(o, "_name") { return rtS(o["_name"]) }
    if rtHas(o, "_cls") { return rtS(o["_cls"]) }
    return "<object>"
}

func rtArrString(_ a: V) -> String { "List(" + rtItems(a).map(rtS).joined(separator: ", ") + ")" }

func _str(_ v: V) -> V { .vStr(rtS(v)) }
func _rtObjStr(_ o: V) -> V { .vStr(rtObjString(o)) }
func _rtArrStr(_ a: V) -> V { .vStr(rtArrString(a)) }

/** String length in UTF-16 code units, as the JVM counts. */
func rtLen(_ s: V) -> V { .vInt(rtS(s).utf16.count) }
/// A Char argument arrives as its code: as a one-character string.
func rtCharOrString(_ v: V) -> String {
    if case .vInt(let c) = v, let scalar = Unicode.Scalar(UInt32(max(0, c))) { return String(Character(scalar)) }
    return rtS(v)
}
func _strContains(_ s: V, _ sub: V) -> V { .vBool(rtDouble(rtIndexOf(s, sub)) >= 0) }
func rtIndexOf(_ s: V, _ sub: V) -> V {
    let hay = Array(rtS(s).utf16), needle = Array(rtCharOrString(sub).utf16)
    if needle.isEmpty { return .vInt(0) }
    if needle.count > hay.count { return .vInt(-1) }
    for i in 0...(hay.count - needle.count) where Array(hay[i..<(i + needle.count)]) == needle { return .vInt(i) }
    return .vInt(-1)
}
func rtLastIndexOf(_ s: V, _ sub: V) -> V {
    let hay = Array(rtS(s).utf16), needle = Array(rtCharOrString(sub).utf16)
    if needle.isEmpty { return .vInt(hay.count) }
    if needle.count > hay.count { return .vInt(-1) }
    for i in stride(from: hay.count - needle.count, through: 0, by: -1) where Array(hay[i..<(i + needle.count)]) == needle { return .vInt(i) }
    return .vInt(-1)
}
func rtLower(_ s: V) -> V { .vStr(rtS(s).lowercased()) }
func rtUpper(_ s: V) -> V { .vStr(rtS(s).uppercased()) }

func _substr(_ s: V, _ a: V, _ b: V) -> V {
    let u = Array(rtS(s).utf16)
    guard case .vInt(let from) = a, case .vInt(let to) = b, from >= 0, to <= u.count, from <= to else { return .vStr("") }
    return .vStr(String(decoding: u[from..<to], as: UTF16.self))
}
func _substr1(_ s: V, _ a: V) -> V { _substr(s, a, rtLen(s)) }
func _trim(_ s: V) -> V { .vStr(rtS(s).trimmingCharacters(in: .whitespaces)) }
func _split(_ s: V, _ sep: V) -> V {
    let str = rtS(s), by = rtCharOrString(sep)
    if by.isEmpty { return rtArr(str.map { .vStr(String($0)) }) }
    return rtArr(str.components(separatedBy: by).filter { !$0.isEmpty }.map { .vStr($0) })
}
func _startsWith(_ s: V, _ p: V) -> V { .vBool(rtS(s).hasPrefix(rtS(p))) }
/// Whether a String has a prefix at an index (Java's startsWith(prefix, offset)), by UTF-16 unit as Java counts.
func _startsWithAt(_ s: V, _ p: V, _ at: V) -> V {
    guard case .vInt(let i) = at else { return .vBool(false) }
    let a = Array(rtS(s).utf16), b = Array(rtS(p).utf16)
    return .vBool(i >= 0 && i + b.count <= a.count && Array(a[i..<(i + b.count)]) == b)
}
/// A String repeated (`"ab" * 3`).
func _strRepeat(_ s: V, _ n: V) -> V {
    guard case .vInt(let k) = n, k > 0 else { return .vStr("") }
    return .vStr(String(repeating: rtS(s), count: k))
}
func _endsWith(_ s: V, _ p: V) -> V { .vBool(rtS(s).hasSuffix(rtS(p))) }
func _stripPrefix(_ s: V, _ p: V) -> V { let a = rtS(s), b = rtS(p); return .vStr(a.hasPrefix(b) ? String(a.dropFirst(b.count)) : a) }
func _stripSuffix(_ s: V, _ p: V) -> V { let a = rtS(s), b = rtS(p); return .vStr(a.hasSuffix(b) ? String(a.dropLast(b.count)) : a) }
func _replace(_ s: V, _ a: V, _ b: V) -> V { .vStr(rtS(s).replacingOccurrences(of: rtS(a), with: rtS(b))) }
func _strInterp(_ parts: V, _ args: V) -> V {
    let ps = rtItems(parts), xs = rtItems(args)
    var r = ""
    for (i, p) in ps.enumerated() {
        r += rtS(p)
        if i < xs.count { r += rtS(xs[i]) }
    }
    return .vStr(r)
}

func println(_ s: V) -> V {
    if !Rt.quiet { print(rtS(s)) }
    return .vNull
}

func RtTodo(_ label: String) -> V { .vStr("[todo: " + label + "]") }
/// A call into a type nothing constructs (the emitter's reachability analysis): it cannot run.
func RtDead(_ what: V) throws -> V { throw RtError(v: .vStr("unreachable: " + rtS(what))) }

// MARK: - numbers

func _iif(_ c: V, _ a: V, _ b: V) -> V { c.rtTrue ? a : b }
func RtMin(_ a: V, _ b: V) -> V { rtCompare(a, b) < 0 ? a : b }
func RtMax(_ a: V, _ b: V) -> V { rtCompare(a, b) > 0 ? a : b }
func RtDoubleMax() -> V { .vDbl(Double.greatestFiniteMagnitude) }
func RtDoubleMin() -> V { .vDbl(-Double.greatestFiniteMagnitude) }
func _cmpNum(_ a: V, _ b: V) -> V { .vInt(rtCompare(a, b)) }
func rtMath(_ v: V, _ f: (Double) -> Double) -> V { .vDbl(f(rtD(v) ?? 0)) }
func rtSqrt(_ v: V) -> V { rtMath(v, sqrt) }
func rtPow(_ a: V, _ b: V) -> V { .vDbl(pow(rtD(a) ?? 0, rtD(b) ?? 0)) }
func rtSin(_ v: V) -> V { rtMath(v, sin) }
func rtCos(_ v: V) -> V { rtMath(v, cos) }
func rtTan(_ v: V) -> V { rtMath(v, tan) }
func rtAtan(_ v: V) -> V { rtMath(v, atan) }
func rtExp(_ v: V) -> V { rtMath(v, exp) }
func rtLog(_ v: V) -> V { rtMath(v, log) }
func rtAbs(_ v: V) -> V { if case .vInt(let i) = v { return .vInt(abs(i)) }; return rtMath(v, abs) }
func rtFloor(_ v: V) -> V { .vInt(Int(floor(rtD(v) ?? 0))) }
func rtCeil(_ v: V) -> V { .vInt(Int(ceil(rtD(v) ?? 0))) }
func rtRound(_ v: V) -> V { .vInt(Int(((rtD(v) ?? 0) + 0.5).rounded(.down))) }
func RtDuration(_ ms: V) -> V { rtObj(["_cls": .vStr("FiniteDuration"), "_fields": rtArr([.vStr("ms")]), "ms": ms]) }
func RtDurationMs(_ d: V) -> V { if case .vNull = d { return .vInt(0) }; return d["ms"] }

// MARK: - identity, equality, dispatch

func _rtKind(_ v: V) -> V { .vStr(rtKindName(v)) }

func _rtHasFn(_ o: V) -> Bool {
    if case .vObj(let obj) = o { return obj.fields.values.contains { if case .vFn = $0 { return true }; return false } }
    return false
}

/** Scala `eq`. */
func RtIdentity(_ a: V, _ b: V) -> V {
    switch (a, b) {
    case (.vObj(let x), .vObj(let y)): return .vBool(x === y)
    case (.vArr, .vArr): return _eq(a, b)
    default: return .vBool(rtPrimEq(a, b))
    }
}

/** Scala `==`: structural for case classes, maps, tuples and plain records; element-wise for sequences; by
  * value for primitives; identity for any other object. */
func _eq(_ a: V, _ b: V) -> V { .vBool(rtEq(a, b)) }

func rtEq(_ a: V, _ b: V) -> Bool {
    switch (a, b) {
    case (.vObj(let x), .vObj(let y)):
        if x === y { return true }
        let af = x.fields["_fields"], bf = y.fields["_fields"]
        if af == nil && bf == nil && (x.fields["_cls"] != nil || y.fields["_cls"] != nil) { return false }
        if x.fields["_map"] != nil && y.fields["_map"] != nil { return _mapEq(a, b).rtTrue }
        if af != nil && bf != nil {
            if !rtPrimEq(a["_cls"], b["_cls"]) { return false }
            for f in rtItems(af!) where !rtEq(a[rtS(f)], b[rtS(f)]) { return false }
            return true
        }
        if _rtHasFn(a) || _rtHasFn(b) { return false }
        let ak = x.fields.keys.filter { $0 != "_id" }, bk = y.fields.keys.filter { $0 != "_id" }
        if ak.count != bk.count { return false }
        for k in ak {
            guard let bv = y.fields[k] else { return false }
            if !rtEq(x.fields[k]!, bv) { return false }
        }
        return true
    case (.vArr(let x), .vArr(let y)):
        if x.items.count != y.items.count { return false }
        for i in 0..<x.items.count where !rtEq(x.items[i], y.items[i]) { return false }
        return true
    default:
        return rtPrimEq(a, b)
    }
}

func RtVcall0(_ r: V, _ n: String) throws -> V { try r[n](r) }
func RtVcall1(_ r: V, _ n: String, _ a1: V) throws -> V { try r[n](r, a1) }
func RtVcall2(_ r: V, _ n: String, _ a1: V, _ a2: V) throws -> V { try r[n](r, a1, a2) }
func RtVcall3(_ r: V, _ n: String, _ a1: V, _ a2: V, _ a3: V) throws -> V { try r[n](r, a1, a2, a3) }
func RtVcall4(_ r: V, _ n: String, _ a1: V, _ a2: V, _ a3: V, _ a4: V) throws -> V { try r[n](r, a1, a2, a3, a4) }
func RtVcall5(_ r: V, _ n: String, _ a1: V, _ a2: V, _ a3: V, _ a4: V, _ a5: V) throws -> V {
    try r[n](r, a1, a2, a3, a4, a5)
}
func RtVcall6(_ r: V, _ n: String, _ a1: V, _ a2: V, _ a3: V, _ a4: V, _ a5: V, _ a6: V) throws -> V {
    try r[n](r, a1, a2, a3, a4, a5, a6)
}
func RtVcall7(_ r: V, _ n: String, _ a1: V, _ a2: V, _ a3: V, _ a4: V, _ a5: V, _ a6: V, _ a7: V) throws -> V {
    try r[n](r, a1, a2, a3, a4, a5, a6, a7)
}
func RtVcall8(_ r: V, _ n: String, _ a1: V, _ a2: V, _ a3: V, _ a4: V, _ a5: V, _ a6: V, _ a7: V, _ a8: V) throws -> V {
    try r[n](r, a1, a2, a3, a4, a5, a6, a7, a8)
}

/** Call a closure value with an argument array. */
func RtInvoke(_ cl: V, _ args: V) throws -> V {
    if case .vFn = cl["fn"] { return try cl["fn"](cl["env"], args) }
    return .vNull
}

/** A by-name argument arrives as a zero-arg closure; force it. A plain value passes through. */
func RtForce(_ x: V) throws -> V {
    if case .vObj = x, case .vFn = x["fn"] { return try x["fn"](x["env"], rtArr([])) }
    return x
}

// MARK: - exceptions

func rtException(_ cls: String, _ message: String) -> V {
    rtObj(["_cls": .vStr(cls), "message": .vStr(message)])
}

@discardableResult
func RtThrow(_ e: V) throws -> V {
    var ex = e
    if case .vObj = e {} else { ex = rtException("RuntimeException", rtS(e)) }
    if case .vStr = ex["message"] {} else { ex["message"] = _str(ex["_cls"]) }
    throw RtError(v: ex)
}

func Exception_new(_ msg: V = .vStr("")) -> V { rtException("Exception", rtS(msg)) }
func RuntimeException_new(_ msg: V = .vNull) -> V { rtException("RuntimeException", rtS(msg)) }
func IllegalStateException_new(_ msg: V = .vNull) -> V { rtException("IllegalStateException", rtS(msg)) }
func IllegalArgumentException_new(_ msg: V = .vNull) -> V { rtException("IllegalArgumentException", rtS(msg)) }
func MatchError_new(_ v: V = .vNull) -> V { rtException("MatchError", "MatchError: " + rtS(v)) }
func AssertionError_new(_ msg: V = .vNull) -> V {
    if case .vNull = msg { return rtException("AssertionError", "assertion failed") }
    return rtException("AssertionError", "assertion failed: " + rtS(msg))
}

func RtAssert(_ cond: V, _ msg: V = .vNull) throws -> V {
    if !cond.rtTrue {
        var text = "assertion failed"
        if case .vNull = msg {} else { text += ": " + rtS(try RtForce(msg)) }
        try RtThrow(rtException("AssertionError", text))
    }
    return .vNull
}

/** Single-threaded model: a synchronized block is just the block. */
func RtSynchronized(_ thunk: V) throws -> V { try RtForce(thunk) }

let rtUnit: V = rtObj(["_cls": .vStr("Unit"), "_fields": rtArr([])])
func RtUnit() -> V { rtUnit }

// MARK: - collections

func _append(_ xs: V, _ x: V) -> V { rtArr(rtItems(xs) + [x]) }
func _prepend(_ x: V, _ xs: V) -> V { rtArr([x] + rtItems(xs)) }
func _concat(_ xs: V, _ ys: V) -> V { rtArr(rtItems(xs) + rtItems(ys)) }
func _arrMap(_ xs: V, _ cl: V) throws -> V { rtArr(try rtItems(xs).map { try RtInvoke(cl, rtArr([$0])) }) }
func _arrFlatMap(_ xs: V, _ cl: V) throws -> V {
    var r: [V] = []
    for e in rtItems(xs) {
        let sub = try RtInvoke(cl, rtArr([e]))
        switch sub {
        case .vArr(let a): r.append(contentsOf: a.items)
        case .vNull: break
        default: r.append(sub)
        }
    }
    return rtArr(r)
}
func _arrFilter(_ xs: V, _ cl: V) throws -> V { rtArr(try rtItems(xs).filter { try RtInvoke(cl, rtArr([$0])).rtTrue }) }
func _arrFilterNot(_ xs: V, _ cl: V) throws -> V {
    rtArr(try rtItems(xs).filter { !(try RtInvoke(cl, rtArr([$0])).rtTrue) })
}
@discardableResult
func _arrForeach(_ xs: V, _ cl: V) throws -> V {
    for e in rtItems(xs) { _ = try RtInvoke(cl, rtArr([e])) }
    return .vNull
}
func _arrExists(_ xs: V, _ cl: V) throws -> V {
    for e in rtItems(xs) where try RtInvoke(cl, rtArr([e])).rtTrue { return .vBool(true) }
    return .vBool(false)
}
func _arrForall(_ xs: V, _ cl: V) throws -> V {
    for e in rtItems(xs) where !(try RtInvoke(cl, rtArr([e])).rtTrue) { return .vBool(false) }
    return .vBool(true)
}
func _arrFind(_ xs: V, _ cl: V) throws -> V {
    for e in rtItems(xs) where try RtInvoke(cl, rtArr([e])).rtTrue { return e }
    return .vNull
}
func _arrSet(_ xs: V, _ i: V, _ v: V) -> V {
    if case .vArr(let a) = xs, case .vInt(let k) = i, k >= 0, k < a.items.count { a.items[k] = v }
    return .vNull
}
func _arrCount(_ xs: V, _ cl: V) throws -> V {
    var n = 0
    for e in rtItems(xs) where try RtInvoke(cl, rtArr([e])).rtTrue { n += 1 }
    return .vInt(n)
}
func _arrIndexWhere(_ xs: V, _ cl: V) throws -> V {
    for (i, e) in rtItems(xs).enumerated() where try RtInvoke(cl, rtArr([e])).rtTrue { return .vInt(i) }
    return .vInt(-1)
}
/** xs.collect(pf) (and collectFirst) where pf is a value: an element it does not take throws its MatchError, which
  * leaves it out. Cases written in place go to _collectCases instead. */
func _collect(_ xs: V, _ pf: V, _ first: Bool) throws -> V {
    var r: [V] = []
    for e in rtItems(xs) {
        do {
            let v = try RtInvoke(pf, rtArr([e]))
            if first { return v }
            r.append(v)
        } catch let error as RtError {
            if rtS(error.v["_cls"]) != "MatchError" { throw error }
        }
    }
    return first ? .vNull : rtArr(r)
}
/** xs.collect { case … } (and collectFirst): an element none of the cases takes comes back with the closure's
  * env._miss set (the emitter's emitPartialClosure), rather than as a MatchError thrown and caught. */
func _collectCases(_ xs: V, _ pf: V, _ first: Bool) throws -> V {
    var r: [V] = []
    let env = pf["env"]
    env["_miss"] = .vBool(false)
    for e in rtItems(xs) {
        let v = try RtInvoke(pf, rtArr([e]))
        if env["_miss"].rtTrue { env["_miss"] = .vBool(false) }
        else {
            if first { return v }
            r.append(v)
        }
    }
    return first ? .vNull : rtArr(r)
}
func _contains(_ xs: V, _ x: V) -> V { .vBool(rtItems(xs).contains { rtEq($0, x) }) }
func _arrIndexOf(_ xs: V, _ x: V) -> V { .vInt(rtItems(xs).firstIndex { rtEq($0, x) } ?? -1) }
func _optToList(_ o: V) -> V { if case .vNull = o { return rtArr([]) }; return rtArr([o]) }
func _mkString(_ xs: V, _ sep: V) -> V { .vStr(rtItems(xs).map(rtS).joined(separator: rtS(sep))) }
/// A collection's first `n`, or the rest after them; a String's are a String, as the Roku's are (a String's `take`
/// was a list of its characters, which a name cut short with it, `Focus.named`'s, came out as "List()").
func _take(_ xs: V, _ n: V) -> V {
    guard case .vInt(let k) = n else { return rtArr([]) }
    if case .vStr(let s) = xs { return .vStr(rtStrSplit(s, k).0) }
    return rtArr(Array(rtItems(xs).prefix(max(0, k))))
}
func _drop(_ xs: V, _ n: V) -> V {
    guard case .vInt(let k) = n else { return xs }
    if case .vStr(let s) = xs { return .vStr(rtStrSplit(s, k).1) }
    return rtArr(Array(rtItems(xs).dropFirst(max(0, k))))
}
/// A String cut after `n` UTF-16 units, as the JVM counts them, a surrogate pair kept whole on the side it starts on.
func rtStrSplit(_ s: String, _ n: Int) -> (String, String) {
    var units = 0
    var at = s.unicodeScalars.startIndex
    for scalar in s.unicodeScalars {
        if units >= n { break }
        units += scalar.utf16.count
        at = s.unicodeScalars.index(after: at)
    }
    return (String(s.unicodeScalars[..<at]), String(s.unicodeScalars[at...]))
}
func _sortBy(_ xs: V, _ cl: V) throws -> V {
    let keyed = try rtItems(xs).map { (k: try RtInvoke(cl, rtArr([$0])), v: $0) }
    return rtArr(rtStableSort(keyed) { rtCompare($0.k, $1.k) < 0 }.map { $0.v })
}
func rtStableSort<T>(_ xs: [T], _ less: (T, T) throws -> Bool) rethrows -> [T] {
    try xs.enumerated().sorted { a, b in
        if try less(a.element, b.element) { return true }
        if try less(b.element, a.element) { return false }
        return a.offset < b.offset
    }.map { $0.element }
}
func _last(_ xs: V) -> V { rtItems(xs).last ?? .vNull }
func _headOption(_ xs: V) -> V { rtItems(xs).first ?? .vNull }
func _lastOption(_ xs: V) -> V { _last(xs) }
func _reverse(_ xs: V) -> V { rtArr(rtItems(xs).reversed()) }
func _distinct(_ xs: V) -> V {
    var r: [V] = []
    for e in rtItems(xs) where !r.contains(where: { rtEq($0, e) }) { r.append(e) }
    return rtArr(r)
}
func _sum(_ xs: V) -> V { rtItems(xs).reduce(.vInt(0), +) }
func _minList(_ xs: V) -> V { rtItems(xs).min { rtCompare($0, $1) < 0 } ?? .vNull }
/// `minBy` and `maxBy`: the first element whose key is least or greatest; none of an empty one.
func _minBy(_ xs: V, _ cl: V, _ least: Bool) throws -> V {
    let items = rtItems(xs)
    guard var best = items.first else { return .vNull }
    var bk = try RtInvoke(cl, rtArr([best]))
    for e in items.dropFirst() {
        let k = try RtInvoke(cl, rtArr([e]))
        if least ? rtCompare(k, bk) < 0 : rtCompare(k, bk) > 0 {
            best = e
            bk = k
        }
    }
    return best
}
func _maxList(_ xs: V) -> V { rtItems(xs).max { rtCompare($0, $1) < 0 } ?? .vNull }
func _arrFoldLeft(_ xs: V, _ z: V, _ cl: V) throws -> V {
    var acc = z
    for e in rtItems(xs) { acc = try RtInvoke(cl, rtArr([acc, e])) }
    return acc
}
func _zip(_ xs: V, _ ys: V) -> V {
    rtArr(zip(rtItems(xs), rtItems(ys)).map { rtObj(["_1": $0.0, "_2": $0.1]) })
}
func _zipWithIndex(_ xs: V) -> V {
    rtArr(rtItems(xs).enumerated().map { rtObj(["_1": $0.element, "_2": .vInt($0.offset)]) })
}
func _until(_ a: V, _ b: V) -> V {
    guard case .vInt(let x) = a, case .vInt(let y) = b, x < y else { return rtArr([]) }
    return rtArr((x..<y).map { .vInt($0) })
}
func _toIncl(_ a: V, _ b: V) -> V {
    guard case .vInt(let x) = a, case .vInt(let y) = b, x <= y else { return rtArr([]) }
    return rtArr((x...y).map { .vInt($0) })
}
func _sortedPrim(_ xs: V) -> V { rtArr(rtStableSort(rtItems(xs)) { rtCompare($0, $1) < 0 }) }
/** `xs.sorted` over Ordered elements: stable, calling each element's `compare` slot. */
func _sortedOrdered(_ xs: V) throws -> V {
    rtArr(try rtStableSort(rtItems(xs)) { a, b in try rtCompare(a["compare"](a, b), .vInt(0)) < 0 })
}
func Tuple2(_ a: V = .vNull, _ b: V = .vNull) -> V { rtObj(["_1": a, "_2": b]) }
func Tuple3(_ a: V = .vNull, _ b: V = .vNull, _ c: V = .vNull) -> V { rtObj(["_1": a, "_2": b, "_3": c]) }
func Tuple4(_ a: V = .vNull, _ b: V = .vNull, _ c: V = .vNull, _ d: V = .vNull) -> V { rtObj(["_1": a, "_2": b, "_3": c, "_4": d]) }
func wrapRefArray(_ a: V = .vNull) -> V { a }
func wrapIntArray(_ a: V = .vNull) -> V { a }
func wrapLongArray(_ a: V = .vNull) -> V { a }
func wrapDoubleArray(_ a: V = .vNull) -> V { a }
func wrapBooleanArray(_ a: V = .vNull) -> V { a }
func wrapByteArray(_ a: V = .vNull) -> V { a }
func wrapShortArray(_ a: V = .vNull) -> V { a }
func wrapCharArray(_ a: V = .vNull) -> V { a }
func wrapFloatArray(_ a: V = .vNull) -> V { a }
func intWrapper(_ x: V = .vNull) -> V { x }

// MARK: - Option (a nullable: Some(x) is x, None is null)

func _optMap(_ o: V, _ cl: V) throws -> V { if case .vNull = o { return .vNull }; return try RtInvoke(cl, rtArr([o])) }
@discardableResult
func _optForeach(_ o: V, _ cl: V) throws -> V { if case .vNull = o {} else { _ = try RtInvoke(cl, rtArr([o])) }; return .vNull }
func _getOrElse(_ o: V, _ d: V) -> V { if case .vNull = o { return d }; return o }
func _getOrElseThunk(_ o: V, _ alt: V) throws -> V { if case .vNull = o { return try RtForce(alt) }; return o }
func _optOrElse(_ o: V, _ alt: V) throws -> V { if case .vNull = o { return try RtForce(alt) }; return o }
func _optFilter(_ o: V, _ cl: V) throws -> V {
    if case .vNull = o { return .vNull }
    return try RtInvoke(cl, rtArr([o])).rtTrue ? o : .vNull
}
func _optFilterNot(_ o: V, _ cl: V) throws -> V {
    if case .vNull = o { return .vNull }
    return try RtInvoke(cl, rtArr([o])).rtTrue ? .vNull : o
}
func _optForall(_ o: V, _ cl: V) throws -> V { if case .vNull = o { return .vBool(true) }; return try RtInvoke(cl, rtArr([o])) }
func _optExists(_ o: V, _ cl: V) throws -> V { if case .vNull = o { return .vBool(false) }; return try RtInvoke(cl, rtArr([o])) }
func _optFold(_ o: V, _ ifEmpty: V, _ cl: V) throws -> V { if case .vNull = o { return ifEmpty }; return try RtInvoke(cl, rtArr([o])) }
func _optContains(_ o: V, _ x: V) -> V { if case .vNull = o { return .vBool(false) }; return _eq(o, x) }

// MARK: - Try / Either

func Success(_ v: V) -> V { rtObj(["_cls": .vStr("Success"), "value": v, "_fields": rtArr([.vStr("value")])]) }
func Failure(_ e: V) -> V {
    rtObj(["_cls": .vStr("Failure"), "exception": e, "error": e, "_fields": rtArr([.vStr("exception")])])
}
func RtLeft(_ v: V) -> V { rtObj(["_cls": .vStr("Left"), "value": v, "_fields": rtArr([.vStr("value")])]) }
func RtRight(_ v: V) -> V { rtObj(["_cls": .vStr("Right"), "value": v, "_fields": rtArr([.vStr("value")])]) }
func rtIsRight(_ t: V) -> Bool { let c = rtS(t["_cls"]); return c == "Success" || c == "Right" }
func _tryMap(_ t: V, _ cl: V) throws -> V {
    rtIsRight(t) ? rtObj(["_cls": t["_cls"], "value": try RtInvoke(cl, rtArr([t["value"]])), "_fields": t["_fields"]]) : t
}
func _tryFlatMap(_ t: V, _ cl: V) throws -> V { rtIsRight(t) ? try RtInvoke(cl, rtArr([t["value"]])) : t }
func _tryFold(_ t: V, _ fa: V, _ fb: V) throws -> V {
    let c = rtS(t["_cls"])
    if c == "Failure" || c == "Left" {
        var arg = t["error"]
        if case .vNull = arg { arg = t["value"] }
        return try RtInvoke(fa, rtArr([arg]))
    }
    return try RtInvoke(fb, rtArr([t["value"]]))
}
func _tryGetOrElse(_ t: V, _ d: V) -> V { rtIsRight(t) ? t["value"] : d }
func _tryToOption(_ t: V) -> V { rtIsRight(t) ? t["value"] : .vNull }
func _tryIsRight(_ t: V) -> V { .vBool(rtIsRight(t)) }
func _tryIsLeft(_ t: V) -> V { .vBool(!rtIsRight(t)) }
func _tryForeach(_ t: V, _ cl: V) throws -> V {
    if rtIsRight(t) { _ = try RtInvoke(cl, rtArr([t["value"]])) }
    return .vNull
}
func _tryExists(_ t: V, _ cl: V) throws -> V { rtIsRight(t) ? try RtInvoke(cl, rtArr([t["value"]])) : .vBool(false) }
func _tryForall(_ t: V, _ cl: V) throws -> V { rtIsRight(t) ? try RtInvoke(cl, rtArr([t["value"]])) : .vBool(true) }
func _tryContains(_ t: V, _ x: V) -> V { rtIsRight(t) ? _eq(t["value"], x) : .vBool(false) }
/// `orElse`: the alternative (a thunk) where there is no value.
func _tryOrElse(_ t: V, _ alt: V) throws -> V { rtIsRight(t) ? t : try RtForce(alt) }
/// `filterOrElse`: a Right whose value fails the test becomes a Left of the alternative (a thunk).
func _eitherFilterOrElse(_ t: V, _ cl: V, _ alt: V) throws -> V {
    if rtIsRight(t), !(try RtInvoke(cl, rtArr([t["value"]]))).rtTrue { return RtLeft(try RtForce(alt)) }
    return t
}
func _eitherSwap(_ t: V) -> V { rtIsRight(t) ? RtLeft(t["value"]) : RtRight(t["value"]) }
func _eitherMerge(_ t: V) -> V { t["value"] }

// MARK: - immutable Set (array-backed, `==` membership)

func _setHas(_ s: V, _ x: V) -> V { .vBool(rtItems(s).contains { rtEq($0, x) }) }
func _setAdd(_ s: V, _ x: V) -> V { _setHas(s, x).rtTrue ? rtArr(rtItems(s)) : rtArr(rtItems(s) + [x]) }
func _setRemove(_ s: V, _ x: V) -> V { rtArr(rtItems(s).filter { !rtEq($0, x) }) }
func _setDiff(_ a: V, _ b: V) -> V {
    if rtItems(a).isEmpty || rtItems(b).isEmpty { return a }
    return rtArr(rtItems(a).filter { !_setHas(b, $0).rtTrue })
}
func _setUnion(_ a: V, _ b: V) -> V {
    var r = rtItems(a)
    for e in rtItems(b) where !r.contains(where: { rtEq($0, e) }) { r.append(e) }
    return rtArr(r)
}
func _setIntersect(_ a: V, _ b: V) -> V { rtArr(rtItems(a).filter { _setHas(b, $0).rtTrue }) }

// MARK: - immutable Map with object keys (an entry list)

func _mapEmpty() -> V { rtObj(["_map": .vBool(true), "entries": rtArr([])]) }
func _rtIsEntryMap(_ m: V) -> Bool { rtHas(m, "_map") }
func _mapSize(_ m: V) -> V { _rtIsEntryMap(m) ? rtCount(m["entries"]) : rtCount(m) }
func _mapFind(_ m: V, _ k: V) -> Int { rtItems(m["entries"]).firstIndex { rtEq($0["k"], k) } ?? -1 }
func _mapGet(_ m: V, _ k: V) -> V {
    if case .vNull = m { return .vNull }
    if !_rtIsEntryMap(m) { return m[rtS(k)] }
    let i = _mapFind(m, k)
    return i < 0 ? .vNull : rtItems(m["entries"])[i]["v"]
}
/// A collection Scala reads as a function (`run.map(heights)`): a sequence as its element at an index, a map as its
/// value at a key, a set as whether it holds a value.
func _seqAsFn(_ xs: V) -> V { rtClosure({ env, args in env["xs"][args[V.vInt(0)]] }, rtObj(["xs": xs])) }
func _mapAsFn(_ m: V) -> V { rtClosure({ env, args in _mapGet(env["m"], args[V.vInt(0)]) }, rtObj(["m": m])) }
func _setAsFn(_ s: V) -> V { rtClosure({ env, args in _setHas(env["s"], args[V.vInt(0)]) }, rtObj(["s": s])) }
func _mapHas(_ m: V, _ k: V) -> V {
    if case .vNull = m { return .vBool(false) }
    if !_rtIsEntryMap(m) { return .vBool(rtHas(m, rtS(k))) }
    return .vBool(_mapFind(m, k) >= 0)
}
func _mapGetOrElse(_ m: V, _ k: V, _ d: V) -> V {
    if case .vBool(true) = _mapHas(m, k) { return _mapGet(m, k) }
    return d
}
func _mapPut(_ m0: V, _ k: V, _ v: V) -> V {
    let m: V
    if case .vNull = m0 { m = _mapEmpty() } else { m = m0 }
    var entries: [V] = []
    var replaced = false
    for e in rtItems(m["entries"]) {
        if !replaced && rtEq(e["k"], k) { entries.append(rtObj(["k": k, "v": v])); replaced = true }
        else { entries.append(e) }
    }
    if !replaced { entries.append(rtObj(["k": k, "v": v])) }
    return rtObj(["_map": .vBool(true), "entries": rtArr(entries)])
}
func _mapPutT(_ m: V, _ t: V) -> V { _mapPut(m, t["_1"], t["_2"]) }
func _mapRemove(_ m: V, _ k: V) -> V {
    if case .vNull = m { return _mapEmpty() }
    return rtObj(["_map": .vBool(true), "entries": rtArr(rtItems(m["entries"]).filter { !rtEq($0["k"], k) })])
}
func _mapKeys(_ m: V) -> V { rtArr(rtItems(m["entries"]).map { $0["k"] }) }
func _mapValues(_ m: V) -> V { rtArr(rtItems(m["entries"]).map { $0["v"] }) }
func _mapToList(_ m: V) -> V { rtArr(rtItems(m["entries"]).map { Tuple2($0["k"], $0["v"]) }) }
func _mapFromPairs(_ pairs: V) -> V { rtItems(pairs).reduce(_mapEmpty()) { _mapPut($0, $1["_1"], $1["_2"]) } }
func _mapConcat(_ m: V, _ pairs: V) -> V { rtItems(pairs).reduce(m) { _mapPut($0, $1["_1"], $1["_2"]) } }
func _mapRemoveAll(_ m: V, _ keys: V) -> V { rtItems(keys).reduce(m) { _mapRemove($0, $1) } }
func _mapEq(_ a: V, _ b: V) -> V {
    let ae = rtItems(a["entries"]), be = rtItems(b["entries"])
    if ae.count != be.count { return .vBool(false) }
    for e in ae {
        let i = _mapFind(b, e["k"])
        if i < 0 || !rtEq(be[i]["v"], e["v"]) { return .vBool(false) }
    }
    return .vBool(true)
}

// MARK: - rapid.Task (the network module)
// One thread: a task is a continuation-passing computation, { _cls: "Task", run: fn } where `run` takes the
// callback and the callback takes (ok, value). A Completable holds its waiters instead. `sync` returns only a
// task that completes at once; anything that would block is an error here. `sleep` is the main queue's timer.

// Closures the runtime invokes through RtInvoke are `{ fn, env }` objects whose `fn` takes (env, args), like the
// emitted ones; `run` takes the callback, a callback takes (ok, value).
func RtTask(_ run: @escaping (V) throws -> V) -> V {
    rtObj(["_cls": .vStr("Task"), "run": rtClosure({ _, args in try run(rtArg(rtItems(args), 0)) }, .vNull)])
}
func RtCb(_ f: @escaping (V, V) throws -> V) -> V {
    rtClosure({ _, args in let a = rtItems(args); return try f(rtArg(a, 0), rtArg(a, 1)) }, .vNull)
}
func rtThunk(_ f: @escaping () throws -> V) -> V { rtClosure({ _, _ in try f() }, .vNull) }
func rtB(_ v: V) -> Bool { if case .vBool(let b) = v { return b }; return false }
func rtCall(_ cb: V, _ ok: Bool, _ v: V) throws { _ = try RtInvoke(cb, rtArr([.vBool(ok), v])) }
/** The exception value of a thrown error, as the emitted `catch` sees it. */
func rtErrorValue(_ error: Error) -> V {
    if let e = error as? RtError { return e.v }
    return rtException("RuntimeException", "\(error)")
}
func RapidTask_run(_ t: V, _ cb: V) throws -> V {
    if case .vNull = t { try rtCall(cb, true, .vNull); return .vNull }
    if rtS(t["_cls"]) == "Completable" {
        if rtB(t["done"]) { try rtCall(cb, rtB(t["ok"]), t["value"]) }
        else if case .vArr(let ws) = t["waiters"] { ws.items.append(cb) }
        return .vNull
    }
    _ = try RtInvoke(t["run"], rtArr([cb]))
    return .vNull
}
func RapidTaskObj_pure(_ v: V) -> V { RtTask { cb in try rtCall(cb, true, v); return .vNull } }
func RapidTaskObj_unit() -> V { RapidTaskObj_pure(.vNull) }
func RapidTaskObj_error(_ e: V) -> V { RtTask { cb in try rtCall(cb, false, e); return .vNull } }
func RapidTaskObj_apply(_ thunk: V) -> V {
    RtTask { cb in
        let v: V
        do { v = try RtForce(thunk) } catch { try rtCall(cb, false, rtErrorValue(error)); return .vNull }
        try rtCall(cb, true, v)
        return .vNull
    }
}
func RapidTaskObj_defer(_ thunk: V) -> V {
    RtTask { cb in
        let t: V
        do { t = try RtForce(thunk) } catch { try rtCall(cb, false, rtErrorValue(error)); return .vNull }
        return try RapidTask_run(t, cb)
    }
}
func RapidTaskObj_sleep(_ d: V) -> V {
    let ms = rtDouble(RtDurationMs(d))
    return RtTask { cb in RtSetTimeout(ms, rtThunk { try rtCall(cb, true, .vNull); return .vNull }); return .vNull }
}
func RapidTaskObj_completable() -> V {
    rtObj(["_cls": .vStr("Completable"), "done": .vBool(false), "ok": .vBool(false), "value": .vNull, "waiters": rtArr([])])
}
func RtTaskComplete(_ c: V, _ ok: Bool, _ v: V) throws -> V {
    if rtB(c["done"]) { return .vNull }
    c["done"] = .vBool(true); c["ok"] = .vBool(ok); c["value"] = v
    let ws = rtItems(c["waiters"])
    c["waiters"] = rtArr([])
    for w in ws { try rtCall(w, ok, v) }
    return .vNull
}
func RapidTask_success(_ c: V, _ v: V) throws -> V { try RtTaskComplete(c, true, v) }
func RapidTask_failure(_ c: V, _ e: V) throws -> V { try RtTaskComplete(c, false, e) }
func RapidTask_map(_ t: V, _ f: V) -> V {
    RtTask { cb in
        try RapidTask_run(t, RtCb { ok, v in
            if !rtB(ok) { try rtCall(cb, false, v); return .vNull }
            let r: V
            do { r = try RtInvoke(f, rtArr([v])) } catch { try rtCall(cb, false, rtErrorValue(error)); return .vNull }
            try rtCall(cb, true, r)
            return .vNull
        })
    }
}
func RapidTask_flatMap(_ t: V, _ f: V) -> V {
    RtTask { cb in
        try RapidTask_run(t, RtCb { ok, v in
            if !rtB(ok) { try rtCall(cb, false, v); return .vNull }
            let nt: V
            do { nt = try RtInvoke(f, rtArr([v])) } catch { try rtCall(cb, false, rtErrorValue(error)); return .vNull }
            return try RapidTask_run(nt, cb)
        })
    }
}
func RapidTask_handleError(_ t: V, _ f: V) -> V {
    RtTask { cb in
        try RapidTask_run(t, RtCb { ok, v in
            if rtB(ok) { try rtCall(cb, true, v); return .vNull }
            let nt: V
            do { nt = try RtInvoke(f, rtArr([v])) } catch { try rtCall(cb, false, rtErrorValue(error)); return .vNull }
            return try RapidTask_run(nt, cb)
        })
    }
}
func RapidTask_guarantee(_ t: V, _ thunk: V) -> V {
    RtTask { cb in
        try RapidTask_run(t, RtCb { ok, v in
            try RapidTask_run(try RtForce(thunk), RtCb { _, _ in try rtCall(cb, rtB(ok), v); return .vNull })
        })
    }
}
/// task.attempt: always succeeds, with the task's outcome as a Try.
func RapidTask_attempt(_ t: V) -> V {
    RtTask { cb in
        try RapidTask_run(t, RtCb { ok, v in
            try rtCall(cb, true, rtB(ok) ? Success(v) : Failure(v))
            return .vNull
        })
    }
}
func RapidTask_timeout(_ t: V, _ d: V) -> V {
    let ms = rtDouble(RtDurationMs(d))
    return RtTask { cb in
        let state = rtObj(["fired": .vBool(false)])
        RtSetTimeout(ms, rtThunk {
            if rtB(state["fired"]) { return .vNull }
            state["fired"] = .vBool(true)
            try rtCall(cb, false, rtException("TimeoutException", "Task timed out after \(Int(ms)) ms"))
            return .vNull
        })
        return try RapidTask_run(t, RtCb { ok, v in
            if rtB(state["fired"]) { return .vNull }
            state["fired"] = .vBool(true)
            try rtCall(cb, rtB(ok), v)
            return .vNull
        })
    }
}
func RapidTask_unit(_ t: V) -> V { RapidTask_map(t, rtClosure({ _, _ in .vNull }, .vNull)) }
func RapidTask_foreach(_ t: V, _ f: V) throws -> V { try RapidTask_start(RapidTask_map(t, f)) }
func RapidTask_start(_ t: V) throws -> V {
    _ = try RapidTask_run(t, RtCb { ok, v in
        // a started task's failure nothing was there to hear: reported, as a thread's uncaught exception is
        if !rtB(ok) {
            print("[rt] task failed: " + rtS(_getMessage(v)))
            RtReportError("a task", RtError(v: v))
        }
        return .vNull
    })
    return RapidTaskObj_pure(.vNull)
}
func RapidTask_startUnit(_ t: V) throws -> V { _ = try RapidTask_start(t); return .vNull }
func RapidTask_sync(_ t: V) throws -> V {
    let box = rtObj(["done": .vBool(false), "ok": .vBool(false), "v": .vNull])
    _ = try RapidTask_run(t, RtCb { ok, v in box["done"] = .vBool(true); box["ok"] = ok; box["v"] = v; return .vNull })
    if !rtB(box["done"]) {
        try RtThrow(rtException("IllegalStateException", "Task.sync: the task did not complete at once (one thread)"))
    }
    if !rtB(box["ok"]) { try RtThrow(box["v"]) }
    return box["v"]
}
/** A one-shot timer on the main queue: `cb` (a zero-argument closure) runs after `ms`, held for the cycle collector
  * until then, and what it throws is reported (RtReportError) as nothing caught it. */
func RtSetTimeout(_ ms: Double, _ cb: V) {
    let held = RtHeld.hold(cb)
    DispatchQueue.main.asyncAfter(deadline: .now() + max(ms, 0) / 1000.0) {
        RtHeld.release(held)
        _ = RtGuard("a timer", cb, [])
    }
}

// MARK: - what the platform holds for the program, and what nothing caught

/** A closure value the program made: an object with its `fn` (a `.vFn` alone is a runtime's own slot). The emitted
  * code makes every lambda one of these, so a hook is checked with this and never with `case .vFn`. */
func rtIsFn(_ v: V) -> Bool {
    if case .vObj = v, case .vFn = v["fn"] { return true }
    return false
}

/** What the platform side holds for the program between its calls into it: a closure a timer, an observer or a view
  * keeps to call later. The cycle collector sees only what the program's own objects reach, so each is a root
  * (`RtHeld.roots`) until it is let go, or its fields would be cleared under it and the call later find nothing. */
enum RtHeld {
    static var values: [Int: V] = [:]
    static var next = 0
    /** More roots, asked for at each collection: a view's handlers, kept with the view and gone with it. */
    static var providers: [() -> [V]] = []

    /** `v` held until `release(the answer)`. */
    static func hold(_ v: V) -> Int {
        next += 1
        values[next] = v
        return next
    }
    static func release(_ id: Int) { values.removeValue(forKey: id) }

    /** Every root of the collection: the program's globals, then what is held here. */
    static func roots(_ more: [V]) -> [V] {
        var all: [V] = [ga, rtUnit, RtErrors.hook] + more
        all.append(contentsOf: values.values)
        for p in providers { all.append(contentsOf: p()) }
        return all
    }
}

/** What nothing caught: told to the app's hook (Platform.onError), counted, and written to the console. */
enum RtErrors {
    /** `(message, stack)`, a closure of the program's (uiOnError), or null. */
    static var hook: V = .vNull
    static var seen = 0
    static var reporting = false
}

/** `error`, thrown out of `place` (a key, a frame, a timer, a task) with nothing to catch it: written to the console
  * as `[error] in <place>: <class>: <message>` and handed to the app's hook as the message and a stack, which here is
  * where it was thrown out of, the Swift runtime having no Scala stack to give. A throw from the hook itself is
  * written and not told again. The app goes on, as a Roku channel does. */
func RtReportError(_ place: String, _ error: Error) {
    let v = rtErrorValue(error)
    var message = ""
    if case .vObj = v {
        let cls = rtS(_clsName(v))
        let said = rtS(_getMessage(v))
        message = said.isEmpty || said == cls ? cls : cls + ": " + said
    } else {
        message = rtS(v)
    }
    RtReportProblem(place, message, "thrown in " + place + ", and nothing caught it")
}

/** A problem with its message and stack as they are: written and told, as RtReportError does. */
func RtReportProblem(_ place: String, _ message: String, _ stack: String) {
    RtErrors.seen += 1
    print("[error] in " + place + ": " + message)
    if !rtIsFn(RtErrors.hook) || RtErrors.reporting { return }
    RtErrors.reporting = true
    do { _ = try RtInvoke(RtErrors.hook, rtArr([.vStr(message), .vStr(stack)])) }
    catch { print("[error] the error hook failed too: " + rtS(_getMessage(rtErrorValue(error)))) }
    RtErrors.reporting = false
}

/** Call `f`, a closure of the program's, from the platform (`place`: a key, a frame, a timer…): what it throws is
  * reported (RtReportError) and null answered, never swallowed. */
@discardableResult
func RtGuard(_ place: String, _ f: V, _ args: [V]) -> V {
    do { return try RtInvoke(f, rtArr(args)) }
    catch {
        RtReportError(place, error)
        return .vNull
    }
}
func rtDouble(_ v: V) -> Double {
    switch v {
    case .vInt(let i): return Double(i)
    case .vDbl(let d): return d
    case .vStr(let s): return Double(s) ?? 0
    default: return 0
    }
}

// MARK: - fabric.Json (the network module): JSON values are V — an object, an array, a string, a number, a bool, null

/// Text read as JSON; none, or text that is not JSON, throws as fabric's parser does (and the Roku runtime's).
func _jsonParse(_ text: V) throws -> V {
    guard case .vStr(let s) = text else { return try RtThrow(rtException("RuntimeException", "no JSON to parse")) }
    let body = s.trimmingCharacters(in: .whitespacesAndNewlines)
    if body.isEmpty { return try RtThrow(rtException("RuntimeException", "no JSON to parse")) }
    guard let any = try? JSONSerialization.jsonObject(with: Data(body.utf8), options: [.fragmentsAllowed]) else {
        return try RtThrow(rtException("RuntimeException", "not JSON: " + String(body.prefix(80))))
    }
    return rtFromJsonAny(any)
}
func rtFromJsonAny(_ any: Any) -> V {
    switch any {
    case let d as [String: Any]:
        var fields: [String: V] = [:]
        for (k, v) in d { fields[k] = rtFromJsonAny(v) }
        return rtObj(fields)
    case let a as [Any]: return rtArr(a.map(rtFromJsonAny))
    case let s as String: return .vStr(s)
    case let n as NSNumber:
        if CFGetTypeID(n) == CFBooleanGetTypeID() { return .vBool(n.boolValue) }
        let d = n.doubleValue
        if d == d.rounded() && abs(d) < 9.0e15 { return .vInt(Int(d)) }
        return .vDbl(d)
    default: return .vNull
    }
}
func rtToJsonAny(_ v: V) -> Any {
    switch v {
    case .vNull: return NSNull()
    case .vBool(let b): return b
    case .vInt(let i): return i
    case .vDbl(let d): return d
    case .vStr(let s): return s
    case .vObj(let o):
        var d: [String: Any] = [:]
        for (k, x) in o.fields { d[k] = rtToJsonAny(x) }
        return d
    case .vArr(let a): return a.items.map(rtToJsonAny)
    default: return rtS(v)
    }
}
func _jsonFormat(_ j: V) -> V {
    guard let data = try? JSONSerialization.data(withJSONObject: rtToJsonAny(j), options: [.fragmentsAllowed]),
          let s = String(data: data, encoding: .utf8) else { return .vStr("null") }
    return .vStr(s)
}
func _jsonGet(_ j: V, _ key: V) -> V {
    switch j {
    case .vObj: return j[rtS(key)]
    case .vArr: return j[key]
    default: return .vNull
    }
}
func _jsonOpt(_ j: V, _ key: V) -> V { _jsonGet(j, key) }
func _jsonObj(_ pairs: V) -> V {
    var fields: [String: V] = [:]
    for p in rtItems(pairs) { fields[rtS(p["_1"])] = p["_2"] }
    return rtObj(fields)
}
func _json_asString(_ j: V) -> V { _str(j) }
func _json_asLong(_ j: V) -> V { if case .vStr(let s) = j { return .vInt(Int(s) ?? 0) }; return j }
func _json_asInt(_ j: V) -> V { _json_asLong(j) }
func _json_asDouble(_ j: V) -> V { if case .vStr(let s) = j { return .vDbl(Double(s) ?? 0) }; return j }
func _json_asFloat(_ j: V) -> V { _json_asDouble(j) }
func _json_asBoolean(_ j: V) -> V { j }
func _json_asVector(_ j: V) -> V { j }
func _json_asArr(_ j: V) -> V { j }
func _json_asObj(_ j: V) -> V { j }
func _json_asMap(_ j: V) -> V { j }
func _json_isNull(_ j: V) -> V { if case .vNull = j { return .vBool(true) }; return .vBool(false) }
func _json_isObj(_ j: V) -> V { if case .vObj = j { return .vBool(true) }; return .vBool(false) }
func _json_isArr(_ j: V) -> V { if case .vArr = j { return .vBool(true) }; return .vBool(false) }
func _json_isStr(_ j: V) -> V { if case .vStr = j { return .vBool(true) }; return .vBool(false) }
/// Json.value: an object's fields as a Scala Map; anything else is its own value.
func _json_value(_ j: V) -> V {
    guard case .vObj(let o) = j else { return j }
    return o.fields.keys.sorted().reduce(_mapEmpty()) { _mapPut($0, .vStr($1), o.fields[$1] ?? .vNull) }
}
func _json_toString(_ j: V) -> V { _jsonFormat(j) }
/// fabric.rw: codecs. A primitive, a String or a Json value is itself (`_rw(name)`); a case class reads and writes
/// by its field names with a codec per field (`_rwGen`, the emitter's derivation of `RW.gen`); Option, List and
/// Map wrap their element's codec.
func _rw(_ name: V) -> V { rtObj(["_cls": .vStr("RW"), "name": name]) }
func _rwGen(_ cls: V, _ keys: V, _ slots: V, _ rws: V, _ ctor: V, _ defs: V = .vNull, _ has: V = .vNull) -> V {
    rtObj(["_cls": .vStr("RW"), "kind": .vStr("case"), "cls": cls, "keys": keys, "slots": slots, "rws": rws, "ctor": ctor,
           "defs": defs, "has": has])
}
func _rwOpt(_ inner: V) -> V { rtObj(["_cls": .vStr("RW"), "kind": .vStr("opt"), "inner": inner]) }
func _rwList(_ inner: V) -> V { rtObj(["_cls": .vStr("RW"), "kind": .vStr("list"), "inner": inner]) }
func _rwMap(_ inner: V) -> V { rtObj(["_cls": .vStr("RW"), "kind": .vStr("map"), "inner": inner]) }
/// JSON → value
func _rwRead(_ rw: V, _ j: V) throws -> V {
    guard case .vObj = rw, case .vStr(let kind) = rw["kind"] else { return j }
    if case .vNull = j { return j }
    switch kind {
    case "opt": return try _rwRead(rw["inner"], j)
    case "list": return rtArr(try rtItems(j).map { try _rwRead(rw["inner"], $0) })
    case "map":
        var m = _mapEmpty()
        if case .vObj(let o) = j { for (k, x) in o.fields { m = try _mapPut(m, .vStr(k), try _rwRead(rw["inner"], x)) } }
        return m
    default:
        let keys = rtItems(rw["keys"]), slots = rtItems(rw["slots"]), rws = rtItems(rw["rws"])
        var args: [V] = []
        for i in keys.indices { args.append(try _rwRead(rws[i], j[rtS(keys[i])])) }
        if case .vFn(let f) = rw["ctor"] { return try f.call(args) }
        let o = rtObj(["_cls": rw["cls"], "_fields": rw["slots"]])
        for i in slots.indices { o[rtS(slots[i])] = args[i] }
        return o
    }
}
/// value → JSON
func _rwWrite(_ rw: V, _ v: V) throws -> V {
    guard case .vObj = rw, case .vStr(let kind) = rw["kind"] else { return v }
    if case .vNull = v { return v }
    switch kind {
    case "opt": return try _rwWrite(rw["inner"], v)
    case "list": return rtArr(try rtItems(v).map { try _rwWrite(rw["inner"], $0) })
    case "map":
        var fields: [String: V] = [:]
        for t in rtItems(_mapToList(v)) { fields[rtS(t["_1"])] = try _rwWrite(rw["inner"], t["_2"]) }
        return rtObj(fields)
    default:
        let keys = rtItems(rw["keys"]), slots = rtItems(rw["slots"]), rws = rtItems(rw["rws"])
        var fields: [String: V] = [:]
        for i in keys.indices { fields[rtS(keys[i])] = try _rwWrite(rws[i], v[rtS(slots[i])]) }
        return rtObj(fields)
    }
}
/// A Scala 3 enum's simple case: one singleton per (enum, case), named by `toString`, ordered by `ordinal`.
func RtEnum(_ cls: V, _ name: V, _ ordinal: V) -> V {
    let key = "__enum_" + rtS(cls) + "_" + rtS(name)
    if case .vObj = ga[key] { return ga[key] }
    let e = rtObj(["_cls": cls, "_enum": name, "ordinal": ordinal, "toString": rtFn { (s: V) in s["_enum"] }])
    ga[key] = e
    return e
}
/// A case of an enum with members (`<Enum>__cases()`): an RtEnum given the enum's slots (its own toString among them,
/// where it has one), put with the cases made before it; its `__init` runs on it next.
func RtEnumOf(_ cs: V, _ vt: V, _ cls: V, _ name: V, _ ordinal: V) -> V {
    let e = rtObj(["_cls": cls, "_enum": name, "ordinal": ordinal, "toString": rtFn { (s: V) in s["_enum"] }])
    if case .vObj(let t) = vt, case .vObj(let o) = e {
        for (k, f) in t.fields where k != "_cls" { o.fields[k] = f }
    }
    if case .vArr(let a) = cs { a.items.append(e) }
    return e
}
/// An enum with members' `values`: its cases, in an array of their own.
func _enumValues(_ cs: V) -> V { rtArr(rtItems(cs)) }
/// An Ordered value of the program's compared with another by its `compare` (`a < b` is `_ordCompare(a, b) < 0`).
func _ordCompare(_ a: V, _ b: V) throws -> V { try a["compare"](a, b) }
/// The greatest (`max`) or least of Ordered values, the first of equals, as Scala's `max` and `min` are.
func _extremeOrdered(_ xs: V, _ greatest: Bool) throws -> V {
    let items = rtItems(xs)
    guard var best = items.first else { return try RtThrow(rtException("UnsupportedOperationException", "empty.max")) }
    for e in items {
        let c = rtCompare(try _ordCompare(e, best), .vInt(0))
        if (greatest && c > 0) || (!greatest && c < 0) { best = e }
    }
    return best
}
/// An enum's `valueOf(name)` over its `values`, throwing as the JVM does for no such case.
func _enumValueOf(_ values: V, _ name: V) throws -> V {
    if let e = rtItems(values).first(where: { rtS($0["_enum"]) == rtS(name) }) { return e }
    return try RtThrow(IllegalArgumentException_new(.vStr("enum case not found: " + rtS(name))))
}
/// An enum's `fromOrdinal(i)` over its `values`, throwing as the JVM does for no such case.
func _enumFromOrdinal(_ values: V, _ i: V) throws -> V {
    let items = rtItems(values)
    guard case .vInt(let n) = rtToInt(i), n >= 0, n < items.count else {
        return try RtThrow(rtException("NoSuchElementException", rtS(i)))
    }
    return items[n]
}

// MARK: - java.lang.Object: `new Object` is a lock (a synchronized block is just the block), so any distinct value does

func Object_new() -> V { rtObj(["_cls": .vStr("Object")]) }

// MARK: - java.util.concurrent.ConcurrentHashMap, AtomicLong / AtomicInteger, UUID, System, scribe

func ConcurrentHashMap_new() -> V { rtObj(["_cls": .vStr("ConcurrentHashMap"), "m": rtObj([:])]) }
func ConcurrentHashMap_put(_ s: V, _ k: V, _ v: V) -> V { let m = s["m"]; let old = m[rtS(k)]; m[rtS(k)] = v; return old }
func ConcurrentHashMap_get(_ s: V, _ k: V) -> V { s["m"][rtS(k)] }
func ConcurrentHashMap_remove(_ s: V, _ k: V) -> V {
    let m = s["m"]; let old = m[rtS(k)]
    if case .vObj(let o) = m { o.fields.removeValue(forKey: rtS(k)) }
    return old
}
func ConcurrentHashMap_getOrDefault(_ s: V, _ k: V, _ d: V) -> V { rtHas(s["m"], rtS(k)) ? s["m"][rtS(k)] : d }
func ConcurrentHashMap_containsKey(_ s: V, _ k: V) -> V { .vBool(rtHas(s["m"], rtS(k))) }
func ConcurrentHashMap_computeIfAbsent(_ s: V, _ k: V, _ f: V) throws -> V {
    let m = s["m"]
    if rtHas(m, rtS(k)) { return m[rtS(k)] }
    let v = try RtInvoke(f, rtArr([k]))
    m[rtS(k)] = v
    return v
}
func ConcurrentHashMap_values(_ s: V) -> V { if case .vObj(let o) = s["m"] { return rtArr(Array(o.fields.values)) }; return rtArr([]) }
func ConcurrentHashMap_size(_ s: V) -> V { if case .vObj(let o) = s["m"] { return .vInt(o.fields.count) }; return .vInt(0) }
func ConcurrentHashMap_isEmpty(_ s: V) -> V { if case .vObj(let o) = s["m"] { return .vBool(o.fields.isEmpty) }; return .vBool(true) }
func ConcurrentHashMap_clear(_ s: V) -> V { if case .vObj(let o) = s["m"] { o.fields.removeAll() }; return .vNull }
func ConcurrentHashMap_forEach(_ s: V, _ f: V) throws -> V {
    if case .vObj(let o) = s["m"] { for (k, v) in o.fields { _ = try RtInvoke(f, rtArr([.vStr(k), v])) } }
    return .vNull
}
func ConcurrentLinkedQueue_new() -> V { rtObj(["_cls": .vStr("ConcurrentLinkedQueue"), "items": rtArr([])]) }
func ConcurrentLinkedQueue_add(_ s: V, _ x: V) -> V { if case .vArr(let a) = s["items"] { a.items.append(x) }; return .vBool(true) }
func ConcurrentLinkedQueue_offer(_ s: V, _ x: V) -> V { ConcurrentLinkedQueue_add(s, x) }
func ConcurrentLinkedQueue_poll(_ s: V) -> V {
    if case .vArr(let a) = s["items"], !a.items.isEmpty { return a.items.removeFirst() }
    return .vNull
}
func ConcurrentLinkedQueue_peek(_ s: V) -> V { rtItems(s["items"]).first ?? .vNull }
func ConcurrentLinkedQueue_isEmpty(_ s: V) -> V { .vBool(rtItems(s["items"]).isEmpty) }
func ConcurrentLinkedQueue_size(_ s: V) -> V { .vInt(rtItems(s["items"]).count) }
func ConcurrentLinkedQueue_clear(_ s: V) -> V { if case .vArr(let a) = s["items"] { a.items.removeAll() }; return .vNull }
func AtomicLong_new(_ v: V = .vInt(0)) -> V { rtObj(["_cls": .vStr("AtomicLong"), "value": v]) }
func AtomicInteger_new(_ v: V = .vInt(0)) -> V { rtObj(["_cls": .vStr("AtomicInteger"), "value": v]) }
func AtomicLong_get(_ s: V) -> V { s["value"] }
func AtomicLong_set(_ s: V, _ v: V) -> V { s["value"] = v; return .vNull }
func AtomicLong_incrementAndGet(_ s: V) -> V { s["value"] = s["value"] + .vInt(1); return s["value"] }
func AtomicLong_decrementAndGet(_ s: V) -> V { s["value"] = s["value"] - .vInt(1); return s["value"] }
func AtomicLong_getAndIncrement(_ s: V) -> V { let old = s["value"]; s["value"] = old + .vInt(1); return old }
func AtomicLong_addAndGet(_ s: V, _ d: V) -> V { s["value"] = s["value"] + d; return s["value"] }
func AtomicLong_compareAndSet(_ s: V, _ expect: V, _ update: V) -> V {
    if rtPrimEq(s["value"], expect) { s["value"] = update; return .vBool(true) }
    return .vBool(false)
}
func _uuid() -> V { .vStr(UUID().uuidString.lowercased()) }
func _nowMillis() -> V { .vInt(Int(Date().timeIntervalSince1970 * 1000)) }
func Scribe_log(_ level: V, _ msgs: V) throws -> V {
    var s = ""
    for m in rtItems(msgs) { s += rtS(try RtForce(m)) }
    print("[" + rtS(level) + "] " + s)
    return .vNull
}
func _getMessage(_ e: V) -> V {
    if case .vObj = e, rtHas(e, "message") { return e["message"] }
    return _str(e)
}
func _clsName(_ x: V) -> V {
    if case .vObj = x, rtHas(x, "_name") { return _str(x["_name"]) }
    if case .vObj = x, rtHas(x, "_cls") { return _str(x["_cls"]) }
    return .vStr(rtKindName(x))
}
// x.getClass, for comparing: its class's name (not a case object's own, which _clsName gives), else its kind
func _clsOf(_ x: V) -> V {
    if case .vObj = x, rtHas(x, "_cls") { return _str(x["_cls"]) }
    return .vStr(rtKindName(x))
}
func _regex(_ pattern: V) -> V { .vRef(try! NSRegularExpression(pattern: rtS(pattern))) }
/// A Regex's first match in a String, as its groups (the whole match first), or None.
func _regexFirstMatch(_ re: V, _ s: V) -> V {
    guard case .vRef(let r) = re, let rx = r as? NSRegularExpression else { return .vNull }
    let str = rtS(s), ns = str as NSString
    guard let m = rx.firstMatch(in: str, range: NSRange(location: 0, length: ns.length)) else { return .vNull }
    let groups: [V] = (0..<m.numberOfRanges).map { i in
        let g = m.range(at: i)
        return g.location == NSNotFound ? .vNull : .vStr(ns.substring(with: g))
    }
    return rtObj(["_cls": .vStr("Match"), "groups": rtArr(groups)])
}
func _matchGroup(_ m: V, _ i: V) -> V { m["groups"][i] }
func _strMatches(_ s: V, _ p: V) -> V { .vBool(rtS(s).range(of: "^(?:" + rtS(p) + ")$", options: .regularExpression) != nil) }

// MARK: - java.util.concurrent.atomic.AtomicBoolean, java.lang.ThreadLocal (one thread)

func AtomicBoolean_new(_ v: V = .vBool(false)) -> V { rtObj(["_cls": .vStr("AtomicBoolean"), "value": v]) }
func AtomicBoolean_get(_ s: V) -> V { s["value"] }
func AtomicBoolean_set(_ s: V, _ v: V) -> V { s["value"] = v; return .vNull }
func AtomicBoolean_compareAndSet(_ s: V, _ expect: V, _ update: V) -> V {
    if rtPrimEq(s["value"], expect) { s["value"] = update; return .vBool(true) }
    return .vBool(false)
}

@discardableResult
func ThreadLocal__install(_ s: V) -> V { s["initialValue"] = rtFn(ThreadLocal_initialValue); return .vNull }
@discardableResult
func ThreadLocal_init(_ s: V) -> V { s["__tlSet"] = .vBool(false); s["__tlValue"] = .vNull; return .vNull }
func ThreadLocal_new() -> V {
    let s = rtNew("ThreadLocal")
    ThreadLocal__install(s)
    ThreadLocal_init(s)
    return s
}
func ThreadLocal_initialValue(_ s: V) -> V { .vNull }
func ThreadLocal_get(_ s: V) throws -> V {
    if !s["__tlSet"].rtTrue {
        s["__tlValue"] = try s["initialValue"](s)
        s["__tlSet"] = .vBool(true)
    }
    return s["__tlValue"]
}
func ThreadLocal_set(_ s: V, _ v: V) -> V { s["__tlValue"] = v; s["__tlSet"] = .vBool(true); return .vNull }
func ThreadLocal_remove(_ s: V) -> V { s["__tlSet"] = .vBool(false); s["__tlValue"] = .vNull; return .vNull }

// MARK: - scala.concurrent Promise / Future (callback-completed, one thread)

func Promise_new() -> V {
    rtObj(["_cls": .vStr("Promise"), "done": .vBool(false), "value": .vNull, "callbacks": rtArr([])])
}
func _promiseSuccess(_ p: V, _ v: V) throws -> V {
    if p["done"].rtTrue { try RtThrow(rtException("IllegalStateException", "Promise already completed.")) }
    p["done"] = .vBool(true)
    p["value"] = v
    let callbacks = rtItems(p["callbacks"])
    p["callbacks"] = rtArr([])
    for cb in callbacks { _ = try RtInvoke(cb, rtArr([v])) }
    return p
}
func _futureOnSuccess(_ f: V, _ cb: V) throws -> V {
    if f["done"].rtTrue { _ = try RtInvoke(cb, rtArr([f["value"]])) }
    else { f["callbacks"] = _append(f["callbacks"], cb) }
    return .vNull
}
func _futureMap(_ f: V, _ fn: V) throws -> V {
    let r = Promise_new()
    _ = try _futureOnSuccess(f, rtClosure({ env, args in try _promiseSuccess(env["r"], try RtInvoke(env["fn"], rtArr([args[.vInt(0)]]))) },
                                          rtObj(["r": r, "fn": fn])))
    return r
}

// MARK: - case classes

/** `c.copy(field = x)`: a shallow copy with the given fields replaced. */
func _caseCopy(_ src: V, _ overrides: V) -> V {
    guard case .vObj(let s) = src, case .vObj(let o) = overrides else { return src }
    var fields = s.fields
    fields["_id"] = nil
    for (k, v) in o.fields { fields[k] = v }
    return .vObj(Obj(fields))
}

// MARK: - cycle collection

/** Mark everything reachable from `roots`; clear the fields of every other instance, breaking its cycles so
  * reference counting frees it. Returns how many instances were cleared. Call only where no emitted code is
  * on the stack. */
@discardableResult
func rtCollectCycles(_ roots: [V]) -> Int {
    Rt.epoch &+= 1
    if Rt.epoch == 0 { Rt.epoch = 1 }
    let epoch = Rt.epoch
    var stack: [V] = roots
    stack.reserveCapacity(1024)
    while let v = stack.popLast() {
        switch v {
        case .vObj(let o):
            if o.mark != epoch {
                o.mark = epoch
                for f in o.fields.values {
                    switch f {
                    case .vObj, .vArr: stack.append(f)
                    default: break
                    }
                }
            }
        case .vArr(let a):
            if a.mark != epoch {
                a.mark = epoch
                for f in a.items {
                    switch f {
                    case .vObj, .vArr: stack.append(f)
                    default: break
                    }
                }
            }
        default:
            break
        }
    }
    // The sweep holds the garbage strongly while their fields are cleared: an object that frees during the
    // clearing unlinks itself from the list, so the list is not walked at the same time.
    var garbage: [Obj] = []
    var o = Rt.sentinel.next
    while o !== Rt.sentinel {
        if o.mark != epoch { garbage.append(o) }
        o = o.next
    }
    for g in garbage { g.fields.removeAll() }
    Rt.allocated = 0
    return garbage.count
}

// MARK: - the rest of the Roku runtime's library (roku's ScalaRuntime.brs), one for one

/// Scala's arithmetic where it can fail: a whole number's quotient and remainder by zero throw ArithmeticException, as
/// the JVM's do (Swift would trap); a fraction's division is IEEE's (x / 0.0 is an infinity, 0.0 / 0.0 NaN).
func _div(_ a: V, _ b: V) -> V { .vDbl((rtD(a) ?? 0) / (rtD(b) ?? 0)) }
func _quot(_ a: V, _ b: V) throws -> V {
    if case .vInt(let x) = a, case .vInt(let y) = b {
        if y == 0 { _ = try RtThrow(rtException("ArithmeticException", "/ by zero")) }
        return .vInt(x == Int.min && y == -1 ? x : x / y)
    }
    return rtToInt(_div(a, b))
}
func _rem(_ a: V, _ b: V) throws -> V {
    if case .vInt(let x) = a, case .vInt(let y) = b {
        if y == 0 { _ = try RtThrow(rtException("ArithmeticException", "/ by zero")) }
        return .vInt(y == -1 ? 0 : x % y)
    }
    return a % b
}
/// A String's character at an index (a Char is a one-character String). Strings count in UTF-16 units, as the JVM's
/// do, but a Swift String cannot hold half a surrogate pair: at the first half the whole pair is given, and at the
/// second nothing, so a walk that copies a string a character at a time still copies its emoji whole.
func rtCharAt(_ s: V, _ i: V) throws -> V {
    let str = rtS(s), u = str.utf16
    guard case .vInt(let n) = i, n >= 0, n < u.count else {
        return try RtThrow(rtException("StringIndexOutOfBoundsException", "index \(rtS(i)), length \(u.count)"))
    }
    let at = u.index(u.startIndex, offsetBy: n)
    let unit = u[at]
    if UTF16.isLeadSurrogate(unit) {
        let next = u.index(after: at)
        if next < u.endIndex, UTF16.isTrailSurrogate(u[next]) { return .vStr(String(str[at..<u.index(after: next)])) }
    }
    if UTF16.isTrailSurrogate(unit), at > u.startIndex, UTF16.isLeadSurrogate(u[u.index(before: at)]) {
        return .vStr("")
    }
    return .vStr(String(decoding: [unit], as: UTF16.self))
}
/// The next place of a character or a string at or after an index, and the last at or before one (Java's
/// `indexOf(x, from)` and `lastIndexOf(x, from)`), by UTF-16 unit.
func _strIndexFrom(_ s: V, _ x: V, _ from: V) -> V {
    let hay = Array(rtS(s).utf16), needle = Array(rtCharOrString(x).utf16)
    let start = max(0, Int(rtDouble(from)))
    if start > hay.count { return .vInt(needle.isEmpty ? hay.count : -1) }
    if needle.isEmpty { return .vInt(start) }
    if needle.count > hay.count - start { return .vInt(-1) }
    for i in start...(hay.count - needle.count) where Array(hay[i..<(i + needle.count)]) == needle { return .vInt(i) }
    return .vInt(-1)
}
func _strLastIndexFrom(_ s: V, _ x: V, _ from: V) -> V {
    let hay = Array(rtS(s).utf16), needle = Array(rtCharOrString(x).utf16)
    let start = min(Int(rtDouble(from)), hay.count - needle.count)
    if start < 0 { return .vInt(-1) }
    for i in stride(from: start, through: 0, by: -1) where Array(hay[i..<(i + needle.count)]) == needle {
        return .vInt(i)
    }
    return .vInt(-1)
}
func _strIndexOf(_ s: V, _ x: V) -> V { rtIndexOf(s, x) }
func _strLastIndexOf(_ s: V, _ x: V) -> V { rtLastIndexOf(s, x) }
/// A String's leading characters while a test holds (`takeWhile`, `keeping`), or what follows them (`dropWhile`).
func _strTakeWhile(_ s: V, _ cl: V, _ keeping: Bool) throws -> V {
    let cs = rtChars(s)
    var at = cs.count
    for (i, c) in cs.enumerated() where !(try RtInvoke(cl, rtArr([c])).rtTrue) { at = i; break }
    return .vStr((keeping ? cs[..<at] : cs[at...]).map(rtS).joined())
}
/// A String's characters that pass a test (`filter`, keep) or fail it (`filterNot`).
func _strFilter(_ s: V, _ cl: V, _ keep: Bool) throws -> V {
    .vStr(try rtChars(s).filter { try RtInvoke(cl, rtArr([$0])).rtTrue == keep }.map(rtS).joined())
}
func _strCapitalize(_ s: V) -> V {
    let t = rtS(s)
    guard let first = t.first else { return .vStr("") }
    return .vStr(String(first).uppercased() + t.dropFirst())
}
func _strCompare(_ s: V, _ o: V) -> V { .vInt(rtCompare(_str(s), _str(o))) }
/// A String read as a whole number or a fraction (`toInt`, `toDouble`), throwing NumberFormatException as Java does.
func _strToInt(_ s: V) throws -> V {
    let n = _strToIntOpt(s)
    if case .vNull = n { _ = try RtThrow(rtException("NumberFormatException", "For input string: \"\(rtS(s))\"")) }
    return n
}
func _strToDouble(_ s: V) throws -> V {
    let n = _strToDoubleOpt(s)
    if case .vNull = n { _ = try RtThrow(rtException("NumberFormatException", "For input string: \"\(rtS(s))\"")) }
    return n
}
/// RichInt's toHexString: a negative number's as its unsigned 32 bits, as Integer.toHexString.
func _toHex(_ n: V) -> V { _hexOf(n) }
/// A String walked as a collection (StringOps: exists, forall, count, foreach…): its characters.
func rtChars(_ s: V) -> [V] { rtS(s).unicodeScalars.map { .vStr(String($0)) } }

/// java.lang.Character's tests on a Char (a one-character String) or a code, as Java answers them.
private func rtScalar(_ c: V) -> Unicode.Scalar? {
    if case .vInt(let n) = c { return Unicode.Scalar(UInt32(truncatingIfNeeded: n)) }
    return rtS(c).unicodeScalars.first
}
func _ch_isDigit(_ c: V) -> V { .vBool(rtScalar(c)?.properties.numericType == .decimal) }
func _ch_isLetter(_ c: V) -> V { .vBool(rtScalar(c)?.properties.isAlphabetic ?? false) }
func _ch_isLetterOrDigit(_ c: V) -> V { .vBool(_ch_isLetter(c).rtTrue || _ch_isDigit(c).rtTrue) }
func _ch_isLower(_ c: V) -> V { .vBool(rtScalar(c)?.properties.isLowercase ?? false) }
func _ch_isUpper(_ c: V) -> V { .vBool(rtScalar(c)?.properties.isUppercase ?? false) }
func _ch_isSpaceChar(_ c: V) -> V {
    guard let s = rtScalar(c) else { return .vBool(false) }
    let k = s.properties.generalCategory
    return .vBool(k == .spaceSeparator || k == .lineSeparator || k == .paragraphSeparator)
}
func _ch_isWhitespace(_ c: V) -> V {
    guard let s = rtScalar(c) else { return .vBool(false) }
    if (9...13).contains(s.value) || (28...31).contains(s.value) { return .vBool(true) }
    // Java: a space separator but the no-break ones
    return .vBool(_ch_isSpaceChar(c).rtTrue && ![0x00A0, 0x2007, 0x202F].contains(s.value))
}

/// fabric's Json kinds a value of the network module answers.
func _json_isNum(_ j: V) -> V { .vBool(rtKindName(j) == "n") }
func _json_isBoolean(_ j: V) -> V { .vBool(rtKindName(j) == "b") }

/// `new Array[T](n)`: n slots, each `z` (what the JVM starts them at).
func _arrNew(_ n: V, _ z: V) -> V { rtArr(Array(repeating: z, count: max(0, Int(rtDouble(n))))) }
/// `Seq.tabulate(n)(f)` and `Seq.fill(n)(x)`, x evaluated for every slot.
func _tabulate(_ n: V, _ cl: V) throws -> V {
    rtArr(try (0..<max(0, Int(rtDouble(n)))).map { try RtInvoke(cl, rtArr([.vInt($0)])) })
}
func _fill(_ n: V, _ thunk: V) throws -> V { rtArr(try (0..<max(0, Int(rtDouble(n)))).map { _ in try RtForce(thunk) }) }
/// `Iterator.iterate(z)(f).takeWhile(p)`, made whole.
func _iterateWhile(_ z: V, _ f: V, _ p: V) throws -> V {
    var xs: [V] = []
    var x = z
    while try RtInvoke(p, rtArr([x])).rtTrue {
        xs.append(x)
        x = try RtInvoke(f, rtArr([x]))
    }
    return rtArr(xs)
}
/// `start until end by step` and `to … by …` (`incl`), and `Range(a, b)`.
func _rangeBy(_ a: V, _ b: V, _ stride: V, _ incl: Bool) -> V {
    var r: [V] = []
    let step = rtDouble(stride)
    if step == 0 { return rtArr(r) }
    var v = a
    while (step > 0 ? rtCompare(v, b) < 0 : rtCompare(v, b) > 0) || (incl && rtCompare(v, b) == 0) {
        r.append(v)
        v = v + stride
    }
    return rtArr(r)
}
/// A Map built from pairs (what `map` on a Map gives): the Roku keeps one of String keys in an object of its own, but
/// every Map here is an entry list, which each of the Map helpers reads.
func _mapOfPairs(_ pairs: V) -> V { _mapFromPairs(pairs) }
/// `xs.lift` and `xs.lift(i)`: the element at an index, or None (null) past either end.
func _lift(_ xs: V, _ i: V) -> V {
    let items = rtSeqItems(xs)
    guard case .vInt(let n) = i, n >= 0, n < items.count else { return .vNull }
    return items[n]
}
func _liftCall(_ env: V, _ args: V) throws -> V { _lift(env["xs"], args[V.vInt(0)]) }
func _seqAsFnCall(_ env: V, _ args: V) throws -> V { env["xs"][args[V.vInt(0)]] }
func _mapAsFnCall(_ env: V, _ args: V) throws -> V { _mapGet(env["m"], args[V.vInt(0)]) }
func _setAsFnCall(_ env: V, _ args: V) throws -> V { _setHas(env["s"], args[V.vInt(0)]) }
/// A collection's elements, a String's its characters.
func rtSeqItems(_ xs: V) -> [V] { if case .vStr = xs { return rtChars(xs) }; return rtItems(xs) }
func _flatten(_ xs: V) -> V {
    var r: [V] = []
    for e in rtItems(xs) {
        if case .vArr(let a) = e { r.append(contentsOf: a.items) } else if case .vNull = e {} else { r.append(e) }
    }
    return rtArr(r)
}
/// The least or greatest element (`minOption`, `least`; `maxOption`), None (null) for none.
func _minOption(_ xs: V, _ least: Bool) -> V {
    if rtItems(xs).isEmpty { return .vNull }
    return least ? _minList(xs) : _maxList(xs)
}
/// `xs.patch(from, other, replaced)`.
func _patch(_ xs: V, _ from: V, _ other: V, _ replaced: V) -> V {
    let items = rtItems(xs), f = max(0, Int(rtDouble(from))), n = max(0, Int(rtDouble(replaced)))
    let at = min(f, items.count)
    return rtArr(Array(items[..<at]) + rtSeqItems(other) + Array(items[min(items.count, at + n)...]))
}
/// `dropRight(n)` of a collection or a String; `slice(from, until)`.
func _dropRight(_ xs: V, _ n: V) -> V {
    let k = max(0, Int(rtDouble(n)))
    if case .vStr(let s) = xs { return .vStr(String(s.utf16.dropLast(k)) ?? "") }
    return rtArr(Array(rtItems(xs).dropLast(k)))
}
func _slice(_ xs: V, _ from: V, _ until: V) -> V {
    let items = rtItems(xs)
    let a = min(max(0, Int(rtDouble(from))), items.count), b = min(max(a, Int(rtDouble(until))), items.count)
    return rtArr(Array(items[a..<b]))
}
/// `takeWhile` (`keeping`) and `dropWhile` of a collection.
func _takeWhile(_ xs: V, _ cl: V, _ keeping: Bool) throws -> V {
    let items = rtItems(xs)
    var at = items.count
    for (i, x) in items.enumerated() where !(try RtInvoke(cl, rtArr([x])).rtTrue) { at = i; break }
    return rtArr(Array(keeping ? items[..<at] : items[at...]))
}
func _arrIndexWhereFrom(_ xs: V, _ cl: V, _ from: V) throws -> V {
    let items = rtSeqItems(xs)
    var i = max(0, Int(rtDouble(from)))
    while i < items.count {
        if try RtInvoke(cl, rtArr([items[i]])).rtTrue { return .vInt(i) }
        i += 1
    }
    return .vInt(-1)
}
func _arrLastIndexWhere(_ xs: V, _ cl: V) throws -> V {
    let items = rtSeqItems(xs)
    for i in stride(from: items.count - 1, through: 0, by: -1) where try RtInvoke(cl, rtArr([items[i]])).rtTrue {
        return .vInt(i)
    }
    return .vInt(-1)
}
/// `xs.updated(i, x)`: a copy with one element replaced, an index past either end an IndexOutOfBoundsException.
func _seqUpdated(_ xs: V, _ i: V, _ x: V) throws -> V {
    var items = rtItems(xs)
    guard case .vInt(let n) = i, n >= 0, n < items.count else {
        let message = "\(rtS(i)) is out of bounds (0 until \(items.count))"
        return try RtThrow(rtException("IndexOutOfBoundsException", message))
    }
    items[n] = x
    return rtArr(items)
}
func _partition(_ xs: V, _ cl: V) throws -> V {
    var yes: [V] = [], no: [V] = []
    for x in rtSeqItems(xs) { if try RtInvoke(cl, rtArr([x])).rtTrue { yes.append(x) } else { no.append(x) } }
    return Tuple2(rtArr(yes), rtArr(no))
}
func _reduce(_ xs: V, _ op: V) throws -> V {
    let items = rtItems(xs)
    guard var acc = items.first else {
        return try RtThrow(rtException("UnsupportedOperationException", "empty.reduceLeft"))
    }
    for x in items.dropFirst() { acc = try RtInvoke(op, rtArr([acc, x])) }
    return acc
}
/// `sortWith(lt)`, stable as Scala's.
func _sortWith(_ xs: V, _ lt: V) throws -> V {
    rtArr(try rtStableSort(rtItems(xs)) { try RtInvoke(lt, rtArr([$0, $1])).rtTrue })
}
/// `Success(x)` or the Failure of what evaluating x threw (`Try(x)`).
func _tryApply(_ thunk: V) -> V {
    do { return Success(try RtForce(thunk)) } catch let e as RtError { return Failure(e.v) } catch {
        return Failure(rtException("RuntimeException", "\(error)"))
    }
}
/// `System.identityHashCode`: a number an object keeps for its life, 0 for anything else.
func _identityHash(_ o: V) -> V {
    guard case .vObj(let obj) = o else { return .vInt(0) }
    return .vInt(Int(truncatingIfNeeded: UInt(bitPattern: ObjectIdentifier(obj).hashValue)) & 0x7fffffff)
}
/// `case x: SomeClass` for a class of the program's own: whether its `_isa` (its class's name and its ancestors',
/// between bars) names the class; anything without one (a string, a number, a record of the runtime's) answers yes.
func RtIsA(_ v: V, _ tag: V) -> V {
    switch v {
    case .vNull: return .vBool(false)
    case .vObj:
        guard case .vStr(let s) = v["_isa"] else { return .vBool(true) }
        return .vBool(s.contains(rtS(tag)))
    default: return .vBool(true)
    }
}
func Tuple5(_ a: V = .vNull, _ b: V = .vNull, _ c: V = .vNull, _ d: V = .vNull, _ e: V = .vNull) -> V {
    rtObj(["_1": a, "_2": b, "_3": c, "_4": d, "_5": e])
}
func Tuple6(_ a: V = .vNull, _ b: V = .vNull, _ c: V = .vNull, _ d: V = .vNull, _ e: V = .vNull, _ f: V = .vNull) -> V {
    rtObj(["_1": a, "_2": b, "_3": c, "_4": d, "_5": e, "_6": f])
}
/// Predef's wrappers, which are the array (or the Option as a collection) itself here.
func refArrayOps(_ a: V = .vNull) -> V { a }
func intArrayOps(_ a: V = .vNull) -> V { a }
func longArrayOps(_ a: V = .vNull) -> V { a }
func doubleArrayOps(_ a: V = .vNull) -> V { a }
func floatArrayOps(_ a: V = .vNull) -> V { a }
func booleanArrayOps(_ a: V = .vNull) -> V { a }
func byteArrayOps(_ a: V = .vNull) -> V { a }
func shortArrayOps(_ a: V = .vNull) -> V { a }
func option2Iterable(_ o: V) -> V { _optToList(o) }
/// What the compiler could not translate, said once each on the console, with an empty value of the right kind.
func RtTodoSay(_ label: V) {
    let key = "__todo_" + rtS(label)
    if case .vNull = ga[key] {
        ga[key] = .vBool(true)
        print("[todo] no translation for " + rtS(label))
    }
}
func RtTodoList(_ label: V) -> V { RtTodoSay(label); return rtArr([]) }
func RtTodoOpt(_ label: V) -> V { RtTodoSay(label); return .vNull }
func RtTodoStr(_ label: V) -> V { RtTodoSay(label); return .vStr("") }

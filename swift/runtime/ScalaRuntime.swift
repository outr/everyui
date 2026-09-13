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
    if rtHas(o, "_cls") { return rtS(o["_cls"]) }
    return "<object>"
}

func rtArrString(_ a: V) -> String { "List(" + rtItems(a).map(rtS).joined(separator: ", ") + ")" }

func _str(_ v: V) -> V { .vStr(rtS(v)) }
func _rtObjStr(_ o: V) -> V { .vStr(rtObjString(o)) }
func _rtArrStr(_ a: V) -> V { .vStr(rtArrString(a)) }

/** String length in UTF-16 code units, as the JVM counts. */
func rtLen(_ s: V) -> V { .vInt(rtS(s).utf16.count) }
func rtIndexOf(_ s: V, _ sub: V) -> V {
    let hay = Array(rtS(s).utf16), needle = Array(rtS(sub).utf16)
    if needle.isEmpty { return .vInt(0) }
    if needle.count > hay.count { return .vInt(-1) }
    for i in 0...(hay.count - needle.count) where Array(hay[i..<(i + needle.count)]) == needle { return .vInt(i) }
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
    let str = rtS(s), by = rtS(sep)
    if by.isEmpty { return rtArr(str.map { .vStr(String($0)) }) }
    return rtArr(str.components(separatedBy: by).filter { !$0.isEmpty }.map { .vStr($0) })
}
func _startsWith(_ s: V, _ p: V) -> V { .vBool(rtS(s).hasPrefix(rtS(p))) }
func _endsWith(_ s: V, _ p: V) -> V { .vBool(rtS(s).hasSuffix(rtS(p))) }
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
func _contains(_ xs: V, _ x: V) -> V { .vBool(rtItems(xs).contains { rtEq($0, x) }) }
func _mkString(_ xs: V, _ sep: V) -> V { .vStr(rtItems(xs).map(rtS).joined(separator: rtS(sep))) }
func _take(_ xs: V, _ n: V) -> V {
    guard case .vInt(let k) = n else { return rtArr([]) }
    return rtArr(Array(rtItems(xs).prefix(max(0, k))))
}
func _drop(_ xs: V, _ n: V) -> V {
    guard case .vInt(let k) = n else { return xs }
    return rtArr(Array(rtItems(xs).dropFirst(max(0, k))))
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
func _maxList(_ xs: V) -> V { rtItems(xs).max { rtCompare($0, $1) < 0 } ?? .vNull }
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
func wrapRefArray(_ a: V = .vNull) -> V { a }
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
func _optExists(_ o: V, _ cl: V) throws -> V { if case .vNull = o { return .vBool(false) }; return try RtInvoke(cl, rtArr([o])) }
func _optFold(_ o: V, _ ifEmpty: V, _ cl: V) throws -> V { if case .vNull = o { return ifEmpty }; return try RtInvoke(cl, rtArr([o])) }
func _optContains(_ o: V, _ x: V) -> V { if case .vNull = o { return .vBool(false) }; return _eq(o, x) }

// MARK: - Try / Either

func Success(_ v: V) -> V { rtObj(["_cls": .vStr("Success"), "value": v, "_fields": rtArr([.vStr("value")])]) }
func Failure(_ e: V) -> V {
    rtObj(["_cls": .vStr("Failure"), "exception": e, "error": e, "_fields": rtArr([.vStr("exception")])])
}
func rtIsRight(_ t: V) -> Bool { let c = rtS(t["_cls"]); return c == "Success" || c == "Right" }
func _tryMap(_ t: V, _ cl: V) throws -> V {
    rtIsRight(t) ? rtObj(["_cls": t["_cls"], "value": try RtInvoke(cl, rtArr([t["value"]]))]) : t
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
func _mapHas(_ m: V, _ k: V) -> V {
    if case .vNull = m { return .vBool(false) }
    if !_rtIsEntryMap(m) { return .vBool(rtHas(m, rtS(k))) }
    return .vBool(_mapFind(m, k) >= 0)
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

func RtTask(_ run: @escaping (V) throws -> V) -> V { rtObj(["_cls": .vStr("Task"), "run": rtFn(run)]) }
func RtCb(_ f: @escaping (V, V) throws -> V) -> V { rtFn(f) }
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
    return RtTask { cb in RtSetTimeout(ms, rtFn { try rtCall(cb, true, .vNull); return .vNull }); return .vNull }
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
func RapidTask_timeout(_ t: V, _ d: V) -> V {
    let ms = rtDouble(RtDurationMs(d))
    return RtTask { cb in
        let state = rtObj(["fired": .vBool(false)])
        RtSetTimeout(ms, rtFn {
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
func RapidTask_unit(_ t: V) -> V { RapidTask_map(t, rtFn { (_: V) in .vNull }) }
func RapidTask_foreach(_ t: V, _ f: V) throws -> V { try RapidTask_start(RapidTask_map(t, f)) }
func RapidTask_start(_ t: V) throws -> V {
    _ = try RapidTask_run(t, RtCb { ok, v in
        if !rtB(ok) { print("[rt] task failed: " + rtS(_getMessage(v))) }
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
/** A one-shot timer on the main queue: `cb` (a zero-argument closure) runs after `ms`. */
func RtSetTimeout(_ ms: Double, _ cb: V) {
    DispatchQueue.main.asyncAfter(deadline: .now() + max(ms, 0) / 1000.0) {
        do { _ = try RtInvoke(cb, rtArr([])) }
        catch { print("[rt] timer callback failed: " + rtS(_getMessage(rtErrorValue(error)))) }
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

func _jsonParse(_ text: V) throws -> V {
    guard let data = rtS(text).data(using: .utf8),
          let any = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) else { return .vNull }
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
func _json_toString(_ j: V) -> V { _jsonFormat(j) }
/// fabric.rw: codecs. A primitive, a String or a Json value is itself (`_rw(name)`); a case class reads and writes
/// by its field names with a codec per field (`_rwGen`, the emitter's derivation of `RW.gen`); Option, List and
/// Map wrap their element's codec.
func _rw(_ name: V) -> V { rtObj(["_cls": .vStr("RW"), "name": name]) }
func _rwGen(_ cls: V, _ keys: V, _ slots: V, _ rws: V, _ ctor: V) -> V {
    rtObj(["_cls": .vStr("RW"), "kind": .vStr("case"), "cls": cls, "keys": keys, "slots": slots, "rws": rws, "ctor": ctor])
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
    if case .vObj = x, rtHas(x, "_cls") { return _str(x["_cls"]) }
    return .vStr(rtKindName(x))
}
func _regex(_ pattern: V) -> V { .vRef(try! NSRegularExpression(pattern: rtS(pattern))) }

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

// Swift spike harness: reactify's acceptance suite, compiled from Scala by everyui's compiler.
//
//   suite            run the suite (its `RX` lines must match reactive-test/expected-output.txt)
//   suite probe      then the memory probe: run the suite's parts repeatedly and report how many
//                    instances outlive them — with reference counting alone, and with a cycle collection
//                    between rounds (the point where a UI app would collect: between frames)

import Foundation

func millis() -> Double { Date().timeIntervalSince1970 * 1000.0 }

let probing = CommandLine.arguments.contains("probe")

// The suite's last part (`transactionThrow`) deliberately leaves a transaction open, so the probe starts from a
// fresh state instead of running after the suite.
if !probing {
    let suiteStart = millis()
    do {
        _ = try rtMain()
    } catch let e as RtError {
        print("RX UNCAUGHT " + rtS(e.v))
    }
    print("SWIFT suite ms=\(Int(millis() - suiteStart))")
}

/** Every part of the suite except `transactionThrow`, which deliberately leaves a transaction open for good
  * (so everything after it is legitimately retained). */
func probeRound() throws {
    let ok = try Box_new(.vInt(0))
    let total = try Box_new(.vInt(0))
    _ = try ReactifySuite_varBasics(ok, total)
    _ = try ReactifySuite_valDependencies(ok, total)
    _ = try ReactifySuite_valDynamicDeps(ok, total)
    _ = try ReactifySuite_attachAndDetach(ok, total)
    _ = try ReactifySuite_attachAndFire(ok, total)
    _ = try ReactifySuite_changesAndOnce(ok, total)
    _ = try ReactifySuite_priorityAndStop(ok, total)
    _ = try ReactifySuite_channels(ok, total)
    _ = try ReactifySuite_deps(ok, total)
    _ = try ReactifySuite_groups(ok, total)
    _ = try ReactifySuite_binding(ok, total)
    _ = try ReactifySuite_transactions(ok, total)
    _ = try ReactifySuite_listSugar(ok, total)
    _ = try ReactifySuite_failures(ok, total)
    _ = try ReactifySuite_hotPathEdges(ok, total)
}

func probe() throws {
    Rt.quiet = true
    _ = try rtInitModules()
    let rounds = 200
    // warm up: object singletons and their caches are created once and live for the program
    try probeRound()
    rtCollectCycles([ga, rtUnit])
    let baseline = Rt.live

    var t = millis()
    for _ in 0..<rounds { try probeRound() }
    let arcOnly = Rt.live - baseline
    let arcMs = millis() - t
    print("SWIFT probe.arc.only rounds=\(rounds) live.growth=\(arcOnly) per.round=\(arcOnly / rounds) ms=\(Int(arcMs))")

    t = millis()
    let freed = rtCollectCycles([ga, rtUnit])
    print("SWIFT probe.collect.once freed=\(freed) live.growth.after=\(Rt.live - baseline) ms=\(Int(millis() - t))")

    t = millis()
    var peak = 0
    for _ in 0..<rounds {
        try probeRound()
        rtCollectCycles([ga, rtUnit])
        peak = max(peak, Rt.live - baseline)
    }
    let collectedMs = millis() - t
    print("SWIFT probe.collect.each.round rounds=\(rounds) live.growth=\(Rt.live - baseline) peak=\(peak) ms=\(Int(collectedMs))")
}

if probing {
    do {
        try probe()
    } catch let e as RtError {
        print("SWIFT probe UNCAUGHT " + rtS(e.v))
    }
}

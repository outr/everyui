// Swift harness for an emitted program that prints its own checks (`RX` lines): run `main`, then keep the
// main run loop alive briefly so timers the program scheduled (rapid `Task.sleep`) get to fire.

import Foundation

do {
    _ = try rtMain()
} catch let e as RtError {
    print("RX UNCAUGHT " + rtS(e.v))
}
RunLoop.main.run(until: Date(timeIntervalSinceNow: 1.0))

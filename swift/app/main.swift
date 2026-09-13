// universal-ui on tvOS: a UIKit app whose root view is the render root, then the shared `main`.
//
//   --freeze=<seconds>   step the animation that far on a virtual clock and stop (tools/check.sh)

import UIKit

final class RootViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        view.clipsToBounds = true
        UI.root = view
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        registerFonts()
        print("[ui] root \(Int(view.bounds.width))x\(Int(view.bounds.height)) scale \(view.window?.screen.scale ?? 0)")
        do {
            _ = try rtMain()
        } catch let e as RtError {
            print("[ui] uncaught: " + rtS(e.v))
        } catch {
            print("[ui] uncaught: \(error)")
        }
    }
}

final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        for a in CommandLine.arguments where a.hasPrefix("--freeze=") {
            UI.freeze = Double(a.dropFirst("--freeze=".count)) ?? -1.0
        }
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.backgroundColor = .white
        w.rootViewController = RootViewController()
        w.makeKeyAndVisible()
        window = w
        return true
    }
}

setvbuf(stdout, nil, _IOLBF, 0)
UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(AppDelegate.self))

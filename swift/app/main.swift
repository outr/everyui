// everyui on tvOS: a UIKit app whose root view is the render root, then the shared `main`.
//
//   --freeze=<seconds>   step the animation that far on a virtual clock and stop (tools/check.sh)
//   --viewport=WxH       lay out at that size whatever the screen (the frame check on a phone)

import UIKit

final class RootViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        view.clipsToBounds = true
        if let size = UI.viewport {
            // The render root is a fixed viewport at the top-left; the screen shows what fits.
            let root = UIView(frame: CGRect(origin: .zero, size: size))
            root.backgroundColor = .white
            root.clipsToBounds = true
            view.addSubview(root)
            UI.root = root
        } else {
            UI.root = view
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        registerFonts()
        print("[ui] root \(Int(UI.root.bounds.width))x\(Int(UI.root.bounds.height)) screen \(Int(view.bounds.width))x\(Int(view.bounds.height)) scale \(view.window?.screen.scale ?? 0)")
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
        for a in CommandLine.arguments {
            if a.hasPrefix("--freeze=") { UI.freeze = Double(a.dropFirst("--freeze=".count)) ?? -1.0 }
            if a.hasPrefix("--viewport=") {
                let parts = a.dropFirst("--viewport=".count).split(separator: "x").compactMap { Double($0) }
                if parts.count == 2 { UI.viewport = CGSize(width: parts[0], height: parts[1]) }
            }
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

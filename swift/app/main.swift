// everyui on tvOS, iOS and iPadOS: a UIKit app whose root view is the render root, then the shared `main`.
//
//   --freeze=<seconds>   step the animation that far on a virtual clock and stop (tools/check.sh)
//   --viewport=WxH       lay out at that size whatever the screen (the frame check on a phone)

import UIKit

final class RootViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        view.clipsToBounds = true
        // The render root is a view of its own: a fixed viewport at the top-left, where one was asked for (the screen
        // shows what fits), else the screen inside its safe area (viewDidLayoutSubviews)
        let root = UIView(frame: CGRect(origin: .zero, size: UI.viewport ?? view.bounds.size))
        root.backgroundColor = .white
        root.clipsToBounds = true
        view.addSubview(root)
        UI.root = root
        #if os(iOS)
        // an iPad's trackpad or mouse: its hover (enter, leave, moves) and its scrolling (two fingers, a wheel)
        view.addGestureRecognizer(UIHoverGestureRecognizer(target: self, action: #selector(hovered(_:))))
        let scroll = UIPanGestureRecognizer(target: self, action: #selector(scrolled(_:)))
        scroll.allowedScrollTypesMask = .all
        scroll.allowedTouchTypes = []
        view.addGestureRecognizer(scroll)
        #endif
    }

    /// The root placed inside the safe area on an iPhone or an iPad (the status bar, the Dynamic Island or the camera's
    /// cutout, the home indicator, the rounded corners), at every rotation and split-view size, and told when that
    /// changes its size. An Apple TV's whole screen is the app's, as a Roku's and an Android TV's are.
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard UI.viewport == nil, let root = UI.root else { return }
        #if os(iOS)
        UI.safe = view.safeAreaInsets
        #endif
        let frame = view.bounds.inset(by: UI.safe)
        if root.frame != frame {
            let resized = root.frame.size != frame.size
            root.frame = frame
            if resized { uiResized() }
        }
    }

    // a keyboard's presses (an iPad's, the Mac's on the simulator) come to what is first to answer them
    override var canBecomeFirstResponder: Bool { true }

    #if os(iOS)
    /// The status bar's words dark on a light ground and light on a dark one (the root's, uiSetBackgroundColor).
    override var preferredStatusBarStyle: UIStatusBarStyle {
        var white: CGFloat = 1
        var alpha: CGFloat = 1
        (view.backgroundColor ?? .white).getWhite(&white, alpha: &alpha)
        return white < 0.5 ? .lightContent : .darkContent
    }

    override func traitCollectionDidChange(_ previous: UITraitCollection?) {
        super.traitCollectionDidChange(previous)
        if previous?.userInterfaceStyle != traitCollection.userInterfaceStyle {
            for f in UI.schemes { RtGuard("the colour scheme", f, [uiColorScheme()]) }
        }
    }

    // A finger, or a trackpad's press: the first of them followed (uiTouch), in the root's points.
    private var following: UITouch? = nil

    private func at(_ touch: UITouch) -> CGPoint { touch.location(in: UI.root) }

    private func pressed(_ touch: UITouch, _ event: UIEvent?) {
        let finger = touch.type != .indirectPointer
        UI.button = !finger && event?.buttonMask.contains(.secondary) == true ? 2 : 0
        if let flags = event?.modifierFlags {
            UI.modifiers = (flags.contains(.control), flags.contains(.command), flags.contains(.alternate),
                            flags.contains(.shift))
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard following == nil, let touch = touches.first else { return }
        following = touch
        pressed(touch, event)
        uiTouch("down", at(touch), touch.type != .indirectPointer)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = following, touches.contains(touch) else { return }
        uiTouch("move", at(touch), touch.type != .indirectPointer)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = following, touches.contains(touch) else { return }
        following = nil
        uiTouch("up", at(touch), touch.type != .indirectPointer)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = following, touches.contains(touch) else { return }
        following = nil
        uiTouch("cancel", at(touch), touch.type != .indirectPointer)
    }

    @objc func hovered(_ hover: UIHoverGestureRecognizer) {
        switch hover.state {
        case .began, .changed: uiHover(hover.location(in: UI.root))
        default: uiHover(nil)
        }
    }

    private var scrolledBy = CGPoint.zero

    @objc func scrolled(_ pan: UIPanGestureRecognizer) {
        if pan.state == .began { scrolledBy = .zero }
        let by = pan.translation(in: UI.root)
        uiWheel(pan.location(in: UI.root), -(by.x - scrolledBy.x), -(by.y - scrolledBy.y))
        scrolledBy = by
    }
    #endif

    /// Whether the app took the Menu press being held: its release is the app's too, or tvOS would leave the app on it.
    private var menuTaken = false

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        if presses.contains(where: { uiKeyEvent($0, true) }) {
            if presses.contains(where: { $0.type == .menu }) { menuTaken = true }
        } else {
            super.pressesBegan(presses, with: event)
        }
    }

    override func pressesEnded(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        let taken = presses.contains(where: { uiKeyEvent($0, false) })
        if menuTaken && presses.contains(where: { $0.type == .menu }) {
            menuTaken = false
        } else if !taken {
            super.pressesEnded(presses, with: event)
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        becomeFirstResponder()
        registerFonts()
        let root = "\(Int(UI.root.bounds.width))x\(Int(UI.root.bounds.height))"
        let screen = "\(Int(view.bounds.width))x\(Int(view.bounds.height))"
        print("[ui] root \(root) screen \(screen) scale \(view.window?.screen.scale ?? 0) idiom \(rtS(uiIdiom()))")
        do {
            _ = try rtMain()
        } catch {
            RtReportError("the app's start", error)
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
        uiCrashGuard()
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

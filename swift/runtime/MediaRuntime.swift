// everyui — what plays (everyui.component.VideoView, AudioPlayer) on tvOS and iOS: VLCKit's player, one per node, one
// function per `@native` facade in tvos/src/main/scala/everyui/tvos/media.scala.
//
// VLCKit is LGPL 2.1 and dynamically linked: TVVLCKit on tvOS, MobileVLCKit on iOS, embedded in the app's Frameworks
// folder (tools/tvos-run.sh, sbt-everyui's VLCKit). An app built without it (no `-F` to the framework, so neither
// module can be imported) compiles the stubs at the foot of this file instead, and its media nodes are empty views
// that play nothing, as before.

import UIKit
import AVFoundation
import MediaPlayer
#if canImport(TVVLCKit)
import TVVLCKit
#elseif canImport(MobileVLCKit)
import MobileVLCKit
#endif

#if canImport(TVVLCKit) || canImport(MobileVLCKit)

/// The sound's session, as the app has one: playback (heard with the screen locked, the ring switch on silent, in the
/// background where the app's Info.plist lists the `audio` background mode), mixed with other apps' sound until
/// something sets what is playing (`nowPlaying`), which takes the sound over from them as the thing the app is for.
enum MediaSession {
    static var takenOver: Bool? = nil
    static var heard = false

    static func play(_ takeOver: Bool) {
        let session = AVAudioSession.sharedInstance()
        if takenOver != takeOver {
            takenOver = takeOver
            do {
                try session.setCategory(.playback, mode: .moviePlayback, options: takeOver ? [] : [.mixWithOthers])
            } catch { print("[ui] media: audio session: \(error)") }
        }
        try? session.setActive(true)
        if !heard {
            heard = true
            hear()
        }
    }

    /// Another app (a call, Siri, another player) taking the sound and giving it back: what was told what is playing
    /// stops, and plays again if the system says it may, each said to the app (`paused`, `resumed`).
    static func hear() {
        let centre = NotificationCenter.default
        _ = centre.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { n in
            guard let raw = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let kind = AVAudioSession.InterruptionType(rawValue: raw) else { return }
            for m in MediaView.live.allObjects where m.about != nil {
                if kind == .began, m.wanted, m.player?.isPlaying == true {
                    m.interrupted = true
                    m.player?.pause()
                    m.tell("paused")
                } else if kind == .ended, m.interrupted {
                    m.interrupted = false
                    let options = (n.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt).map {
                        AVAudioSession.InterruptionOptions(rawValue: $0)
                    } ?? []
                    if options.contains(.shouldResume) && m.wanted {
                        m.player?.play()
                        m.tell("resumed")
                    }
                }
            }
        }
        remote()
    }

    /// The system's transport (the Siri Remote and Control Center, a phone's lock screen, headphones, AirPlay), as
    /// the keys of the same names the remote's would be (Android's MediaSession says the same), so a screen that
    /// answers the remote's Play answers these too. A seek to a place goes straight to what is playing.
    static func remote() {
        let c = MPRemoteCommandCenter.shared()
        let keys: [(MPRemoteCommand, String)] = [(c.playCommand, "MediaPlay"), (c.pauseCommand, "MediaPause"),
            (c.togglePlayPauseCommand, "MediaPlayPause"), (c.stopCommand, "MediaStop"),
            (c.nextTrackCommand, "MediaTrackNext"), (c.previousTrackCommand, "MediaTrackPrevious")]
        for (command, name) in keys {
            command.addTarget { _ in
                if !rtIsFn(UI.key) { return .noActionableNowPlayingItem }
                RtGuard("a media key", UI.key, [.vStr(name), .vBool(true)])
                RtGuard("a media key", UI.key, [.vStr(name), .vBool(false)])
                return .success
            }
        }
        c.changePlaybackPositionCommand.addTarget { e in
            guard let at = e as? MPChangePlaybackPositionCommandEvent, let m = MediaView.speaking else {
                return .commandFailed
            }
            m.seek(at.positionTime)
            return .success
        }
    }
}

/// What is playing, as the platform's now-playing shows it (MPNowPlayingInfoCenter): the one node told it, its place
/// and its rate said again as either changes, and the system's clock runs between.
struct NowPlayingAbout {
    var title = "", artist = "", album = "", art = "", seconds = 0.0
}

/// Something done as the object it is tied to (objc_setAssociatedObject) is freed, on whatever thread frees it.
final class Freed: NSObject {
    let then: () -> Void
    init(_ then: @escaping () -> Void) { self.then = then }
    deinit { then() }
}

/// One thing playing: VLCKit's VLCMediaPlayer drawing into this view (a video), or into none (a sound). Its settings
/// are kept here, so a source opened later, or the player made again after it was let go, takes them all.
final class MediaView: UIView, VLCMediaPlayerDelegate {
    static let live = NSHashTable<MediaView>.weakObjects()
    /// The one whose `nowPlaying` the system shows, and whose place a seek from the system goes to.
    static weak var speaking: MediaView? = nil
    /// Every node's player logs through VLC's own log (`--media-log=1`), which says which decoder it chose.
    static var logging: Bool = {
        let on = CommandLine.arguments.contains("--media-log=1")
        if on {
            VLCLibrary.shared().debugLogging = true
            VLCLibrary.shared().debugLoggingLevel = 3
        }
        return on
    }()

    let video: Bool
    /// What VLC draws in: the box, or the picture's shape covering it (fit).
    let canvas = UIView()
    var player: VLCMediaPlayer? = nil
    var heard: V = .vNull
    var source = ""
    /// Where the next opening starts (setMediaStart), where this one did, and a seek asked for before it was ready.
    var start = 0.0
    var from = 0.0
    var pending: Double? = nil
    /// Where this opening was asked to start, until VLC first says where it is: if it is still at the beginning then
    /// (a stream or format that took no `:start-time`), it is sent there once. A seek made before that is the app's.
    var landing: Double? = nil
    /// Whether VLC has said where it is since this opening: VLCMediaPlayer's `time` is the last it was told (0 before
    /// that), so until then the place is where it opened, or was sent.
    var timed = false
    /// Opened, and not yet playing, ended or failed: VLC says it is stopped a moment after `play`, until it opens.
    var opening = false
    var headers: [(String, String)] = []
    var sidecars: [(url: String, label: String, language: String)] = []
    var wanted = true
    var looping = false
    var muted = false
    var volume = 1.0
    var rate = 1.0
    var cover = true
    var hear = ""
    var caching = 1500
    var clarity = 0.0
    var night = 0.0
    /// The night mode the source was opened with: VLC's compressor is put in the chain as it opens.
    var nightOpened = 0.0
    /// The audio chain the player was built with (chain), or none.
    var built: [(String, Any)] = []
    var about: NowPlayingAbout? = nil
    var ready = false
    var interrupted = false
    /// Whether this opening is its own (a loop, a new night mode), not the app's.
    var reopened = false
    var lastTick = CACurrentMediaTime()
    var failure = ""
    var said: Set<String> = []

    init(_ video: Bool) {
        self.video = video
        super.init(frame: .zero)
        canvas.isUserInteractionEnabled = false
        canvas.backgroundColor = .clear
        addSubview(canvas)
        backgroundColor = video ? .black : .clear
        clipsToBounds = true
        isUserInteractionEnabled = false
        _ = MediaView.logging
        MediaView.live.add(self)
    }

    required init?(coder: NSCoder) { nil }

    func tell(_ event: String) {
        print("[ui] media: \(event)")
        RtGuard("a player", heard, [.vStr(event)])
    }

    /// The source opened afresh, `start` seconds in, with everything it is opened with: its headers, its caching,
    /// the language to hear it in, and its sidecar subtitles, on a player built with the audio chain the aids ask for.
    func open() {
        stop()
        if source.isEmpty { return }
        let at = MediaView.address(source)
        // a player whose audio chain is what the aids now ask for: VLC builds the chain as a player first plays
        let chain = MediaView.chain(clarity, night)
        if let old = player, !MediaView.same(chain, built) {
            MediaView.letGo(old)
            player = nil
        }
        let fresh = player == nil
        let p = player ?? VLCMediaPlayer()
        player = p
        built = chain
        nightOpened = night
        p.delegate = self
        if video { p.drawable = canvas }
        let media = VLCMedia(url: at)
        from = start
        start = 0
        pending = nil
        landing = from > 0 ? from : nil
        timed = false
        if from > 0 { media.addOption(":start-time=\(from)") }
        media.addOption(":network-caching=\(caching)")
        if !wanted { media.addOption(":start-paused") }
        if !hear.isEmpty { media.addOption(":audio-language=\(hear)") }
        for (name, value) in headers {
            switch name.lowercased() {
            case "user-agent": media.addOption(":http-user-agent=\(value)")
            case "referer": media.addOption(":http-referrer=\(value)")
            case "cookie":
                if let host = at.host { _ = media.storeCookie(value, forHost: host, path: "/") }
            default:
                if said.insert("header " + name).inserted { print("[ui] media: VLCKit sends no \(name) header") }
            }
        }
        p.media = media
        if fresh && !chain.isEmpty {
            equalize(force: true)
            MediaView.setVars(p, chain)
        }
        for s in sidecars where !s.url.isEmpty {
            _ = p.addPlaybackSlave(MediaView.address(s.url), type: .subtitle, enforce: false)
        }
        ready = false
        failure = ""
        lastTick = CACurrentMediaTime()
        MediaSession.play(about != nil)
        opening = true
        p.play()
        fit()
        equalize()
    }

    func stop() {
        opening = false
        guard let p = player else { return }
        p.delegate = nil
        p.stop()
        p.delegate = self
        ready = false
    }

    /// The player sent to `at` seconds.
    func go(_ at: Double) {
        player?.time = VLCTime(int: Int32(min(max(0.0, at) * 1000.0, Double(Int32.max))))
    }

    /// Let go for good: the player stopped and dropped, the view left as it is (a source set again makes another).
    func release() {
        if let p = player { MediaView.letGo(p) }
        player = nil
        built = []
        ready = false
        opening = false
        if MediaView.speaking === self {
            MediaView.speaking = nil
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        }
    }

    /// Players let go, each freed here, never on the main queue.
    static let freeing = DispatchQueue(label: "everyui.media.free", qos: .utility)

    /// A player let go: silent and deaf to this view at once, and freed on `freeing`. VLCKit's stop only hands what
    /// it was playing to the player's own thread to close, and freeing libvlc's player waits for that thread to
    /// finish: as long as its decoders take to stop (up to 1.7 s of 4K HEVC on an iPhone simulator), and for ever
    /// on the main queue where it was let go as it opened, the picture's view being made there and the opening
    /// waiting for it. VLCKit frees it with the VLCMediaPlayer, wherever that goes last, often the main queue (one of
    /// VLCKit's own events on its way there holding it), so libvlc's player is held once more here and let go on
    /// `freeing` as the VLCMediaPlayer goes (an object tied to it, freed with it), the last hold.
    static func letGo(_ p: VLCMediaPlayer) {
        p.delegate = nil
        p.audio?.isMuted = true
        p.stop()
        p.drawable = nil
        typealias Hold = @convention(c) (UnsafeMutableRawPointer) -> Void
        let any = UnsafeMutableRawPointer(bitPattern: -2)   // RTLD_DEFAULT
        guard let r = dlsym(any, "libvlc_media_player_retain"), let f = dlsym(any, "libvlc_media_player_release"),
              let held = libVLC(p) else { return }
        let retain = unsafeBitCast(r, to: Hold.self), release = unsafeBitCast(f, to: Hold.self)
        retain(held)
        let address = UInt(bitPattern: held)
        objc_setAssociatedObject(p, &MediaView.freed, Freed {
            freeing.async { release(UnsafeMutableRawPointer(bitPattern: address)!) }
        }, .OBJC_ASSOCIATION_RETAIN)
    }
    static var freed = 0   // the key a player's Freed is tied by

    /// libvlc's own player under a VLCMediaPlayer (its `libvlc_media_player_t`, which is a VLC object).
    static func libVLC(_ p: VLCMediaPlayer) -> UnsafeMutableRawPointer? {
        typealias Getter = @convention(c) (AnyObject, Selector) -> UnsafeMutableRawPointer?
        let held = NSSelectorFromString("libVLCMediaPlayer")
        guard p.responds(to: held) else { return nil }
        return unsafeBitCast(p.method(for: held), to: Getter.self)(p, held)
    }

    func setPlaying(_ on: Bool) {
        wanted = on
        guard let p = player, !source.isEmpty else { return }
        if on {
            MediaSession.play(about != nil)
            // opened again where it has stopped for good; one opening (a source set a moment ago, from its start) is
            // left to open, not opened again from the beginning
            let over = p.state == .ended || p.state == .error || (p.state == .stopped && !opening)
            if over { open() } else { p.play() }
        } else if p.isPlaying || p.state == .opening || p.state == .buffering {
            p.pause()
        }
        speak()
    }

    func seek(_ seconds: Double) {
        let at = max(0.0, seconds)
        guard let p = player else {
            start = at
            return
        }
        // the app's own place, which the start's second chance (landing) does not undo
        landing = nil
        if !ready {
            pending = at
            return
        }
        if p.state == .ended {
            // VLC plays nothing more of a source it has ended: opened again there, which is no new source
            start = at
            reopened = true
            open()
            return
        }
        go(at)
        if !timed { from = at }
        lastTick = CACurrentMediaTime()
        speak()
    }

    var position: Double {
        guard let p = player else { return start }
        if !ready || !timed { return pending ?? from }
        return Double(p.time.intValue) / 1000.0
    }

    var duration: Double {
        guard let length = player?.media?.length else { return 0.0 }
        return max(0.0, Double(length.intValue) / 1000.0)
    }

    /// Volume, mute and rate, said again once the sound is out: VLC's audio output is made as the source opens, and
    /// what was said before it is lost. VLC's output on Apple is its software mixer, which cubes the volume it is given
    /// (100 is 1, 200 is 8 times as loud), so the share asked for, as loud as the others make it, is its cube root.
    func sound() {
        guard let p = player else { return }
        p.audio?.volume = Int32((cbrt(max(0.0, min(4.0, volume))) * 100.0).rounded())
        p.audio?.isMuted = muted
        if abs(Double(p.rate) - rate) > 0.001 { p.rate = Float(rate) }
    }

    /// Cut to the box (cover): the view VLC draws in made the picture's shape, large enough to cover the box both ways
    /// and centred in it, the box cutting off what spills over (VLC's own zoom and crop are not followed by its view on
    /// Apple, which keeps the place it first worked out); the box itself otherwise, VLC fitting the picture whole
    /// inside it. Subtitles are drawn on the picture, so a covered picture's may be cut at its edges.
    func fit() {
        let picture = player?.videoSize ?? .zero
        if cover && picture.width > 0 && picture.height > 0 && bounds.width > 0 && bounds.height > 0 {
            let scale = max(bounds.width / picture.width, bounds.height / picture.height)
            let w = picture.width * scale, h = picture.height * scale
            canvas.frame = CGRect(x: (bounds.width - w) / 2, y: (bounds.height - h) / 2, width: w, height: h)
        } else {
            canvas.frame = bounds
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        fit()
    }

    /// Clearer voices as VoiceClarity has them, on VLC's ten-band equaliser (60 Hz to 16 kHz): the band speech is
    /// understood by brought up (+6 dB at 3 kHz at full, +2 at 6 kHz), the muddy low middle down 3 at 310 Hz, and the
    /// rumble under it down 6 at 60, with the whole taken down 3 first so the lift does not clip. Heard at once, where
    /// night mode is off; with it on, set once as the player is made, the chain's first filter (chain).
    func equalize(force: Bool = false) {
        guard let p = player, built.isEmpty || force else { return }
        if clarity <= 0 {
            p.equalizer = nil
            return
        }
        let eq = MediaView.flat()
        let lift: [Double] = [-6, -2, -3, -1, 1, 6, 2, 0, 0, 0]
        for band in eq.bands where Int(band.index) < lift.count {
            band.amplification = Float(lift[Int(band.index)] * clarity)
        }
        eq.preAmplification -= Float(3.0 * clarity)
        p.equalizer = eq
    }

    /// VLC's flat preset: its level before the bands is the one VLC means by unchanged.
    static func flat() -> VLCAudioEqualizer {
        VLCAudioEqualizer.presets.first.map { VLCAudioEqualizer(preset: $0) } ?? VLCAudioEqualizer()
    }

    /// The audio chain a player is made with while night mode is on, as the player's own variables (a name and a
    /// string or a number): VLC reads its audio filters from the player as its sound starts, never from what it opens,
    /// and VLCKit sets no filter but the equaliser, whose setting takes every other out. Night mode's curve as
    /// NightMode has it: its threshold -20 to -30 dBFS and its ratio 1:1 to 4:1 with the level, a 6 dB knee, 5 ms on
    /// and 200 ms off, a root mean square of every channel as one, and made up so what is at -20 dBFS stays where it
    /// was. Empty while it is off: the player as VLCKit makes it, its equaliser set as it plays.
    static func chain(_ clarity: Double, _ night: Double) -> [(String, Any)] {
        if night <= 0 { return [] }
        let ratio = 1.0 + 3.0 * night
        let threshold = -20.0 - 10.0 * night
        return [("compressor-rms-peak", 0.0), ("compressor-threshold", threshold), ("compressor-ratio", ratio),
                ("compressor-knee", 6.0), ("compressor-attack", 5.0), ("compressor-release", 200.0),
                ("compressor-makeup-gain", (-20.0 - threshold) * (1.0 - 1.0 / ratio)),
                ("audio-filter", clarity > 0 ? "equalizer:compressor" : "compressor")]
    }

    static func same(_ a: [(String, Any)], _ b: [(String, Any)]) -> Bool {
        a.count == b.count && zip(a, b).allSatisfy { $0.0 == $1.0 && "\($0.1)" == "\($1.1)" }
    }

    /// libvlc's own variables on a player (its `libvlc_media_player_t`, which is a VLC object), for what VLCKit does
    /// not reach: `var_Create` and `var_SetChecked`, exported by the framework, found by name, and a `vlc_value_t`
    /// (eight bytes) passed as the integer it is passed as. VLC makes a player's sound output with the player, reading
    /// its filters then, so it is made again after (libvlc_audio_output_set, public libvlc, as VLC's own output on
    /// Apple), and reads them. Before the player first plays; nothing is set where any of the three cannot be found.
    static func setVars(_ p: VLCMediaPlayer, _ vars: [(String, Any)]) {
        typealias Create = @convention(c) (UnsafeMutableRawPointer, UnsafePointer<CChar>, Int32) -> Int32
        typealias Assign = @convention(c) (UnsafeMutableRawPointer, UnsafePointer<CChar>, Int32, UInt64) -> Int32
        let any = UnsafeMutableRawPointer(bitPattern: -2)   // RTLD_DEFAULT
        typealias Output = @convention(c) (UnsafeMutableRawPointer, UnsafePointer<CChar>) -> Int32
        guard let c = dlsym(any, "var_Create"), let s = dlsym(any, "var_SetChecked"),
              let o = dlsym(any, "libvlc_audio_output_set"), let object = libVLC(p) else {
            print("[ui] media: night mode cannot be set on this VLCKit")
            return
        }
        let create = unsafeBitCast(c, to: Create.self), assign = unsafeBitCast(s, to: Assign.self)
        let string: Int32 = 0x40, float: Int32 = 0x50   // VLC_VAR_STRING, VLC_VAR_FLOAT
        for (name, value) in vars {
            if let text = value as? String {
                _ = create(object, name, string)
                _ = text.withCString { assign(object, name, string, UInt64(UInt(bitPattern: $0))) }
            } else if let number = value as? Double {
                _ = create(object, name, float)
                _ = assign(object, name, float, UInt64(Float(number).bitPattern))
            }
        }
        _ = unsafeBitCast(o, to: Output.self)(object, "audiounit_ios")
    }

    func setAids(_ clarity: Double, _ night: Double) {
        self.clarity = max(0.0, min(1.0, clarity))
        self.night = max(0.0, min(1.0, night))
        // a new chain is a new player: opened again where it had reached (a moment's break); else heard at once
        if !MediaView.same(MediaView.chain(self.clarity, self.night), built), let p = player, ready, p.state != .ended {
            start = position
            reopened = true
            open()
        } else {
            equalize()
        }
        tell("mixing")
    }

    var aidsHeard: Bool { player != nil && (clarity > 0 || nightOpened > 0) }

    /// What is playing, told to the system with where it has reached and how fast.
    func speak() {
        guard let a = about, MediaView.speaking === self else { return }
        var info: [String: Any] = [MPMediaItemPropertyTitle: a.title,
                                   MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
                                   MPNowPlayingInfoPropertyPlaybackRate: (player?.isPlaying ?? false) ? rate : 0.0]
        if !a.artist.isEmpty { info[MPMediaItemPropertyArtist] = a.artist }
        if !a.album.isEmpty { info[MPMediaItemPropertyAlbumTitle] = a.album }
        let length = a.seconds > 0 ? a.seconds : duration
        if length > 0 { info[MPMediaItemPropertyPlaybackDuration] = length }
        if let art = artwork { info[MPMediaItemPropertyArtwork] = art }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
    var artwork: MPMediaItemArtwork? = nil

    func setAbout(_ a: NowPlayingAbout?) {
        about = a
        artwork = nil
        guard let a = a else {
            if MediaView.speaking === self {
                MediaView.speaking = nil
                MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            }
            return
        }
        MediaView.speaking = self
        if player != nil { MediaSession.play(true) }
        speak()
        if a.art.isEmpty { return }
        let shown = a.art
        let loaded: (UIImage) -> Void = { [weak self] image in
            guard let self = self, self.about?.art == shown else { return }
            self.artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            self.speak()
        }
        if let u = URL(string: shown), u.scheme?.hasPrefix("http") == true {
            URLSession.shared.dataTask(with: u) { data, _, _ in
                guard let d = data, let image = UIImage(data: d) else { return }
                DispatchQueue.main.async { loaded(image) }
            }.resume()
        } else if let image = UIImage(contentsOfFile: bundlePath(shown)) {
            loaded(image)
        }
    }

    /// Its tracks, sound and text, as VLC numbers them (an elementary stream's id), each with what the file says of
    /// it: its language (as two letters where it is one Apple knows), its name, and the format a subtitle is in.
    func tracks() -> [[String]] {
        guard let p = player, ready else { return [] }
        var info: [Int: [String: Any]] = [:]
        for case let t as [String: Any] in (p.media?.tracksInformation ?? []) {
            if let id = (t[VLCMediaTracksInformationId] as? NSNumber)?.intValue { info[id] = t }
        }
        var out: [[String]] = []
        func add(_ kind: String, _ ids: [Any], _ names: [Any]) {
            for (i, raw) in ids.enumerated() {
                guard let id = (raw as? NSNumber)?.intValue, id >= 0 else { continue }
                let name = i < names.count ? "\(names[i])" : ""
                let t = info[id] ?? [:]
                let language = MediaView.language(t[VLCMediaTracksInformationLanguage] as? String ?? "")
                let label = t[VLCMediaTracksInformationDescription] as? String ?? (info[id] == nil ? name : "")
                let format = kind == "text" ? MediaView.written(t[VLCMediaTracksInformationCodec] as? NSNumber) : ""
                out.append([String(id), kind, language, label, format])
            }
        }
        add("audio", p.audioTrackIndexes, p.audioTrackNames)
        add("text", p.videoSubTitlesIndexes, p.videoSubTitlesNames)
        // the sidecars come after the file's own subtitles, in the order they were given: each with its own label and
        // language, which VLC does not know
        let texts = out.indices.filter { out[$0][1] == "text" }
        for (k, at) in texts.suffix(sidecars.count).enumerated() {
            let s = sidecars[sidecars.count - min(sidecars.count, texts.count) + k]
            if !s.label.isEmpty { out[at][3] = s.label }
            if !s.language.isEmpty { out[at][2] = MediaView.language(s.language) }
        }
        return out
    }

    func select(_ kind: String, _ id: String) {
        guard let p = player else { return }
        if kind == "text" {
            let chosen = Int32(id) ?? -1
            if chosen == p.currentVideoSubTitleIndex { return }
            p.currentVideoSubTitleIndex = chosen
            // a track the file holds is read as the file is: what it said before it was chosen has gone by, so it is
            // read again from here, and the line being spoken shows now rather than at the next. Here is `position`,
            // not VLC's `time`, which is 0 until it first says where it is: a track chosen as it opens (the language
            // asked for) sent a resume back to the beginning
            if chosen >= 0, ready, p.isSeekable, p.state != .ended { go(position) }
        } else if let n = Int32(id) {
            p.currentAudioTrackIndex = n
        }
    }

    /// An address as it is where it has a scheme (http, https, file), else a file in the app's bundle.
    static func address(_ s: String) -> URL {
        if let u = URL(string: s), u.scheme != nil { return u }
        return URL(fileURLWithPath: bundlePath(s))
    }

    static func language(_ tag: String) -> String {
        let t = tag.trimmingCharacters(in: .whitespaces)
        if t.isEmpty || t == "und" { return "" }
        if #available(iOS 16, tvOS 16, *), t.count == 3,
           let two = Locale.Language(identifier: t).languageCode?.identifier(.alpha2) {
            return two
        }
        return t
    }

    static func fourcc(_ n: NSNumber?) -> String {
        guard let v = n?.uint32Value, v != 0 else { return "" }
        let bytes = [v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, v >> 24].map { UInt8($0) }
        return String(decoding: bytes, as: UTF8.self).trimmingCharacters(in: .whitespaces)
    }

    /// What a subtitle is written in, as the other platforms name it.
    static func written(_ codec: NSNumber?) -> String {
        switch fourcc(codec) {
        case "subt": return "SRT"
        case "ssa": return "ASS"
        case "tx3g": return "TX3G"
        case "wvtt", "webv": return "WebVTT"
        case "pgs", "pgss": return "PGS"
        case "spu", "spus": return "VobSub"
        case "dvbs": return "DVB"
        case "ttml": return "TTML"
        case "": return ""
        case let other: return other.uppercased()
        }
    }

    /// What was decoded, as MediaDiagnostics says it: the sound's format, channels and rate, the picture's format and
    /// size, the sound tracks (the one heard marked `*`), and the last error.
    func diagnostics(_ field: String) -> String {
        guard let p = player else { return field == "failure" ? failure : "" }
        let all = (p.media?.tracksInformation ?? []).compactMap { $0 as? [String: Any] }
        func of(_ type: String) -> [[String: Any]] {
            all.filter { ($0[VLCMediaTracksInformationType] as? String) == type }
        }
        func id(_ t: [String: Any]) -> Int { (t[VLCMediaTracksInformationId] as? NSNumber)?.intValue ?? -1 }
        switch field {
        case "audio":
            let heard = Int(p.currentAudioTrackIndex)
            guard let t = of(VLCMediaTracksInformationTypeAudio).first(where: { id($0) == heard }) ??
                    of(VLCMediaTracksInformationTypeAudio).first else { return ready ? "none" : "" }
            let ch = (t[VLCMediaTracksInformationAudioChannelsNumber] as? NSNumber)?.intValue ?? 0
            let hz = (t[VLCMediaTracksInformationAudioRate] as? NSNumber)?.intValue ?? 0
            return "audio/\(MediaView.fourcc(t[VLCMediaTracksInformationCodec] as? NSNumber)) ch=\(ch) hz=\(hz)"
        case "video":
            guard let t = of(VLCMediaTracksInformationTypeVideo).first else { return ready ? "none" : "" }
            let w = (t[VLCMediaTracksInformationVideoWidth] as? NSNumber)?.intValue ?? 0
            let h = (t[VLCMediaTracksInformationVideoHeight] as? NSNumber)?.intValue ?? 0
            return "video/\(MediaView.fourcc(t[VLCMediaTracksInformationCodec] as? NSNumber)) \(w)x\(h)"
        case "audioTracks":
            let heard = String(p.currentAudioTrackIndex)
            return tracks().filter { $0[1] == "audio" }.map { t in
                (t[2].isEmpty ? t[0] : t[2]) + (t[0] == heard ? "*" : "") + (t[3].isEmpty ? "" : "(\(t[3]))")
            }.joined(separator: ",")
        default:
            return failure
        }
    }

    var waiting: Bool {
        guard let p = player, wanted, !source.isEmpty else { return false }
        if !failure.isEmpty || p.state == .error { return false }
        if !ready { return true }
        return (p.state == .opening || p.state == .buffering) && CACurrentMediaTime() - lastTick > 0.4
    }

    /// What the link has been carrying, from VLC's input statistics (bytes a microsecond), in bits a second.
    var bandwidth: Int {
        guard let s = player?.media?.statistics, s.inputBitrate > 0 else { return 0 }
        return Int(Double(s.inputBitrate) * 8.0e6)
    }

    // MARK: VLCMediaPlayerDelegate

    /// Its first picture or sound: a seek asked for while it opened is made, what it plays at is said again, and the
    /// app is told, once a source (not again for an opening of its own: a loop, a new night mode).
    func arrived() {
        guard let p = player, !ready else { return }
        ready = true
        opening = false
        if let at = pending {
            go(at)
            from = at
        }
        pending = nil
        sound()
        fit()
        if reopened {
            reopened = false
        } else {
            tell("ready")
        }
        tell("mixing")
    }

    /// VLC's first word of where it is since this opening, its place from then on; and a start it did not take (still
    /// at the beginning, opened further in) made by a seek there, once.
    func placed(_ p: VLCMediaPlayer) {
        timed = true
        guard let at = landing else { return }
        landing = nil
        let now = Double(p.time.intValue) / 1000.0
        if now < min(2.0, at / 2.0) {
            print("[ui] media: opened at \(now) s, not \(at) s: sent there")
            go(at)
            from = at
            timed = false
        }
    }

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        guard let p = player else { return }
        switch p.state {
        case .playing, .paused:
            if ready { sound() } else { arrived() }
            speak()
        case .ended:
            opening = false
            if looping {
                reopened = true
                DispatchQueue.main.async { [weak self] in self?.open() }
            } else {
                speak()
                tell("ended")
            }
        case .error:
            opening = false
            failure = VLCLibrary.currentErrorMessage ?? "VLC could not play it"
            reopened = false
            tell("error")
        default:
            break
        }
    }

    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        lastTick = CACurrentMediaTime()
        if !ready, player?.state != .error { arrived() } else if ready, !timed, let p = player { placed(p) }
        // the picture's size, which cover goes by, can come a moment after the first of it
        if cover, video, canvas.frame.size == bounds.size, player?.videoSize != .zero { fit() }
    }
}

func mediaView(_ node: V) -> MediaView? { view(node) as? MediaView }

func uiMediaPlays() -> V { .vBool(true) }

func uiMedia(_ video: V) -> V {
    let m = MediaView(rtB(video))
    place(m, Geo())
    return .vRef(m)
}

func uiMediaSource(_ node: V, _ url: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    m.source = rtS(url).trimmingCharacters(in: .whitespaces)
    m.reopened = false
    if m.source.isEmpty { m.release() } else { m.open() }
    return .vNull
}

func uiMediaStart(_ node: V, _ seconds: V) -> V { mediaView(node)?.start = max(0.0, dbl(seconds)); return .vNull }

/// "Name: value" lines.
func uiMediaHeaders(_ node: V, _ lines: V) -> V {
    mediaView(node)?.headers = rtS(lines).components(separatedBy: "\n").compactMap { line in
        guard let colon = line.firstIndex(of: ":") else { return nil }
        let name = line[..<colon].trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? nil : (name, line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces))
    }
    return .vNull
}

/// Each sidecar's address, label and language, a line each in the same order.
func uiMediaSidecars(_ node: V, _ urls: V, _ labels: V, _ languages: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    let u = rtS(urls).components(separatedBy: "\n"), l = rtS(labels).components(separatedBy: "\n")
    let g = rtS(languages).components(separatedBy: "\n")
    m.sidecars = u.enumerated().compactMap { i, url in
        url.isEmpty ? nil : (url, i < l.count ? l[i] : "", i < g.count ? g[i] : "")
    }
    return .vNull
}

func uiMediaPlaying(_ node: V, _ on: V) -> V { mediaView(node)?.setPlaying(rtB(on)); return .vNull }
func uiMediaLoop(_ node: V, _ on: V) -> V { mediaView(node)?.looping = rtB(on); return .vNull }

func uiMediaMuted(_ node: V, _ on: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    m.muted = rtB(on)
    m.sound()
    return .vNull
}

func uiMediaVolume(_ node: V, _ volume: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    m.volume = dbl(volume)
    m.sound()
    return .vNull
}

func uiMediaRate(_ node: V, _ rate: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    m.rate = dbl(rate)
    m.sound()
    m.speak()
    return .vNull
}

func uiMediaCover(_ node: V, _ on: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    m.cover = rtB(on)
    m.fit()
    return .vNull
}

/// The milliseconds VLC holds from the network before playing: responsive, balanced (as unset) or slow.
func uiMediaCaching(_ node: V, _ named: V) -> V {
    let ms: Int
    switch rtS(named) {
    case "responsive": ms = 600
    case "slow": ms = 5000
    default: ms = 1500
    }
    mediaView(node)?.caching = ms
    return .vNull
}

func uiMediaHear(_ node: V, _ language: V) -> V {
    mediaView(node)?.hear = rtS(language).trimmingCharacters(in: .whitespaces)
    return .vNull
}

func uiMediaSelect(_ node: V, _ kind: V, _ id: V) -> V { mediaView(node)?.select(rtS(kind), rtS(id)); return .vNull }

func uiMediaAids(_ node: V, _ clarity: V, _ night: V) -> V {
    mediaView(node)?.setAids(dbl(clarity), dbl(night))
    return .vNull
}

func uiMediaAidsHeard(_ node: V) -> V { .vBool(mediaView(node)?.aidsHeard ?? false) }

func uiMediaNowPlaying(_ node: V, _ title: V, _ artist: V, _ album: V, _ art: V, _ seconds: V) -> V {
    guard let m = mediaView(node) else { return .vNull }
    let t = rtS(title)
    m.setAbout(t.isEmpty ? nil : NowPlayingAbout(title: t, artist: rtS(artist), album: rtS(album), art: rtS(art),
                                                  seconds: dbl(seconds)))
    return .vNull
}

func uiMediaPosition(_ node: V) -> V { .vDbl(mediaView(node)?.position ?? 0.0) }
func uiMediaDuration(_ node: V) -> V { .vDbl(mediaView(node)?.duration ?? 0.0) }
func uiMediaSeek(_ node: V, _ seconds: V) -> V { mediaView(node)?.seek(dbl(seconds)); return .vNull }
func uiMediaWaiting(_ node: V) -> V { .vBool(mediaView(node)?.waiting ?? false) }
func uiMediaBandwidth(_ node: V) -> V { .vInt(mediaView(node)?.bandwidth ?? 0) }

func uiMediaSize(_ node: V, _ axis: V) -> V {
    guard let size = mediaView(node)?.player?.videoSize else { return .vDbl(0) }
    return .vDbl(Double(rtB(axis) ? size.height : size.width))
}

func uiMediaTrackCount(_ node: V) -> V { .vInt(mediaView(node)?.tracks().count ?? 0) }

/// The track's id, kind, language, label or format (0 to 4).
func uiMediaTrack(_ node: V, _ index: V, _ field: V) -> V {
    guard let m = mediaView(node), case .vInt(let i) = rtToInt(index), case .vInt(let f) = rtToInt(field) else {
        return .vStr("")
    }
    let all = m.tracks()
    return .vStr(i >= 0 && i < all.count && f >= 0 && f < all[i].count ? all[i][f] : "")
}

func uiMediaDiagnostics(_ node: V, _ field: V) -> V { .vStr(mediaView(node)?.diagnostics(rtS(field)) ?? "") }

func uiMediaOnEvent(_ node: V, _ f: V) -> V { mediaView(node)?.heard = f; return .vNull }
/// Each live player's handler, a root of the collector (uiHooks) while the player is.
func uiMediaRoots() -> [V] { MediaView.live.allObjects.map { $0.heard } }
func uiMediaRelease(_ node: V) -> V { mediaView(node)?.release(); return .vNull }

#else

// built without VLCKit: nothing plays, and every node is an empty view
func uiMediaPlays() -> V { .vBool(false) }
func uiMedia(_ video: V) -> V { uiContainer() }
func uiMediaSource(_ node: V, _ url: V) -> V { .vNull }
func uiMediaStart(_ node: V, _ seconds: V) -> V { .vNull }
func uiMediaHeaders(_ node: V, _ lines: V) -> V { .vNull }
func uiMediaSidecars(_ node: V, _ urls: V, _ labels: V, _ languages: V) -> V { .vNull }
func uiMediaPlaying(_ node: V, _ on: V) -> V { .vNull }
func uiMediaLoop(_ node: V, _ on: V) -> V { .vNull }
func uiMediaMuted(_ node: V, _ on: V) -> V { .vNull }
func uiMediaVolume(_ node: V, _ volume: V) -> V { .vNull }
func uiMediaRate(_ node: V, _ rate: V) -> V { .vNull }
func uiMediaCover(_ node: V, _ on: V) -> V { .vNull }
func uiMediaCaching(_ node: V, _ named: V) -> V { .vNull }
func uiMediaHear(_ node: V, _ language: V) -> V { .vNull }
func uiMediaSelect(_ node: V, _ kind: V, _ id: V) -> V { .vNull }
func uiMediaAids(_ node: V, _ clarity: V, _ night: V) -> V { .vNull }
func uiMediaAidsHeard(_ node: V) -> V { .vBool(false) }
func uiMediaNowPlaying(_ node: V, _ title: V, _ artist: V, _ album: V, _ art: V, _ seconds: V) -> V { .vNull }
func uiMediaPosition(_ node: V) -> V { .vDbl(0) }
func uiMediaDuration(_ node: V) -> V { .vDbl(0) }
func uiMediaSeek(_ node: V, _ seconds: V) -> V { .vNull }
func uiMediaWaiting(_ node: V) -> V { .vBool(false) }
func uiMediaBandwidth(_ node: V) -> V { .vInt(0) }
func uiMediaSize(_ node: V, _ axis: V) -> V { .vDbl(0) }
func uiMediaTrackCount(_ node: V) -> V { .vInt(0) }
func uiMediaTrack(_ node: V, _ index: V, _ field: V) -> V { .vStr("") }
func uiMediaDiagnostics(_ node: V, _ field: V) -> V { .vStr("") }
func uiMediaOnEvent(_ node: V, _ f: V) -> V { .vNull }
func uiMediaRoots() -> [V] { [] }
func uiMediaRelease(_ node: V) -> V { .vNull }

#endif

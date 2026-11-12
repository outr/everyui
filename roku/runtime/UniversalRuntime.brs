' everyui — Roku support runtime.
'
' The render seam's primitives as plain BrightScript globals. `emitRoku`
' copies this into the emitted channel's components/ and adds a <script> line
' for it to the generated AppScene.xml, beside the compiler's ScalaRuntime.brs
' (the Scala semantics BrightScript lacks). This file is the backend's own:
' what a render node and the platform can do.
'
' Every function here is declared as an @native facade in
' `everyui.roku.facades` — the Scala side and this file are one API,
' mirrored by web/universal-runtime.js on the DOM backend.

function uiRoot() as object
    return GetGlobalAA().appRoot
end function

' The panel's UI pixels per design pixel: 2/3 on a Roku whose UI is 720p, 1 at 1080p. SceneGraph scales a node's
' size to the panel, but draws a 9-patch's fixed corners at the image's own pixels, so a corner image is chosen in
' panel pixels or it is squeezed into an ellipse.
function uiPanelScale() as float
    ga = GetGlobalAA()
    if ga.uiPanelScaleValue = invalid
        ga.uiPanelScaleValue = 1.0
        resolution = CreateObject("roDeviceInfo").GetUIResolution()
        if resolution <> invalid and resolution.height <> invalid and resolution.height > 0 then ga.uiPanelScaleValue = resolution.height / 1080.0
    end if
    return ga.uiPanelScaleValue
end function

' Layout units to the channel's 1920x1080 design space: 2, so a Roku lays out in the 960x540 units an Android TV at
' density 2 does and a screen comes out the same size on both. The render node multiplies positions, sizes and font
' sizes by it (a scaled root would draw text small and enlarge it). A sideload with pkg:/viewport.txt lays out in the
' design space itself (1): the frame tools/check.sh compares with the golden, pixel for pixel.
function uiScale() as float
    ga = GetGlobalAA()
    if ga.uiScaleValue = invalid
        ga.uiScaleValue = 2.0
        if uiLaunchArg("viewport") <> "" then ga.uiScaleValue = 1.0
    end if
    return ga.uiScaleValue
end function

' A SceneGraph Label, sized for 1080p and visible against the generated
' scene's light background.
function uiText() as object
    n = uiNode("Label")
    n.color = "0x1C1B1FFF"
    ga = GetGlobalAA()
    if ga.uiDefaultFont = invalid
        ga.uiDefaultFont = uiNode("Font")
        ga.uiDefaultFont.uri = "font:BoldSystemFontFile"
        ga.uiDefaultFont.size = 48
    end if
    n.font = ga.uiDefaultFont
    n.translation = [0.0, 0.0]
    uiApplyAlign(n)
    return n
end function

function uiGetText(node as object) as string
    if node = invalid then return ""
    return node.text
end function

sub uiSetText(node as object, text as string)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node <> invalid then node.text = text
end sub

function uiGetX(node as object) as float
    if node = invalid then return 0.0
    t = node.translation
    if t = invalid then return 0.0
    return t[0]
end function

sub uiSetX(node as object, x as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    t = node.translation
    y = 0.0
    if t <> invalid then y = t[1]
    node.translation = [x, y]
end sub

function uiGetY(node as object) as float
    if node = invalid then return 0.0
    t = node.translation
    if t = invalid then return 0.0
    return t[1]
end function

sub uiSetY(node as object, y as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    t = node.translation
    x = 0.0
    if t <> invalid then x = t[0]
    node.translation = [x, y]
end sub

' A Poster loading from the channel package. Rotation on a SceneGraph node is
' about its ORIGIN, not its centre, so the pivot is moved to the middle to
' match how the DOM's rotate() behaves — the two backends must agree. The
' field is `scaleRotateCenter` (Group, in LOCAL PIXELS); `rotationPivot` does
' not exist and Roku only warns about it at runtime.
function uiImage(uri as string, width as float, height as float) as object
    n = uiNode("Poster")
    n.uri = uri
    n.width = width
    n.height = height
    n.scaleRotateCenter = [width / 2.0, height / 2.0]
    n.translation = [0.0, 0.0]
    return n
end function

' The seam's rotation is clockwise, as CSS rotate() is. SceneGraph's is
' counter-clockwise, so the sign flips here.
function uiGetRotation(node as object) as float
    if node = invalid then return 0.0
    r = node.rotation
    if r = invalid then return 0.0
    return -r
end function

sub uiSetRotation(node as object, radians as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node <> invalid then node.rotation = -radians
end sub

' Larger or smaller about the node's own centre, which uiSetWidth and uiSetHeight keep as its pivot,
' so a focused card grows where the DOM and the view systems grow it.
sub uiSetScale(node as object, factor as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    node.scale = [factor, factor]
end sub

' The whole app drawn factor times its size (everyui.theme.UiScale): the root group scaled about the screen's top
' left, which uiSetWidth and uiSetHeight leave as its pivot. SceneGraph enlarges what it has drawn, as a picture is
' enlarged, so words drawn larger than 1 are a little soft.
sub uiSetRootScale(factor as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    root = GetGlobalAA().appRoot
    if root = invalid then return
    root.scaleRotateCenter = [0.0, 0.0]
    root.scale = [factor, factor]
end sub

sub uiSetFontSize(node as object, size as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    f = uiNode("Font")
    f.uri = "font:BoldSystemFontFile"
    f.size = Int(size)
    node.font = f
end sub

' The screen in layout units: the manifest's design resolution (ui_resolutions=fhd), the space SceneGraph lays
' out in whatever the panel, over the root's scale.
function uiScreenWidth() as float
    return 1920.0 / uiScale()
end function

function uiScreenHeight() as float
    return 1080.0 / uiScale()
end function

sub uiAppend(parent as object, child as object)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if parent <> invalid and child <> invalid then
        ' out of the parent it is in first, as uiInsert does: a component put in another container is moved
        old = child.getParent()
        if old <> invalid then old.removeChild(child)
        parent.appendChild(child)
    end if
end sub

sub uiInsert(parent as object, child as object, index as integer)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if parent <> invalid and child <> invalid then
        old = child.getParent()
        if old <> invalid then old.removeChild(child)
        parent.insertChild(child, index)
    end if
end sub

' The line to the console, and kept as the last logged (RtTurnCheck says it of a turn that was cut off).
sub uiLog(message as dynamic)
    print "[ui] "; message
    ga = GetGlobalAA()
    ga.uiLastLog = message
    ga.uiLastLogAt = UpTime(0)
end sub

' Milliseconds since the first call — the animation clock. roTimespan is the
' only sub-second clock Roku exposes.
function uiTicks() as float
    ga = GetGlobalAA()
    if ga.uiClock = invalid then ga.uiClock = CreateObject("roTimespan")
    return ga.uiClock.TotalMilliseconds()
end function

' Drive `tick` (a defunctionalised Scala closure) once per render frame. A SceneGraph Timer of duration 0 fires on
' every frame the renderer draws, which is what makes this a real frame rate; but with nothing to draw, it fires as
' fast as the thread can turn (5,000 times a second on a Roku TV), taking the thread from everything else. So when
' it fires three times running within a few milliseconds of the last, nothing is being drawn, and it waits a frame
' between fires, trying 0 again every quarter second in case something is. (A Timer of a frame all the time beats
' against the display's, and skips some.)
sub uiAnimate(tick as dynamic)
    ga = GetGlobalAA()
    uiDebugFlags()
    ga.uiTick = tick
    ga.uiFast = 0
    t = uiNode("Timer")
    t.repeat = true
    t.duration = 0
    ga.appScene.appendChild(t)
    ga.uiTimer = t
    t.observeField("fire", "uiOnFrame")
    t.control = "start"
end sub

sub uiOnFrame()
    RtTurnCheck()
    ga = GetGlobalAA()
    now = uiTicks()
    t = ga.uiTimer
    if ga.uiFrameAt = invalid then ga.uiFrameAt = now
    if t.duration = 0
        if now - ga.uiFrameAt < 4 then ga.uiFast = ga.uiFast + 1 else ga.uiFast = 0
        if ga.uiFast >= 3
            ga.uiIdleSince = now
            t.duration = 1.0 / 60.0
            t.control = "start"
        end if
    else if now - ga.uiIdleSince >= 250
        ga.uiFast = 0
        t.duration = 0
        t.control = "start"
    end if
    ga.uiFrameAt = now
    if ga.uiPerf <> invalid then uiPerfFrame(now)
    if ga.uiTick <> invalid then RtGuard(ga.uiTick, [])
    if ga.uiPerf <> invalid then ga.uiPerf.tick = uiTicks() - now
end sub

' ── what each frame cost: rokuperf=1 ──

' Launched with rokuperf=1 (an ECP launch parameter, or pkg:/rokuperf.txt), each frame that did anything, or took 50 ms
' or more, is said on the console when the next begins: from its start to the next one's, how long the frame's own
' update took (tick), the keys handled since and how long they took, the later and after turns, and what is left of the
' frame (sg): SceneGraph drawing, and any handler not timed here (a picture loaded, a player's state); then the nodes
' made and taken out of their parents, the runtime's writes to nodes (each a field or a few) and the texts measured.
'
'   [perf] 312ms: tick 4, keys 1 in 280, later 20, after 0, sg 8; nodes +14 -3, writes 220, measures 6
'
' Off, nothing is counted: each count is one test of an unset field.
'
' The flags are read as the channel starts and again whenever a launch reaches it while it runs (uiArrived): a launch
' of a channel already running, which a sideload leaves running, is a resume or an input event (ECP /input), not a
' start, so `rokuperf=0` or `=1` and `httplog=0` or `=1` sent either way turn them off or on there and then. Kept on
' for every start until turned off: `everyui_perf=1` (a launch, an input event or a resume), which writes the
' key `everyui_perf` in the registry's everyui section, as an app may itself (Platform.saveSetting); `everyui_perf=0`
' takes it out. A flag given with a launch wins over the key for that run.
sub uiDebugFlags()
    ga = GetGlobalAA()
    section = CreateObject("roRegistrySection", "everyui")
    kept = section.Read("everyui_perf") = "1"
    perf = uiFlagSaid("rokuperf", kept)
    if perf and ga.uiPerf = invalid
        ga.uiPerf = { at: -1.0, tick: 0.0, keys: 0, key: 0.0, later: 0.0, after: 0.0, nodes: 0, removes: 0, writes: 0, measures: 0 }
        print "[perf] counting each frame (rokuperf=1)"
    else if not perf and ga.uiPerf <> invalid
        ga.uiPerf = invalid
        print "[perf] off"
    end if
    http = perf or uiFlagSaid("httplog", kept)
    if http <> (ga.uiHttpLog = true)
        if http then print "[http] timing each request (httplog=1)" else print "[http] timing off"
    end if
    ga.uiHttpLog = http
end sub

' Whether the launch flag `name` is on: as the launch said ("1" on, anything else off), or `otherwise` where it said
' nothing of it.
function uiFlagSaid(name as string, otherwise as boolean) as boolean
    params = GetGlobalAA().uiLaunchParams
    if params = invalid or not params.DoesExist(name) then return otherwise
    return uiLaunchArg(name).Trim() = "1"
end function

' `everyui_perf=1` or `=0` from a launch that reached the running channel: the registry's key written or taken out,
' and the flags read again.
sub uiDebugArrived(own as object)
    said = own.everyui_perf
    if said <> invalid
        section = CreateObject("roRegistrySection", "everyui")
        if said.Trim() = "1"
            section.Write("everyui_perf", "1")
            print "[perf] kept on for every start (everyui_perf=1)"
        else
            section.Delete("everyui_perf")
            print "[perf] no longer kept on (everyui_perf=0)"
        end if
        section.Flush()
        ' (the key decides from now on, over a flag an earlier launch gave, unless this one gives it too)
        params = GetGlobalAA().uiLaunchParams
        if own.rokuperf = invalid then params.Delete("rokuperf")
        if own.httplog = invalid then params.Delete("httplog")
    end if
    if said <> invalid or own.rokuperf <> invalid or own.httplog <> invalid then uiDebugFlags()
end sub

' The frame before this one, said, and the counts begun again.
sub uiPerfFrame(now as float)
    p = GetGlobalAA().uiPerf
    if p.at >= 0
        ms = now - p.at
        timed = p.tick + p.key + p.later + p.after
        if ms >= 50 or p.keys > 0 or p.nodes > 0 or p.removes > 0 or p.writes > 0
            ' one string, built up and printed whole: the device's compiler refused this as one long print list
            said = "[perf] " + Int(ms).ToStr() + "ms: tick " + Int(p.tick).ToStr()
            said = said + ", keys " + Int(p.keys).ToStr() + " in " + Int(p.key).ToStr()
            said = said + ", later " + Int(p.later).ToStr() + ", after " + Int(p.after).ToStr()
            said = said + ", sg " + Int(ms - timed).ToStr() + "; nodes +" + Int(p.nodes).ToStr()
            said = said + " -" + Int(p.removes).ToStr() + ", writes " + Int(p.writes).ToStr()
            said = said + ", measures " + Int(p.measures).ToStr()
            print said
        end if
    end if
    p.at = now
    p.tick = 0.0
    p.keys = 0
    p.key = 0.0
    p.later = 0.0
    p.after = 0.0
    p.nodes = 0
    p.removes = 0
    p.writes = 0
    p.measures = 0
end sub

' A key to the hook it stands for (uiOnKey), when it came kept (the cycle collector waits for the keys to stop), and
' timed with rokuperf=1.
function uiKeyHeard(env as object, args as object) as dynamic
    at = uiTicks()
    ga = GetGlobalAA()
    ga.uiKeyAt = at
    taken = RtInvoke(env.inner, args)
    p = ga.uiPerf
    if p <> invalid
        p.keys = p.keys + 1
        p.key = p.key + uiTicks() - at
    end if
    return taken
end function

' A SceneGraph node of `kind`, counted (rokuperf=1).
function uiNode(kind as string) as object
    if m.uiPerf <> invalid then m.uiPerf.nodes = m.uiPerf.nodes + 1
    return CreateObject("roSGNode", kind)
end function

' Run `f` on a later turn of the render thread's event loop, on a fresh stack: a one-shot Timer drains the queue.
sub uiLater(f as dynamic)
    ga = GetGlobalAA()
    if ga.uiLaterQueue = invalid then ga.uiLaterQueue = []
    ga.uiLaterQueue.push(f)
    if ga.uiLaterTimer = invalid
        t = uiNode("Timer")
        t.repeat = false
        t.duration = 0.001
        ga.appScene.appendChild(t)
        t.observeField("fire", "uiOnLater")
        ga.uiLaterTimer = t
    end if
    if ga.uiLaterArmed <> true
        ga.uiLaterArmed = true
        ga.uiLaterTimer.control = "start"
    end if
end sub

' Run `f` once `seconds` have passed: a one-shot Timer of its own while it waits. The Timers are kept on the scene
' once they have fired, and used again: the focus layer alone waits a quarter second at a time, all the time, and a
' Timer made, put on the scene, watched and taken off again for each was a node a frame for the render thread.
sub uiAfter(seconds as float, f as dynamic)
    ga = GetGlobalAA()
    if ga.uiAfters = invalid then ga.uiAfters = {}
    if ga.uiAfterIdle = invalid then ga.uiAfterIdle = []
    t = ga.uiAfterIdle.Pop()
    if t = invalid
        t = uiNode("Timer")
        t.id = "uiAfter" + RtNextId().ToStr()
        t.repeat = false
        ga.appScene.appendChild(t)
        t.observeField("fire", "uiOnAfter")
    end if
    t.duration = seconds
    ga.uiAfters[t.id] = { f: f, node: t }
    t.control = "start"
end sub

sub uiOnAfter(event as object)
    ga = GetGlobalAA()
    t = event.getRoSGNode()
    entry = ga.uiAfters[t.id]
    if entry = invalid then return
    ga.uiAfters.Delete(t.id)
    ga.uiAfterIdle.Push(t)
    at = uiTicks()
    RtGuard(entry.f, [])
    if ga.uiPerf <> invalid then ga.uiPerf.after = ga.uiPerf.after + uiTicks() - at
end sub

sub uiOnLater()
    RtTurnCheck()
    ga = GetGlobalAA()
    ga.uiLaterArmed = false
    queue = ga.uiLaterQueue
    ga.uiLaterQueue = []
    at = uiTicks()
    for each f in queue
        RtGuard(f, [])
    end for
    if ga.uiPerf <> invalid then ga.uiPerf.later = ga.uiPerf.later + uiTicks() - at
end sub

' Math intrinsics for Platform.sqrt / pow / sin / cos.
' A number from 0 up to 1, at random.
function uiRandom() as float
    return Rnd(0)
end function

function uiSqrt(x as dynamic) as dynamic
    return Sqr(x)
end function

function uiPow(x as dynamic, y as dynamic) as dynamic
    return x ^ y
end function

function uiSin(x as dynamic) as dynamic
    return Sin(x)
end function

function uiCos(x as dynamic) as dynamic
    return Cos(x)
end function

' "#rrggbb" -> SceneGraph's "0xRRGGBBAA".
sub uiSetColor(node as object, hex as string)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    node.color = "0x" + UCase(Mid(hex, 2, 6)) + "FF"
end sub

' The bundled faces: emitRoku copies assets/fonts/*.ttf to pkg:/fonts/.
function uiFontUri(family as string, weight as integer) as string
    if family = "Open Sans"
        if weight >= 600 then return "pkg:/fonts/OpenSans-SemiBold.ttf"
        return "pkg:/fonts/OpenSans-Regular.ttf"
    end if
    if family = "Material Icons" then return "pkg:/fonts/MaterialIcons-Regular.ttf"
    if family = "Roboto Mono" then return "pkg:/fonts/RobotoMono-Regular.ttf"
    if weight >= 600 then return "font:BoldSystemFontFile"
    return "font:SystemFontFile"
end function

' A SceneGraph Font's size is whole pixels. On a panel whose UI resolution is
' below the channel's 1080p design space, small text is rasterised at fewer real
' pixels and its glyph advances round down, so it comes out narrower than the
' same size in a browser. Calibrate once per face and size: measure a reference
' string at four times the size (where rounding is negligible) and pick the
' whole size whose width comes closest to a quarter of it.
function uiCalibratedSize(uri as string, size as float) as integer
    ga = GetGlobalAA()
    if ga.uiFontSizes = invalid then ga.uiFontSizes = {}
    key = uri + "@" + Str(size)
    cached = ga.uiFontSizes[key]
    if cached <> invalid then return cached
    probe = uiNode("Label")
    probe.text = "Hamburgefonstiv Example 0123"
    big = uiNode("Font")
    big.uri = uri
    big.size = Int(size * 4.0 + 0.5)
    probe.font = big
    target = probe.boundingRect().width / 4.0
    best = Int(size + 0.5)
    bestErr = 1.0e9
    for candidate = Int(size + 0.5) to Int(size * 1.25 + 1.0)
        f = uiNode("Font")
        f.uri = uri
        f.size = candidate
        probe.font = f
        err = Abs(probe.boundingRect().width - target)
        if err < bestErr
            bestErr = err
            best = candidate
        end if
    end for
    ga.uiFontSizes[key] = best
    return best
end function

' The language the app is in (Platform.setLanguage): which CJK glyphs a text takes, Traditional for zh_Hant.
sub uiSetLanguage(code as string)
    GetGlobalAA().uiLanguageCode = code
end sub

' The script a character is written in, for choosing a face: "arabic", "cjk", "latin" (a letter or a digit), or ""
' for what belongs to whatever it stands beside (spaces and punctuation).
function uiScriptOf(c as integer) as string
    if c < 128
        if (c >= 48 and c <= 57) or (c >= 65 and c <= 90) or (c >= 97 and c <= 122) then return "latin"
        return ""
    end if
    if c >= &hE000 and c <= &hF8FF then return "icon"
    if c >= &h2000
        ' the symbols the CJK face carries (RokuChannel.fallbackFonts adds them to its cut): suits, check marks, sun, stars
        if (c >= &h2600 and c <= &h2603) or c = &h2605 or c = &h2606 or (c >= &h2660 and c <= &h266F) or c = &h2713 then return "cjk"
        if uiEmojiIcon(c) <> 0 or uiEmojiDropped(c) then return "emoji"
    end if
    if (c >= &h0600 and c <= &h06FF) or (c >= &h0750 and c <= &h077F) or (c >= &h08A0 and c <= &h08FF) then return "arabic"
    if (c >= &hFB50 and c <= &hFDFF) or (c >= &hFE70 and c <= &hFEFF) then return "arabic"
    if (c >= &h2E80 and c <= &h9FFF) or (c >= &hF900 and c <= &hFAFF) or (c >= &hFF00 and c <= &hFFEF) then return "cjk"
    if (c >= &h2000 and c <= &h206F) or (c >= &h00A0 and c <= &h00BF) then return ""
    return "latin"
end function

' Arabic letters by code: how each joins its neighbours (D both sides, R only the letter before it, U neither) and its
' presentation forms, isolated, final, initial and medial (0 where a letter has none).
function uiArabicLetters() as object
    ga = GetGlobalAA()
    if ga.uiArabicLetters <> invalid then return ga.uiArabicLetters
    rows = [
        [&h0621, "U", &hFE80, 0, 0, 0], [&h0622, "R", &hFE81, &hFE82, 0, 0], [&h0623, "R", &hFE83, &hFE84, 0, 0],
        [&h0624, "R", &hFE85, &hFE86, 0, 0], [&h0625, "R", &hFE87, &hFE88, 0, 0], [&h0626, "D", &hFE89, &hFE8A, &hFE8B, &hFE8C],
        [&h0627, "R", &hFE8D, &hFE8E, 0, 0], [&h0628, "D", &hFE8F, &hFE90, &hFE91, &hFE92], [&h0629, "R", &hFE93, &hFE94, 0, 0],
        [&h062A, "D", &hFE95, &hFE96, &hFE97, &hFE98], [&h062B, "D", &hFE99, &hFE9A, &hFE9B, &hFE9C],
        [&h062C, "D", &hFE9D, &hFE9E, &hFE9F, &hFEA0], [&h062D, "D", &hFEA1, &hFEA2, &hFEA3, &hFEA4],
        [&h062E, "D", &hFEA5, &hFEA6, &hFEA7, &hFEA8], [&h062F, "R", &hFEA9, &hFEAA, 0, 0], [&h0630, "R", &hFEAB, &hFEAC, 0, 0],
        [&h0631, "R", &hFEAD, &hFEAE, 0, 0], [&h0632, "R", &hFEAF, &hFEB0, 0, 0], [&h0633, "D", &hFEB1, &hFEB2, &hFEB3, &hFEB4],
        [&h0634, "D", &hFEB5, &hFEB6, &hFEB7, &hFEB8], [&h0635, "D", &hFEB9, &hFEBA, &hFEBB, &hFEBC],
        [&h0636, "D", &hFEBD, &hFEBE, &hFEBF, &hFEC0], [&h0637, "D", &hFEC1, &hFEC2, &hFEC3, &hFEC4],
        [&h0638, "D", &hFEC5, &hFEC6, &hFEC7, &hFEC8], [&h0639, "D", &hFEC9, &hFECA, &hFECB, &hFECC],
        [&h063A, "D", &hFECD, &hFECE, &hFECF, &hFED0], [&h0640, "C", &h0640, &h0640, &h0640, &h0640],
        [&h0641, "D", &hFED1, &hFED2, &hFED3, &hFED4], [&h0642, "D", &hFED5, &hFED6, &hFED7, &hFED8],
        [&h0643, "D", &hFED9, &hFEDA, &hFEDB, &hFEDC], [&h0644, "D", &hFEDD, &hFEDE, &hFEDF, &hFEE0],
        [&h0645, "D", &hFEE1, &hFEE2, &hFEE3, &hFEE4], [&h0646, "D", &hFEE5, &hFEE6, &hFEE7, &hFEE8],
        [&h0647, "D", &hFEE9, &hFEEA, &hFEEB, &hFEEC], [&h0648, "R", &hFEED, &hFEEE, 0, 0], [&h0649, "R", &hFEEF, &hFEF0, 0, 0],
        [&h064A, "D", &hFEF1, &hFEF2, &hFEF3, &hFEF4], [&h067E, "D", &hFB56, &hFB57, &hFB58, &hFB59],
        [&h0686, "D", &hFB7A, &hFB7B, &hFB7C, &hFB7D], [&h0698, "R", &hFB8A, &hFB8B, 0, 0],
        [&h06A9, "D", &hFB8E, &hFB8F, &hFB90, &hFB91], [&h06AF, "D", &hFB92, &hFB93, &hFB94, &hFB95],
        [&h06CC, "D", &hFBFC, &hFBFD, &hFBFE, &hFBFF]
    ]
    table = {}
    for each r in rows
        table[r[0].ToStr()] = { join: r[1], forms: [r[2], r[3], r[4], r[5]] }
    end for
    ga.uiArabicLetters = table
    return table
end function

' A combining mark (harakat, superscript alef, Quranic marks): it sits on the letter before it and joins nothing.
function uiArabicMark(c as integer) as boolean
    return (c >= &h064B and c <= &h065F) or c = &h0670 or (c >= &h06D6 and c <= &h06ED)
end function

' An Arabic run shaped and in the order it is drawn, left to right. A Roku Label joins Arabic letters but lays them
' out left to right, so the text arrives here already in the letters' positional forms (the presentation forms, which
' no shaper changes again), lam-alef as its ligature, and its letters, each with its marks, reversed; brackets turn
' to face the way they read.
function uiShapeArabic(text as string) as string
    letters = uiArabicLetters()
    codes = []
    n = Len(text)
    for i = 1 to n
        codes.push(Asc(Mid(text, i, 1)))
    end for
    clusters = []
    lastJoin = "U"
    i = 0
    while i < n
        c = codes[i]
        if uiArabicMark(c)
            if clusters.count() = 0 then clusters.push("")
            clusters[clusters.count() - 1] = clusters[clusters.count() - 1] + Chr(c)
            i = i + 1
        else
            letter = letters[c.ToStr()]
            if letter = invalid
                mirrored = c
                if c = 40 then mirrored = 41
                if c = 41 then mirrored = 40
                if c = 91 then mirrored = 93
                if c = 93 then mirrored = 91
                if c = 123 then mirrored = 125
                if c = 125 then mirrored = 123
                if c = &hAB then mirrored = &hBB
                if c = &hBB then mirrored = &hAB
                clusters.push(Chr(mirrored))
                lastJoin = "U"
                i = i + 1
            else
                j = i + 1
                while j < n and uiArabicMark(codes[j])
                    j = j + 1
                end while
                nextJoin = "U"
                nextCode = 0
                if j < n
                    nextCode = codes[j]
                    following = letters[nextCode.ToStr()]
                    if following <> invalid then nextJoin = following.join
                end if
                joinsBefore = (lastJoin = "D" or lastJoin = "C") and letter.join <> "U"
                if c = &h0644 and (nextCode = &h0622 or nextCode = &h0623 or nextCode = &h0625 or nextCode = &h0627)
                    ligature = &hFEFB
                    if nextCode = &h0622 then ligature = &hFEF5
                    if nextCode = &h0623 then ligature = &hFEF7
                    if nextCode = &h0625 then ligature = &hFEF9
                    if joinsBefore then ligature = ligature + 1
                    cluster = Chr(ligature)
                    for k = i + 1 to j - 1
                        cluster = cluster + Chr(codes[k])
                    end for
                    clusters.push(cluster)
                    lastJoin = "R"
                    i = j + 1
                else
                    joinsAfter = letter.join = "D" or letter.join = "C"
                    joinsAfter = joinsAfter and (nextJoin = "D" or nextJoin = "R" or nextJoin = "C")
                    form = 0
                    if joinsBefore and joinsAfter
                        form = 3
                    else if joinsBefore
                        form = 1
                    else if joinsAfter
                        form = 2
                    end if
                    glyph = letter.forms[form]
                    if glyph = 0 then glyph = letter.forms[0]
                    clusters.push(Chr(glyph))
                    lastJoin = letter.join
                    i = i + 1
                end if
            end if
        end if
    end while
    clusters.Reverse()
    ' Reversed, an alef that followed a lam now follows it on the page in the lam's place, where the font's own lam-alef
    ' ligature would join the two: a zero-width non-joiner keeps them apart.
    visual = ""
    previousLam = false
    for each cluster in clusters
        code = Asc(cluster)
        isAlef = (code >= &hFE81 and code <= &hFE88) or code = &hFE8D or code = &hFE8E
        if previousLam and isAlef then visual = visual + Chr(&h200C)
        visual = visual + cluster
        previousLam = code >= &hFEDD and code <= &hFEE0
    end for
    return visual
end function

' The face for a script: the text's own for Latin; for Arabic and CJK the channel's fonts/fallback-* (bold at a weight
' of 600 or more, when the channel has one), or "" when it ships none.
function uiScriptFace(script as string, baseUri as string, weight as integer) as string
    if script = "latin" or script = "" then return baseUri
    if script = "icon" then return "pkg:/fonts/MaterialIcons-Regular.ttf"
    ga = GetGlobalAA()
    if ga.uiFaceFiles = invalid then ga.uiFaceFiles = {}
    name = script
    ext = ".ttf"
    if script = "cjk"
        name = "cjk-sc"
        if ga.uiLanguageCode = "zh_Hant" then name = "cjk-tc"
        ext = ".otf"
    end if
    key = name + "@" + weight.ToStr()
    uri = ga.uiFaceFiles[key]
    if uri = invalid
        ' the fonts the channel ships, listed once (roFileSystem is not to be had on the render thread)
        if ga.uiFontListing = invalid
            ga.uiFontListing = {}
            listing = ListDir("pkg:/fonts")
            if listing <> invalid
                for each entry in listing
                    ga.uiFontListing[entry] = true
                end for
            end if
        end if
        uri = ""
        if weight >= 600 and ga.uiFontListing.DoesExist("fallback-" + name + "-bold" + ext) then uri = "pkg:/fonts/fallback-" + name + "-bold" + ext
        if uri = "" and ga.uiFontListing.DoesExist("fallback-" + name + ext) then uri = "pkg:/fonts/fallback-" + name + ext
        ga.uiFaceFiles[key] = uri
    end if
    if uri = "" then return baseUri
    return uri
end function

' A Font node for a face at a size in pixels, made once.
function uiFontNode(uri as string, size as float) as object
    ga = GetGlobalAA()
    if ga.uiFontNodes = invalid then ga.uiFontNodes = {}
    key = uri + "@" + Str(size)
    f = ga.uiFontNodes[key]
    if f = invalid
        f = uiNode("Font")
        f.uri = uri
        f.size = uiCalibratedSize(uri, size)
        ga.uiFontNodes[key] = f
    end if
    return f
end function

' How wide and tall `text` is in a face at a size in pixels.
function uiMeasureText(text as string, uri as string, size as float) as object
    if m.uiPerf <> invalid then m.uiPerf.measures = m.uiPerf.measures + 1
    ga = GetGlobalAA()
    if ga.uiRunProbe = invalid then ga.uiRunProbe = uiNode("Label")
    probe = ga.uiRunProbe
    probe.font = uiFontNode(uri, size)
    probe.text = text
    rect = probe.boundingRect()
    return { width: rect.width, height: rect.height }
end function

' How wide one line of `text` is in a face at a size in pixels, as the Label draws it: measured whole, in the Font node
' the Label has, and kept (a wrapped paragraph is measured again at every width a layout tries).
function uiLineWidth(text as string, uri as string, size as float) as float
    ga = GetGlobalAA()
    if ga.uiLineWidths = invalid or ga.uiLineWidths.count() > 4000
        ga.uiLineWidths = CreateObject("roAssociativeArray")
        ga.uiLineWidths.SetModeCaseSensitive()
    end if
    key = uri + "@" + Str(size) + "|" + text
    wide = ga.uiLineWidths[key]
    if wide = invalid
        wide = uiMeasureText(text, uri, size).width
        ga.uiLineWidths[key] = wide
    end if
    return wide
end function

' How tall one line of a face at a size in pixels is, as a Label draws it: a one-line Label's own box, which a
' wrapping Label puts each of its lines in, `lineSpacing` apart. A wrapping Label's own bounding rect is not read: on a
' Roku TV (OS 15.3) the heights laid out from it came out lines short of what it drew.
function uiLineBox(uri as string, size as float) as float
    ga = GetGlobalAA()
    if ga.uiLineBoxes = invalid then ga.uiLineBoxes = {}
    key = uri + "@" + Str(size)
    tall = ga.uiLineBoxes[key]
    if tall = invalid
        tall = uiMeasureText("Ag", uri, size).height
        ga.uiLineBoxes[key] = tall
    end if
    return tall
end function

' How many lines a wrapping Label draws its text in at its width, worked out as it wraps: each line of the text (a
' blank one is a line) put into lines of whole words, a word going to the next line where the line measured whole with
' it would be wider than the Label. A word wider than the Label takes the lines it would be broken over, and the next
' word starts a line of its own. Where the Label might differ, more lines are counted rather than fewer: a line is
' broken a pixel short of the width, and never after a hyphen, where the Label may break one. At most the Label's
' maxLines. Text drawn in runs (uiRichText) is as many lines as it was put on.
function uiWrappedLines(label as object, uri as string, size as float) as integer
    if label = invalid then return 1
    if label.text = "" and label.getChildCount() > 0 and label.hasField("uiRunsLines") then return label.uiRunsLines
    width = label.width - 1.0
    if width <= 0 then return 1
    count = 0
    for each paragraph in label.text.Replace(Chr(13), "").Split(Chr(10))
        count = count + 1
        line = ""
        for each word in paragraph.Split(" ")
            if word <> ""
                if line <> ""
                    joined = line + " " + word
                    if uiLineWidth(joined, uri, size) > width
                        count = count + 1
                        line = ""
                    else
                        line = joined
                    end if
                end if
                if line = ""
                    line = word
                    wide = uiLineWidth(word, uri, size)
                    if wide > width then count = count - Int(-wide / width) - 1
                end if
            end if
        end for
    end for
    if count < 1 then count = 1
    if label.maxLines > 0 and count > label.maxLines then count = label.maxLines
    return count
end function

' The pixels a Label leaves between its lines.
function uiGetLineSpacing(node as object) as float
    if node = invalid or not node.hasField("lineSpacing") then return 0.0
    return node.lineSpacing
end function

' Text a Roku Label cannot draw alone: in more than one script, or in one its face has no glyphs for. It is drawn as
' runs of one script each, Labels inside `label`, each in a face that has its glyphs (uiScriptFace), in the order they
' read on screen. An Arabic run is shaped and reversed (uiShapeArabic), and in a paragraph that begins right to left (its
' first letter is Arabic) so is the order of the runs. Returns the width drawn in pixels, or -1 for text in the Latin
' script alone, which the label draws itself.
function uiRichText(label as object, text as string, baseUri as string, weight as integer, size as float, color as string) as float
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if label = invalid then return -1.0
    ' text of ASCII alone is in the Latin script or none, which the label draws itself: one test of the whole, where the
    ' letters were looked at one by one (a synopsis's 300, each time its words were cut to their box)
    ga = GetGlobalAA()
    if ga.uiNonAscii = invalid then ga.uiNonAscii = CreateObject("roRegex", "[^\x01-\x7F]", "")
    if not ga.uiNonAscii.IsMatch(text)
        children = label.getChildCount()
        if children > 0 then label.removeChildrenIndex(children, 0)
        label.text = text
        return -1.0
    end if
    ' words, each with the script its letters are in ("" for a word of spaces-and-punctuation only)
    words = []
    complex = false
    rtl = invalid
    for each piece in text.Split(" ")
        script = ""
        n = Len(piece)
        for i = 1 to n
            s = uiScriptOf(Asc(Mid(piece, i, 1)))
            if s = "emoji"
                ' a character the box has no glyph for: the text is drawn again without it (uiEmojiText)
                return uiRichText(label, uiEmojiText(text), baseUri, weight, size, color)
            else if s = "arabic" or s = "cjk" or s = "icon"
                script = s
                complex = true
                exit for
            else if s = "latin"
                script = "latin"
            end if
        end for
        if rtl = invalid and script <> "" then rtl = (script = "arabic")
        words.push({ text: piece, script: script })
    end for
    children = label.getChildCount()
    if not complex
        if children > 0 then label.removeChildrenIndex(children, 0)
        label.text = text
        return -1.0
    end if
    label.text = ""
    tint = color
    if color = "" then tint = label.color
    base = uiMeasureText("Ag", baseUri, size)
    space = uiMeasureText("A A", baseUri, size).width - uiMeasureText("AA", baseUri, size).width
    ' a label that wraps at a width puts its words on lines as they fit (uiWrapWords), each line drawn as the one line was
    lines = [words]
    if label.wrap and label.width > 0 then lines = uiWrapWords(words, label.width, label.maxLines, space, baseUri, weight, size)
    advance = base.height
    if lines.count() > 1 then advance = base.height + label.lineSpacing
    widest = 0.0
    index = 0
    for l = 0 to lines.count() - 1
        ' runs of consecutive words in one script; a word with no letters joins the run it follows
        runs = []
        for each w in lines[l]
            last = invalid
            if runs.count() > 0 then last = runs[runs.count() - 1]
            if last <> invalid and (w.script = "" or w.script = last.script)
                last.words.push(w.text)
            else
                script = w.script
                if script = "" then script = "latin"
                runs.push({ script: script, words: [w.text] })
            end if
        end for
        if rtl = true then runs.Reverse()
        x = 0.0
        first = index
        for each segment in runs
            runText = segment.words.Join(" ")
            if segment.script = "arabic" then runText = uiShapeArabic(runText)
            uri = uiScriptFace(segment.script, baseUri, weight)
            node = invalid
            if index < children then node = label.getChild(index)
            if node = invalid
                node = uiNode("Label")
                label.appendChild(node)
            end if
            node.font = uiFontNode(uri, size)
            node.text = runText
            node.color = tint
            measured = uiMeasureText(runText, uri, size)
            own = uiMeasureText("Ag", uri, size)
            ' the runs share a middle, whatever each face's line box; where along its line a run starts is kept for
            ' uiAlignRuns
            node.translation = [x, l * advance + (base.height - own.height) / 2.0]
            if not node.hasField("uiLineX") then node.addFields({ uiLineX: 0.0, uiLineWidth: 0.0 })
            node.uiLineX = x
            x = x + measured.width + space
            index = index + 1
        end for
        for i = first to index - 1
            drawn = label.getChild(i)
            drawn.uiLineWidth = x - space
        end for
        if x - space > widest then widest = x - space
    end for
    count = label.getChildCount()
    if count > index then label.removeChildrenIndex(count - index, index)
    if not label.hasField("uiRunsWidth") then label.addFields({ uiRunsWidth: 0.0, uiRunsLines: 1 })
    label.uiRunsWidth = widest
    label.uiRunsLines = lines.count()
    uiAlignRuns(label)
    return widest
end function

' Words on lines no wider than `width`, in reading order, each measured in the face it is drawn in (a word with no
' letters in the face of the word before it); a word wider than a line has one to itself. At most `maxLines` lines (0
' for no limit), the last cut short with an ellipsis where words are left over.
function uiWrapWords(words as object, width as float, maxLines as integer, space as float, baseUri as string, weight as integer, size as float) as object
    lines = []
    line = []
    x = 0.0
    script = "latin"
    for each w in words
        if w.script <> "" then script = w.script
        shown = w.text
        if script = "arabic" then shown = uiShapeArabic(shown)
        wide = uiMeasureText(shown, uiScriptFace(script, baseUri, weight), size).width
        if line.count() > 0 and x + space + wide > width
            lines.push(line)
            line = []
            x = 0.0
        end if
        if line.count() > 0 then x = x + space
        line.push({ text: w.text, script: w.script, width: wide })
        x = x + wide
    end for
    if line.count() > 0 then lines.push(line)
    if maxLines > 0 and lines.count() > maxLines
        last = lines[maxLines - 1]
        lines = uiTake(lines, maxLines)
        ellipsis = uiMeasureText(Chr(&h2026), baseUri, size).width
        used = 0.0
        for each w in last
            used = used + w.width + space
        end for
        while last.count() > 1 and used - space + ellipsis > width
            gone = last.Pop()
            used = used - gone.width - space
        end while
        tail = last[last.count() - 1]
        last[last.count() - 1] = { text: tail.text + Chr(&h2026), script: tail.script, width: tail.width + ellipsis }
    end if
    return lines
end function

' The first `n` elements of an array.
function uiTake(items as object, n as integer) as object
    taken = []
    for i = 0 to n - 1
        if i < items.count() then taken.push(items[i])
    end for
    return taken
end function

' A face by its file (pkg:/fonts/<file>.ttf): the one the text is measured by, whatever its family.
sub uiSetFontFile(node as object, uri as string, size as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    node.font = uiFontNode(uri, size)
end sub

sub uiSetFont(node as object, family as string, weight as integer, size as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    uri = uiFontUri(family, weight)
    node.font = uiFontNode(uri, size)
end sub


function uiGetWidth(node as object) as float
    if node = invalid then return 0.0
    return node.boundingRect().width
end function

function uiGetHeight(node as object) as float
    if node = invalid then return 0.0
    return node.boundingRect().height
end function

' How wide a Label's text is at exactly `size` pixels. A Font's size is whole
' pixels and small text is rasterised at the panel's resolution, so the Label's
' own bounding rect is a few pixels off the same text in a browser. Measured at
' sixteen times the size, where that rounding is negligible (at four times, a
' 720p panel still came out 1.3% narrow), and scaled back. One
' probe Label and one Font per face and size are reused, so measuring creates
' no nodes.
function uiTextWidth(node as object, uri as string, size as float) as float
    if m.uiPerf <> invalid then m.uiPerf.measures = m.uiPerf.measures + 1
    if node = invalid then return 0.0
    ga = GetGlobalAA()
    if ga.uiMeasureLabel = invalid
        ga.uiMeasureLabel = uiNode("Label")
        ga.uiMeasureFonts = {}
    end if
    big = Int(size * 32.0 + 0.5)
    key = uri + "@" + big.ToStr()
    f = ga.uiMeasureFonts[key]
    if f = invalid
        f = uiNode("Font")
        f.uri = uri
        f.size = big
        ga.uiMeasureFonts[key] = f
    end if
    probe = ga.uiMeasureLabel
    probe.font = f
    probe.text = node.text
    return probe.boundingRect().width * size / big
end function

' A container: a Group, which draws nothing. A Rectangle can paint a background, but SceneGraph fills even a
' transparent one, and a screen of them (rails a few thousand pixels long) held a Roku to 30 frames a second; a
' container with a colour has a Rectangle behind its children instead (uiFill).
function uiContainer() as object
    n = uiNode("Group")
    n.translation = [0.0, 0.0]
    return n
end function

' A transparent Rectangle, to be given a colour and a size (a square edge).
function uiRect() as object
    n = uiNode("Rectangle")
    n.color = "0x00000000"
    n.width = 0
    n.height = 0
    n.translation = [0.0, 0.0]
    return n
end function

' A container's colour: a Rectangle `width` by `height` behind its children (`existing`, or a new one), or none for
' "". Anything else (the render root, a Rectangle) takes the colour itself.
function uiFill(node as object, existing as dynamic, hex as string, width as float, height as float) as dynamic
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return invalid
    if node.subtype() <> "Group" or node.isSameNode(GetGlobalAA().appRoot)
        uiSetBackgroundColor(node, hex)
        return invalid
    end if
    if hex = ""
        if existing <> invalid then uiRemove(existing)
        return invalid
    end if
    fill = existing
    if fill = invalid
        fill = uiRect()
        node.insertChild(fill, 0)
    end if
    uiSetBackgroundColor(fill, hex)
    fill.width = width
    fill.height = height
    return fill
end function

' Posters keep rotating about their centre as their size changes.
sub uiSetWidth(node as object, width as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    if not node.hasField("width")
        ' a Group has no size of its own, but grows about its middle all the same; the root about the screen's corner
        if node.hasField("scaleRotateCenter") and not node.isSameNode(GetGlobalAA().appRoot)
            pivot = node.scaleRotateCenter
            node.scaleRotateCenter = [width / 2.0, pivot[1]]
        end if
        return
    end if
    node.width = uiPosterSide(node, width)
    if node.hasField("scaleRotateCenter") then node.scaleRotateCenter = [node.width / 2.0, node.height / 2.0]
    if node.subtype() = "Label" then uiAlignRuns(node)
end sub

' Where a Label's lines sit in its width: "start", "center" or "end" by the reading direction (uiSetDirection), so a
' Latin line in Arabic starts on the right as the Arabic lines do.
sub uiSetTextAlign(node as object, align as string)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid or node.subtype() <> "Label" then return
    if not node.hasField("uiAlign") then node.addField("uiAlign", "string", false)
    node.uiAlign = align
    uiApplyAlign(node)
end sub

sub uiApplyAlign(label as object)
    align = "start"
    if label.hasField("uiAlign") then align = label.uiAlign
    ga = GetGlobalAA()
    rtl = ga.uiRtl <> invalid and ga.uiRtl
    if align = "center"
        label.horizAlign = "center"
    else if (align = "end" and not rtl) or (align <> "end" and rtl)
        label.horizAlign = "right"
    else
        label.horizAlign = "left"
    end if
    uiAlignRuns(label)
end sub

' Runs drawn by uiRichText, moved along to where the Label's alignment puts a line of their line's width in its own.
sub uiAlignRuns(label as object)
    count = label.getChildCount()
    if count = 0 or not label.hasField("uiRunsWidth") then return
    for i = 0 to count - 1
        child = label.getChild(i)
        if child.hasField("uiLineX")
            room = label.width - child.uiLineWidth
            offset = 0.0
            if room > 0.0 and label.horizAlign = "right" then offset = room
            if room > 0.0 and label.horizAlign = "center" then offset = room / 2.0
            x = offset + child.uiLineX
            if child.translation[0] <> x then child.translation = [x, child.translation[1]]
        end if
    end for
end sub

' The reading direction: every Label under the root starts again on the side it reads from.
sub uiSetDirection(rtl as boolean)
    GetGlobalAA().uiRtl = rtl
    uiRealign(uiRoot())
end sub

' Walked with a list rather than by recursion: a deep tree would run a Roku out of stack. A Label's own children are
' its runs, which follow it.
sub uiRealign(root as object)
    pending = [root]
    while pending.count() > 0
        node = pending.pop()
        if node <> invalid
            if node.subtype() = "Label"
                uiApplyAlign(node)
            else
                for i = 0 to node.getChildCount() - 1
                    pending.push(node.getChild(i))
                end for
            end if
        end if
    end while
end sub

sub uiSetHeight(node as object, height as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    if not node.hasField("height")
        if node.hasField("scaleRotateCenter") and not node.isSameNode(GetGlobalAA().appRoot)
            pivot = node.scaleRotateCenter
            node.scaleRotateCenter = [pivot[0], height / 2.0]
        end if
        return
    end if
    node.height = uiPosterSide(node, height)
    if node.hasField("scaleRotateCenter") then node.scaleRotateCenter = [node.width / 2.0, node.height / 2.0]
end sub

' A side as a node takes it. A Poster given 0 draws its bitmap at the bitmap's own size, where an image sized to nothing
' (a rating a title does not have) is meant to draw nothing: a sliver instead.
function uiPosterSide(node as object, side as float) as float
    if side <= 0 and node.subtype() = "Poster" then return 0.001
    return side
end function

sub uiSetBackgroundColor(node as object, hex as string)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    color = "0x00000000"
    if hex <> ""
        if Len(hex) = 9 then color = "0x" + UCase(Mid(hex, 2, 8)) else color = "0x" + UCase(Mid(hex, 2, 6)) + "FF"
    end if
    if node.subtype() = "Rectangle"
        node.color = color
    else if node.isSameNode(GetGlobalAA().appRoot)
        ' the render root is a Group: its background is the scene's full-screen "bg" rectangle (white when cleared)
        scene = node.getScene()
        if scene <> invalid
            bg = scene.findNode("bg")
            if bg <> invalid
                if hex = "" then bg.color = "0xFFFFFFFF" else bg.color = color
            end if
        end if
    end if
end sub

' A Poster's picture in one colour: its blend colour, which multiplies a white picture into that colour and keeps
' its transparency. "" draws it as it is.
sub uiSetTint(node as object, hex as string)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid or not node.hasField("blendColor") then return
    if hex = "" then node.blendColor = "0xFFFFFFFF" else node.blendColor = "0x" + UCase(Mid(hex, 2, 6)) + "FF"
end sub

sub uiSetImageSource(node as object, uri as string)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    if node.hasField("uri") then node.uri = uri
end sub

' Decode a picture at the size it is actually drawn, so the resampling is the DECODER's. Left to itself the
' renderer shrinks the full-size bitmap instead, and its scaler is the crude one: the wordmark on the loading
' screen is a 654 pixel image drawn at about 260 on a 720p panel, and came out stepped and harsh beside the
' splash, which the firmware blits at its own size and never scales at all.
sub uiSetImageSized(node as object, uri as string, width as float, height as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    if not node.hasField("uri") then return
    ' the size SceneGraph composes at, NOT the panel's: the scene is laid out in the 1920x1080 design space and the
    ' box scales the finished frame to the panel afterwards. Decoding at panel pixels handed the renderer a picture
    ' two thirds the size it needed and it enlarged it again, which turned every poster soft.
    w = Int(width + 0.5)
    h = Int(height + 0.5)
    sized = w >= 1 and h >= 1 and node.hasField("loadDisplayMode")
    if sized and (node.loadWidth <> w or node.loadHeight <> h)
        ' no larger than it is drawn, and never enlarged: a picture smaller than its box keeps its own pixels
        node.loadDisplayMode = "limitSize"
        node.loadWidth = w
        node.loadHeight = h
        ' the load size has to be in place BEFORE the uri, or the picture is already on its way at its own size
        if node.uri <> "" then node.uri = ""
        node.uri = uri
    else if node.uri <> uri
        node.uri = uri
    end if
end sub

sub uiRemove(node as object)
    if m.uiPerf <> invalid then m.uiPerf.removes = m.uiPerf.removes + 1
    if node = invalid then return
    p = node.getParent()
    if p <> invalid then p.removeChild(node)
end sub

' Load an image off-screen to learn its natural size, then call `loaded(width, height)`. SceneGraph reports it
' through the Poster's loadStatus field.
'
' The loads are throttled: at most uiImageConcurrency() measuring Posters load at once, the rest waiting in a queue
' that starts the next one as each in flight reaches ready or failed. A screen that builds many pictures in one frame
' (a cast montage fires a Poster apiece) otherwise saturates the loader, and the ones that lose the race time out and
' come back a zero size — an empty circle, for good, with nothing to draw. Spreading the burst over time, the way a
' scrolling rail does, is what keeps them from failing at all; a bounded re-queue on failure is the safety net for a
' genuinely transient one, and by going to the back of the queue it lands after the burst has drained rather than
' beating against a loader that is still busy.
'
' A picture in the package is read from the box itself, at once, and never waits in the queue: a weather scene's forty
' icons, queued with the rest, held a screensaver's cast faces and its next backdrop behind them long enough for that
' backdrop's wait to run out, and the rotation stood still on the cast with no faces in it.
sub uiLoadImage(uri as string, loaded as dynamic)
    ga = GetGlobalAA()
    if ga.uiImageQueue = invalid then ga.uiImageQueue = []
    job = { uri: uri, loaded: loaded, tries: 0, local: LCase(Left(uri, 4)) = "pkg:" }
    if job.local
        uiImageStart(job)
    else
        ga.uiImageQueue.Push(job)
        uiImagePump()
    end if
end sub

' At most this many measuring Posters loading over the network at once. Three kept a cast montage from saturating the
' loader, but left a screen that wanted twenty pictures seconds behind; six keeps the burst spread without the wait.
function uiImageConcurrency() as integer
    return 6
end function

' Start as many waiting loads as the concurrency limit allows.
sub uiImagePump()
    ga = GetGlobalAA()
    if ga.uiImageLoads = invalid then ga.uiImageLoads = {}
    if ga.uiImageActive = invalid then ga.uiImageActive = 0
    if ga.uiImageSeq = invalid then ga.uiImageSeq = 0
    while ga.uiImageActive < uiImageConcurrency() and ga.uiImageQueue.Count() > 0
        job = ga.uiImageQueue.Shift()
        ga.uiImageActive = ga.uiImageActive + 1
        uiImageStart(job)
    end while
end sub

' A measuring Poster for `job`, loading.
sub uiImageStart(job as object)
    ga = GetGlobalAA()
    if ga.uiImageLoads = invalid then ga.uiImageLoads = {}
    if ga.uiImageSeq = invalid then ga.uiImageSeq = 0
    ga.uiImageSeq = ga.uiImageSeq + 1
    p = uiNode("Poster")
    p.id = "uiImageLoad" + ga.uiImageSeq.ToStr()
    p.visible = false
    ' read no larger than the screen it is for: a photo from a library came at 2560 by 2590, forty megabytes of texture
    ' to learn a size, and the size it reports is the same shape at most the screen's
    p.loadDisplayMode = "limitSize"
    p.loadWidth = 1920
    p.loadHeight = 1080
    ga.uiImageLoads[p.id] = { poster: p, loaded: job.loaded, uri: job.uri, tries: job.tries, local: job.local }
    ga.appScene.appendChild(p)
    p.observeField("loadStatus", "uiOnImageLoadStatus")
    p.uri = job.uri
end sub

sub uiOnImageLoadStatus(event as object)
    status = event.getData()
    if status <> "ready" and status <> "failed" then return
    p = event.getRoSGNode()
    ga = GetGlobalAA()
    entry = ga.uiImageLoads[p.id]
    if entry = invalid then return
    ga.uiImageLoads.Delete(p.id)
    ga.appScene.removeChild(p)
    if not entry.local then ga.uiImageActive = ga.uiImageActive - 1
    ' a picture that would not load is said, with where it was, since what it was is otherwise lost with it
    if status = "failed" then print "[image] failed (try "; entry.tries; ") "; entry.uri
    ' a failure once the loader is no longer saturated is more likely a genuinely transient one: send it to the back
    ' of the queue to try again, a few times over, before giving up
    if status = "failed" and entry.tries < 4 and not entry.local
        ga.uiImageQueue.Push({ uri: entry.uri, loaded: entry.loaded, tries: entry.tries + 1, local: false })
        uiImagePump()
        return
    end if
    uiImagePump()
    ' bitmapWidth/Height come back scaled from the device's UI resolution to the channel's 1080p design space; report
    ' the image's own pixels, as a browser does.
    scale = CreateObject("roDeviceInfo").GetUIResolution().height / 1080.0
    RtGuard(entry.loaded, [p.bitmapWidth * scale, p.bitmapHeight * scale])
end sub

sub uiSetTranslation(node as object, x as float, y as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node <> invalid then node.translation = [x, y]
end sub

' tools/check.sh adds pkg:/freeze.txt to a test sideload: how many seconds of
' animation to step before freezing the frame. -1 (run live) when absent.
function uiFreezeSeconds() as float
    text = uiLaunchArg("freeze")
    if text = "" then return -1.0
    return Val(text)
end function

' What the channel was launched with — `/launch/dev?name=value` over ECP, a deep link's contentId — or else
' `pkg:/<name>.txt`, which a test sideload adds (Checks roku-install --add name.txt=value): Main gathers both. ""
' when neither has it. A launch that reaches the channel once it runs replaces the values it names (uiArrived).
function uiLaunchArg(name as string) as string
    params = GetGlobalAA().uiLaunchParams
    if params = invalid or not params.DoesExist(name) then return ""
    v = params[name]
    if type(v) = "roString" or type(v) = "String" then return v
    if v = invalid then return ""
    return v.ToStr()
end function

' Which of `ids` (channel ids, comma-separated) are installed on this Roku, comma-separated.
function uiAppsInstalled(ids as string) as string
    am = CreateObject("roAppManager")
    if am = invalid then return ""
    found = []
    for each id in ids.Split(",")
        if id <> "" and am.IsAppInstalled(id, "") then found.Push(id)
    end for
    return found.Join(",")
end function

' ── the channel's life: deep links, leaving and coming back ──

' `f(contentId, mediaType)` for each deep link: the one the channel was launched with (once, a turn after asking, so
' the app has finished starting), one sent to it while it runs (roInputEvent) and one it is resumed with.
sub uiOnDeepLink(f as dynamic)
    ga = GetGlobalAA()
    ga.uiDeepLink = f
    params = ga.uiLaunchParams
    if params <> invalid and params.contentId <> invalid and ga.uiLaunchLinked <> true
        ga.uiLaunchLinked = true
        uiLater({ fn: uiDeliverLaunchLink })
    end if
end sub

' Hands the launch's deep link over, from uiOnDeepLink's later turn.
function uiDeliverLaunchLink(_env as object, _args as object) as dynamic
    uiLinked(GetGlobalAA().uiLaunchParams)
    return invalid
end function

sub uiLinked(params as dynamic)
    ga = GetGlobalAA()
    if params = invalid or ga.uiDeepLink = invalid then return
    contentId = params.contentId
    if contentId = invalid or contentId = "" then return
    mediaType = params.mediaType
    if mediaType = invalid then mediaType = ""
    print "[launch] deep link "; contentId; " ("; mediaType; ")"
    RtGuard(ga.uiDeepLink, [contentId, mediaType])
end sub

' A deep link or a launch while the channel is up (the scene's inputArgs, from Main's roInputEvent).
sub uiLaunched(params as dynamic)
    uiArrived(params)
end sub

' A launch that reached the channel once it was running: an roInputEvent (a deep link to the running channel, which
' is how ECP's /launch reaches one), or the launch an Instant Resume brings it back with. It goes to the deep link
' hook where it is one, and to the launch hook (Platform.onLaunch) with what it says, its values as strings; its
' values replace those of the same name that uiLaunchArg reads. One with nothing of its own (a plain return from the
' home screen, which says only where it came from) leaves them as they are. The same launch heard twice in a few
' seconds, by both ways at once, is taken once.
sub uiArrived(params as dynamic)
    if type(params) <> "roAssociativeArray" then return
    ga = GetGlobalAA()
    own = {}
    own.SetModeCaseSensitive()
    said = ""
    for each name in params.Keys()
        if not uiLaunchHousekeeping(name)
            own[name] = _str(params[name])
            if said <> "" then said = said + "&"
            said = said + name + "=" + own[name]
        end if
    end for
    if said = "" then return
    now = UpTime(0)
    if ga.uiLastLaunchAt <> invalid and said = ga.uiLastLaunch and now - ga.uiLastLaunchAt < 3.0
        print "[launch] heard again, passed over: "; said
        return
    end if
    ga.uiLastLaunch = said
    ga.uiLastLaunchAt = now
    print "[launch] arrived: "; said
    if ga.uiLaunchParams = invalid then ga.uiLaunchParams = {}
    ga.uiLaunchParams.Append(own)
    if own.everyui_debug_motion <> invalid
        ga.uiMotionDebug = (own.everyui_debug_motion.Trim() = "1")
        ' (kept for the next cold start, which a sideload or a launch of a suspended channel cannot pass it to)
        section = CreateObject("roRegistrySection", "everyui")
        if ga.uiMotionDebug then section.Write("debug_motion", "1") else section.Delete("debug_motion")
        section.Flush()
    end if
    ' (rokuperf, httplog and everyui_perf turned on or off as they arrive: a resumed channel never starts again)
    uiDebugArrived(own)
    uiLinked(params)
    if ga.uiLaunchHook <> invalid then RtGuard(ga.uiLaunchHook, [own])
end sub

' What the device adds to a launch, a resume or an input event, saying where it came from (and from which address,
' over ECP) and how the last run ended, rather than anything the launch asks for.
function uiLaunchHousekeeping(name as string) as boolean
    n = LCase(name)
    return n = "source" or n = "reason" or n = "splashtime" or n = "lastexitorterminationreason" or n = "lastsuspendorresumereason" or n = "instant_on_run_mode" or n = "launchparams" or n = "source_ip_addr"
end function

' `f(params)` for each launch that reaches the channel once it is running (uiArrived).
sub uiOnLaunch(f as dynamic)
    GetGlobalAA().uiLaunchHook = f
end sub

' `resumed()` when the channel is in front again, and `away()` when it goes behind another or the device's
' screensaver (Instant Resume's suspend and resume handlers).
sub uiOnLifecycle(resumed as dynamic, away as dynamic)
    ga = GetGlobalAA()
    ga.uiResumeHook = resumed
    ga.uiAwayHook = away
end sub

' Each says why, where the box says (lastSuspendOrResumeReason), and how much memory the channel is using: a suspended
' channel over its background limit is closed by the box, and one coming back has been closed within seconds of it.
sub uiSuspended(args as dynamic)
    ga = GetGlobalAA()
    print "[launch] suspended ("; uiLifeReason(args); "), memory "; uiMemoryNote()
    if ga.uiAwayHook <> invalid then RtGuard(ga.uiAwayHook, [])
end sub

' The launch it is resumed with is Instant Resume's launchParams (or, on a box that does not nest them, the handler's
' own arguments, the device's own left out). The documents kept in cachefs: are listed again: the box may have cleared
' them while the channel was away, and a read of one the old list still had failed on the console.
sub uiResumed(args as dynamic)
    ga = GetGlobalAA()
    print "[launch] resumed ("; uiLifeReason(args); "), memory "; uiMemoryNote()
    ga.uiStoreFiles = invalid
    if ga.uiResumeHook <> invalid then RtGuard(ga.uiResumeHook, [])
    if type(args) = "roAssociativeArray"
        if type(args.launchParams) = "roAssociativeArray" then uiArrived(args.launchParams) else uiArrived(args)
    end if
    ' Instant Resume asks for this once the scene is back up; it is up already, never having been taken down
    ga.appScene.signalBeacon("AppResumeComplete")
end sub

' Why the box suspended or resumed the channel ("home", "deeplink", "screensaver"...), or "" where it does not say.
function uiLifeReason(args as dynamic) as string
    if type(args) <> "roAssociativeArray" then return ""
    reason = args.lastSuspendOrResumeReason
    if reason = invalid and type(args.launchParams) = "roAssociativeArray" then reason = args.launchParams.lastSuspendOrResumeReason
    if reason = invalid then return ""
    return _str(reason)
end function

' The channel's memory in a few words for the console: the share of its limit in use, what is left of it, and the
' device's own level (normal, low, critical).
function uiMemoryNote() as string
    pct = uiDeviceFact("memoryPct")
    avail = uiDeviceFact("appMemoryKb")
    level = uiDeviceFact("memoryLevel")
    said = ""
    if pct <> "" then said = pct + "% of the limit"
    if avail <> "" then said = said + ", " + Int(Val(avail) / 1024).ToStr() + " MB free"
    if level <> "" then said = said + ", device " + level
    if Left(said, 2) = ", " then said = Mid(said, 3)
    if said = "" then return "unknown"
    return said
end function

' The first screen is up and can be used: Roku's launch beacon, which certification requires and times the launch by.
' Sent once, whoever asks (Platform.launchComplete, or uiBeacon by its name).
sub uiLaunchComplete()
    ga = GetGlobalAA()
    if ga.uiLaunchBeaconed = true then return
    ga.uiLaunchBeaconed = true
    print "[beacon] AppLaunchComplete"
    ga.appScene.signalBeacon("AppLaunchComplete")
end sub

' One of the beacons certification reads, by name: AppDialogInitiate and AppDialogComplete around a screen shown before
' the first real one (a sign-in, a profile picker), whose time is then not counted as the launch's.
sub uiBeacon(name as string)
    if name = "AppLaunchComplete"
        uiLaunchComplete()
        return
    end if
    print "[beacon] "; name
    ga = GetGlobalAA()
    ga.appScene.signalBeacon(name)
end sub

' A packaged text file by its pkg:/ path (a translation), or "".
function uiReadText(path as string) as string
    return ReadAsciiFile(path)
end function

' An ARB file's messages as a string-keyed assoc array, which the runtime reads as a Scala Map: parsed natively, with
' its metadata (@key, @@locale) and anything that is not a string left out.
function uiArbMessages(text as string) as object
    messages = {}
    messages.SetModeCaseSensitive()
    if text = "" then return messages
    json = ParseJson(text)
    if type(json) <> "roAssociativeArray" then return messages
    for each key in json
        if Left(key, 1) <> "@"
            value = json[key]
            if GetInterface(value, "ifString") <> invalid then messages[key] = value
        end if
    end for
    return messages
end function

' The channel's saved settings: one registry section, kept across launches.
function uiSetting(name as string) as string
    section = CreateObject("roRegistrySection", "everyui")
    if section.Exists(name) then return section.Read(name)
    return ""
end function

sub uiSaveSetting(name as string, value as string)
    section = CreateObject("roRegistrySection", "everyui")
    if value = "" then section.Delete(name) else section.Write(name, value)
    section.Flush()
end sub

' Documents kept on the device between launches, in named spaces: what the app caches so a screen can be drawn
' before the server has answered. The registry above is the wrong home for this -- it holds a few small values and
' a cache of rails would not fit -- so these go to `cachefs:`, which survives a channel restart where `tmp:` does
' not. The box may clear it whenever it likes, so a read can always come back empty and NOTHING may depend on a
' document still being there: every caller must draw the same screen from the server when it is gone.
'
' One file per key, flat, rather than a directory per space: the Roku need only read the one document it is about
' to draw, instead of parsing a whole cache to find it. A key is expected to be a plain identifier (a rail id, say);
' anything outside [A-Za-z0-9.-] is folded to "_", so two keys differing only in punctuation would land in one file.
function uiStoreSafe(name as string) as string
    out = ""
    for i = 1 to Len(name)
        ch = Mid(name, i, 1)
        ok = (ch >= "a" and ch <= "z") or (ch >= "A" and ch <= "Z") or (ch >= "0" and ch <= "9") or ch = "." or ch = "-"
        if ok then out = out + ch else out = out + "_"
    end for
    return out
end function

function uiStorePrefix(space as string) as string
    return "everyui-" + uiStoreSafe(space) + "-"
end function

function uiStorePath(space as string, key as string) as string
    return "cachefs:/" + uiStorePrefix(space) + uiStoreSafe(key) + ".json"
end function

' "" for a document that is not there, which is also what an empty one reads as: a caller storing "" is storing
' nothing, and asking for it back gets nothing.
' Only a document the store holds is read: asking for one it does not (most of a cache's questions, the first time)
' failed a read on the thread that draws and said so on the console, ten times over as a page came up. The box can
' clear cachefs: behind the list's back, and a document found gone (or read back empty) leaves the list, so it fails
' one read, once, and is not asked for again. A channel back from Instant Resume lists them again (uiResumed): the box
' may clear cachefs: while a channel is away, and a page drawn on the way back failed a read for each document gone. It
' is not looked for first: roFileSystem is to be had only on the main and task threads, and on the thread that draws
' creating one fails and the next call on it throws.
function uiStoreGet(space as string, key as string) as string
    held = uiStoreHeld()
    name = uiStorePrefix(space) + uiStoreSafe(key) + ".json"
    if not held.DoesExist(name) then return ""
    text = ReadAsciiFile(uiStorePath(space, key))
    ' a missing file reads as invalid on some boxes and "" on others; both mean there is nothing kept here, and
    ' handing invalid up as though it were a document is how it reaches ParseJSON and takes the app down with it
    if text = invalid or text = ""
        held.Delete(name)
        return ""
    end if
    return text
end function

sub uiStorePut(space as string, key as string, value as string)
    ok = WriteAsciiFile(uiStorePath(space, key), value)
    held = uiStoreHeld()
    if ok then held[uiStorePrefix(space) + uiStoreSafe(key) + ".json"] = true
    if not ok then print "[store] WRITE FAILED "; key; " ("; Len(value); " bytes)"
end sub

sub uiStoreDelete(space as string, key as string)
    DeleteFile(uiStorePath(space, key))
    held = uiStoreHeld()
    held.Delete(uiStorePrefix(space) + uiStoreSafe(key) + ".json")
end sub

' The store's file names, listed once and kept up to date as documents are written and deleted.
function uiStoreHeld() as object
    ga = GetGlobalAA()
    if ga.uiStoreFiles = invalid
        files = {}
        files.SetModeCaseSensitive()
        for each name in ListDir("cachefs:/")
            files[name] = true
        end for
        ga.uiStoreFiles = files
    end if
    return ga.uiStoreFiles
end function

' The keys a space holds, as the file names say them -- so a key folded by uiStoreSafe comes back folded.
function uiStoreKeys(space as string) as object
    prefix = uiStorePrefix(space)
    found = CreateObject("roArray", 0, true)
    for each name in ListDir("cachefs:/")
        if Len(name) > Len(prefix) + 5 and Left(name, Len(prefix)) = prefix and Right(name, 5) = ".json"
            found.Push(Mid(name, Len(prefix) + 1, Len(name) - Len(prefix) - 5))
        end if
    end for
    return found
end function

sub uiStoreClear(space as string)
    prefix = uiStorePrefix(space)
    for each name in ListDir("cachefs:/")
        if Left(name, Len(prefix)) = prefix then DeleteFile("cachefs:/" + name)
    end for
    ga = GetGlobalAA()
    ga.uiStoreFiles = invalid
end sub

' The device's language as a BCP 47 tag ("en-US"; the Roku says "en_US").
function uiLanguage() as string
    return CreateObject("roDeviceInfo").GetCurrentLocale().Replace("_", "-")
end function

' This device's own IPv4 on its network, for a request to be answered back on the same network. First interface
' with an address; "" if the box is somehow offline.
function uiLocalAddress() as string
    addrs = CreateObject("roDeviceInfo").GetIPAddrs()
    if addrs <> invalid
        for each iface in addrs
            ip = addrs[iface]
            if ip <> invalid and ip <> "" then return ip
        end for
    end if
    return ""
end function

' A stable id for this box, the same one it answers with when asked over its control port, so a request meant
' for it can be told apart from one that reached a different box at the same address later.
function uiDeviceSerial() as string
    return CreateObject("roDeviceInfo").GetChannelClientId()
end function

' What the person calls this Roku ("Living Room Roku"), else its model's name.
function uiDeviceName() as string
    info = CreateObject("roDeviceInfo")
    name = info.GetFriendlyName()
    if name = invalid or name = "" then name = info.GetModelDisplayName()
    return name
end function

' One fact of the device and its memory, as text, for everyui.perf: "" where the box does not say (an older firmware
' without the call, or a simulator). The model is GetModel's number with its kind (TV or STB) and name: a Roku TV and
' an Ultra are told apart by it. Memory is read when asked, a window at a time, never per frame.
function uiDeviceFact(name as string) as string
    info = CreateObject("roDeviceInfo")
    if name = "model"
        model = ""
        if FindMemberFunction(info, "GetModel") <> invalid then model = info.GetModel()
        if FindMemberFunction(info, "GetModelType") <> invalid then model = model + " " + info.GetModelType()
        if FindMemberFunction(info, "GetModelDisplayName") <> invalid then model = model + " " + info.GetModelDisplayName()
        return model.Trim()
    else if name = "graphics"
        if FindMemberFunction(info, "GetGraphicsPlatform") <> invalid then return info.GetGraphicsPlatform()
    else if name = "memoryLevel"
        if FindMemberFunction(info, "GetGeneralMemoryLevel") <> invalid then return LCase(info.GetGeneralMemoryLevel())
    else if name = "refreshHz"
        ' the rate a video mode names after its "p" ("720p50", "1080p30"); the 60 Hz modes name none ("1080p"), and are 60
        if FindMemberFunction(info, "GetVideoMode") <> invalid
            mode = info.GetVideoMode()
            at = Instr(1, mode, "p")
            if at > 0
                rate = Int(Val(Mid(mode, at + 1)))
                if rate > 0 then return rate.ToStr()
                if Mid(mode, at + 1) = "" then return "60"
            end if
        end if
    else
        monitor = CreateObject("roAppMemoryMonitor")
        if monitor = invalid then return ""
        if name = "memoryPct" and FindMemberFunction(monitor, "GetMemoryLimitPercent") <> invalid
            return monitor.GetMemoryLimitPercent().ToStr()
        else if name = "appMemoryKb" and FindMemberFunction(monitor, "GetChannelAvailableMemory") <> invalid
            return monitor.GetChannelAvailableMemory().ToStr()
        else if name = "appLimitKb" and FindMemberFunction(monitor, "GetChannelMemoryLimit") <> invalid
            ' the foreground limit, of the limits a box with Instant Resume names (a simulator gives one number)
            limit = monitor.GetChannelMemoryLimit()
            if type(limit) = "roAssociativeArray" then limit = limit.maxForegroundMemory
            if limit <> invalid then return limit.ToStr()
        end if
    end if
    return ""
end function

' ── the network module (network/native): sockets and HTTP through Task nodes ──────
' The render thread cannot own an roStreamSocket or an roUrlTransfer, so each socket is an EveryUISocketTask node
' and each request goes to one of a few EveryUIHttpTask workers (roku/runtime/*Task.brs): their fields carry the
' traffic, and the callbacks run here, on the render thread, like every other facade.
function uiSocketOpen(url as string, onOpen as dynamic, onText as dynamic, onClose as dynamic) as object
    ga = GetGlobalAA()
    if ga.uiSockets = invalid
        ga.uiSockets = {}
        ga.uiSocketSeq = 0
    end if
    ga.uiSocketSeq = ga.uiSocketSeq + 1
    t = uiNode("EveryUISocketTask")
    t.id = "uiSocket" + ga.uiSocketSeq.ToStr()
    ga.uiSockets[t.id] = { onOpen: onOpen, onText: onText, onClose: onClose, node: t }
    t.observeField("socketEvent", "uiOnSocketEvent")
    t.url = url
    t.control = "RUN"
    return t
end function

sub uiOnSocketEvent(event as object)
    ga = GetGlobalAA()
    t = event.getRoSGNode()
    h = ga.uiSockets[t.id]
    if h = invalid then return
    ev = event.getData()
    if ev.kind = "open"
        RtGuard(h.onOpen, [])
    else if ev.kind = "text"
        RtGuard(h.onText, [ev.data])
    else if ev.kind = "close"
        ga.uiSockets.Delete(t.id)
        RtGuard(h.onClose, [ev.code, ev.reason])
    end if
end sub

sub uiSocketSend(t as object, text as string)
    t.outbox = text
end sub

sub uiSocketClose(t as object)
    t.command = "close"
end sub

' Requests wait in a queue for one of a few EveryUIHttpTask workers (uiHttpWorkers), which keep their connections.
sub uiHttp(method as string, url as string, headersJson as string, body as string, hasBody as boolean, done as dynamic)
    headers = ParseJson(headersJson)
    if headers = invalid then headers = {}
    uiHttpEnqueue({ url: url, method: method, headers: headers, body: body, hasBody: hasBody }, done)
end sub

' The same with a body of bytes, or an answer wanted as bytes (`binary`): the bytes cross to the worker and back as hex,
' which a field carries where it cannot carry an roByteArray, and the worker sends and takes them through files, so they
' come through whole where a string would stop at a NUL. `done` hears (status, headersJson, text, bytes).
sub uiHttpBytes(method as string, url as string, headersJson as string, body as object, hasBody as boolean, binary as boolean, done as dynamic)
    headers = ParseJson(headersJson)
    if headers = invalid then headers = {}
    request = { url: url, method: method, headers: headers, body: "", hasBody: hasBody, bytes: true, binary: binary }
    if hasBody
        ba = CreateObject("roByteArray")
        for each b in body
            ba.Push(b and 255)
        end for
        request.bodyHex = ba.ToHexString()
    end if
    uiHttpEnqueue(request, done)
end sub

' An roByteArray as a Scala Array[Byte] is kept: numbers from -128 to 127, read by index (not every engine walks a byte
' array with for each as a device does).
function uiSignedBytes(ba as object) as object
    out = []
    last = ba.Count() - 1
    for i = 0 to last
        b = ba[i]
        if b > 127 then out.Push(b - 256) else out.Push(b)
    end for
    return out
end function

' Queue `request` for a worker, `done` to hear its answer; the workers are started with the first.
sub uiHttpEnqueue(request as object, done as dynamic)
    ga = GetGlobalAA()
    if ga.uiHttps = invalid
        ga.uiHttps = {}
        ga.uiHttpSeq = 0
        ga.uiHttpQueue = []
        ga.uiHttpIdle = []
        ga.uiHttpPool = []
        if ga.uiHttpLog = invalid then uiDebugFlags()
        for i = 1 to uiHttpWorkers()
            t = uiNode("EveryUIHttpTask")
            t.observeField("result", "uiOnHttpResult")
            t.observeField("ready", "uiOnHttpReady")
            t.control = "RUN"
            ga.uiHttpPool.Push(t)
        end for
    end if
    ga.uiHttpSeq = ga.uiHttpSeq + 1
    request.id = "uiHttp" + ga.uiHttpSeq.ToStr()
    request.asked = uiClockMs()
    ga.uiHttps[request.id] = done
    ga.uiHttpQueue.Push(request)
    uiHttpNext()
end sub

function uiHttpWorkers() as integer
    ' a screen opened asks for half a dozen things or more at once (a title's page: nine), and five, one held most of a
    ' minute at a time by an app's long poll, answered them in waves of four, each wave waiting for the one before;
    ' each worker keeps its connection, so more of them is more connections kept open, not more handshakes per call
    return 8
end function

' Hand the oldest waiting request to an idle worker, if both there are.
sub uiHttpNext()
    ga = GetGlobalAA()
    if ga.uiHttpQueue.Count() = 0 or ga.uiHttpIdle.Count() = 0 then return
    t = ga.uiHttpIdle.Pop()
    t.request = ga.uiHttpQueue.Shift()
end sub

' A worker is only idle once it is listening: a request set before then would never be seen.
sub uiOnHttpReady(event as object)
    ga = GetGlobalAA()
    ga.uiHttpIdle.Push(event.getRoSGNode())
    uiHttpNext()
end sub

' Where a request's time went, with rokuperf=1 or httplog=1: waiting for a worker, on the wire (the worker's own, its
' connection made or kept, the request and the answer), and back to the render thread, which waits for whatever the
' render thread is doing first (a screen being built).
'
'   [http] uiHttp12 200 POST https://api.example.com/x: 540 ms = queued 3, wire 520, back 17
sub uiHttpSaid(r as object)
    if r.began = invalid or r.ended = invalid then return
    now = uiClockMs()
    asked = r.began
    if r.asked <> invalid then asked = r.asked
    ' one string, built up and printed whole, as uiPerfFrame's (the device's compiler refuses a long print list)
    said = "[http] " + _str(r.id) + " " + _str(r.status) + " " + _str(r.method) + " " + Left(_str(r.url), 100)
    said = said + ": " + Int(now - asked).ToStr() + " ms = queued " + Int(r.began - asked).ToStr()
    said = said + ", wire " + Int(r.ended - r.began).ToStr() + ", back " + Int(now - r.ended).ToStr()
    print said
end sub

' The time of day in milliseconds, which every thread reads alike (UpTime, a Float, loses its milliseconds once the box
' has been up a while).
function uiClockMs() as double
    d = CreateObject("roDateTime")
    return d.AsSeconds() * 1000# + d.GetMilliseconds()
end function

sub uiOnHttpResult(event as object)
    ga = GetGlobalAA()
    ga.uiHttpIdle.Push(event.getRoSGNode())
    r = event.getData()
    if ga.uiHttpLog = true then uiHttpSaid(r)
    done = ga.uiHttps[r.id]
    ga.uiHttps.Delete(r.id)
    uiHttpNext()
    if done = invalid then return
    if r.bytes <> invalid
        ' the bytes back from hex, as a Scala Array[Byte] is kept: numbers from -128 to 127
        bytes = []
        if r.hex <> invalid
            ba = CreateObject("roByteArray")
            ba.FromHexString(r.hex)
            bytes = uiSignedBytes(ba)
        end if
        RtGuard(done, [r.status, r.headers, r.body, bytes])
    else
        RtGuard(done, [r.status, r.headers, r.body])
    end if
end sub

' A SceneGraph Rectangle has no corner radius.
sub uiSetRadius(_target as object, _radius as float)
end sub

' A shape behind a node's children, tinted: a rounded fill or edge (a white 9-patch in images/rr, made by the channel
' build), created the first time at `index` among the children, then updated. "#rrggbb" or "#rrggbbaa".
function uiShape(parent as object, existing as dynamic, uri as string, hex as string, width as float, height as float, index as integer) as object
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    p = existing
    if p = invalid
        p = uiNode("Poster")
        parent.insertChild(p, index)
    end if
    if p.uri <> uri then p.uri = uri
    alpha = 255
    if Len(hex) = 9 then alpha = Val(Mid(hex, 8, 2), 16)
    p.blendColor = "0x" + UCase(Mid(hex, 2, 6)) + "FF"
    p.opacity = alpha / 255.0
    p.width = width
    p.height = height
    return p
end function

' Milliseconds since the epoch as local "yyyy-MM-dd HH:mm:ss".
function uiLocalDateTime(millis as dynamic) as string
    dt = CreateObject("roDateTime")
    ' whole seconds by integer division: Int() takes a Float, whose steps at today's date are two minutes long
    dt.FromSeconds(millis \ 1000)
    dt.ToLocalTime()
    return Right("0" + dt.GetYear().ToStr(), 4) + "-" + Right("0" + dt.GetMonth().ToStr(), 2) + "-" + Right("0" + dt.GetDayOfMonth().ToStr(), 2) + " " + Right("0" + dt.GetHours().ToStr(), 2) + ":" + Right("0" + dt.GetMinutes().ToStr(), 2) + ":" + Right("0" + dt.GetSeconds().ToStr(), 2)
end function

' The time zone the device is set to, by its own name for it ("US/Central").
function uiTimeZone() as string
    return CreateObject("roDeviceInfo").GetTimeZone()
end function

' A Roku has no pointer: nothing ever fires.
sub uiOnPointer(_target as object, _kind as string, _f as dynamic)
end sub

' The key hook (everyui.focus.Focus); the generated runtime's RtKeyEvent feeds it from AppScene's onKeyEvent. One set
' while the keyboard is up (uiEditText) is the one put back as it closes.
sub uiOnKey(f as dynamic)
    ga = GetGlobalAA()
    if f <> invalid then f = { fn: uiKeyHeard, inner: f }
    if ga.uiEditing <> invalid then ga.uiEditKey = f else ga.uiKey = f
end sub

' ── the keyboard: Platform.editText ──

' Roku's own keyboard over the app: a StandardKeyboardDialog on the scene's dialog field, which brings voice entry with
' it where the remote has a microphone, headed `title`, holding `value`, its keyboard `domain` ("generic", "email",
' "password", "numeric", "alphanumeric"), hiding what is typed when `secure`. Its first button (`ok`) hands the words
' to `done`; its second (`cancel`), Back, and another asked for while it is up hand nothing. The app's focus stays where
' it was (on the field), and the app hears no key while the dialog is up: what is pressed there is the dialog's.
sub uiEditText(title as string, value as string, secure as boolean, domain as string, ok as string, cancel as string, done as dynamic)
    ga = GetGlobalAA()
    if ga.uiEditing <> invalid then uiEditClose(false)
    dialog = uiNode("StandardKeyboardDialog")
    dialog.title = title
    dialog.buttons = [ok, cancel]
    if dialog.hasField("keyboardDomain") then dialog.keyboardDomain = domain
    editBox = dialog.textEditBox
    if editBox <> invalid
        ' (a TextEditBox keeps 15 characters unless told otherwise, which an email address is often longer than)
        editBox.maxTextLength = 255
        editBox.secureMode = secure
    end if
    if dialog.hasField("secureMode") then dialog.secureMode = secure
    if dialog.hasField("text")
        dialog.text = value
    else if editBox <> invalid
        editBox.text = value
    end if
    if editBox <> invalid and editBox.hasField("cursorPosition") then editBox.cursorPosition = Len(value)
    dialog.observeField("buttonSelected", "uiOnEditButton")
    dialog.observeField("wasClosed", "uiOnEditClosed")
    ga.uiEditing = { dialog: dialog, done: done }
    ' the app's key hook set aside while the dialog has the keys: a key the dialog does not take is not passed on to it
    ga.uiEditKey = ga.uiKey
    ga.uiKey = { fn: uiEditSwallow }
    print "[keyboard] open: domain "; domain; ", secure "; secure; ", "; Len(value); " characters, '"; title; "'"
    ga.appScene.dialog = dialog
end sub

' A key while the keyboard is up: taken, so none reaches the app or (Back) closes the channel.
function uiEditSwallow(_env as object, _args as object) as dynamic
    return true
end function

' The dialog's first button is Done, the second Cancel.
sub uiOnEditButton(event as object)
    ga = GetGlobalAA()
    if ga.uiEditing = invalid or not ga.uiEditing.dialog.isSameNode(event.getRoSGNode()) then return
    uiEditClose(event.getData() = 0)
end sub

' Closed by Back (or the options key), never by a button: nothing is handed back.
sub uiOnEditClosed(event as object)
    ga = GetGlobalAA()
    if ga.uiEditing = invalid or not ga.uiEditing.dialog.isSameNode(event.getRoSGNode()) then return
    uiEditClose(false)
end sub

' The keyboard put away, the words handed to `done` where `taken`. The app's key hook is put back with the release of
' the OK that opened the dialog, which went to the dialog: the focus layer counts a press held until it is let go, and
' would take the next OK on the field for a repeat of it. The scene has the remote's keys again.
sub uiEditClose(taken as boolean)
    ga = GetGlobalAA()
    editing = ga.uiEditing
    if editing = invalid then return
    ga.uiEditing = invalid
    dialog = editing.dialog
    words = ""
    if dialog.hasField("text")
        words = dialog.text
    else if dialog.textEditBox <> invalid
        words = dialog.textEditBox.text
    end if
    if words = invalid then words = ""
    dialog.unobserveField("buttonSelected")
    dialog.unobserveField("wasClosed")
    if dialog.wasClosed <> true then dialog.close = true
    scene = ga.appScene
    if scene.dialog <> invalid and scene.dialog.isSameNode(dialog) then scene.dialog = invalid
    scene.setFocus(true)
    ga.uiKey = ga.uiEditKey
    ga.uiEditKey = invalid
    if taken then print "[keyboard] done: "; Len(words); " characters" else print "[keyboard] cancelled"
    if ga.uiKey <> invalid then RtGuard(ga.uiKey, ["Enter", false])
    if taken then RtGuard(editing.done, [words])
end sub

' ── the account's details: AccountDetails ──

' The details of the person's Roku account named in `fields` ("email", or "email,firstname,lastname"), asked through a
' ChannelStore node's getUserData (Request for Information, which certification asks of a channel that signs people
' in): Roku shows its own dialog naming them, and `done` is handed what the person shared, a table of the fields asked
' for that have a value, or invalid where they declined or shared nothing. `signIn` asks as for signing in
' (requestedUserDataInfo's context "signin", where Roku offers the email address and phone alone); otherwise as for
' signing up. One request at a time: another asked for while one waits answers the first invalid. The node is kept
' (with `done`) until it answers, and let go then. A sideloaded channel launched with fakestore=1 asks the store's fake
' server, which answers at once with made-up details and no dialog, to try the flow.
sub uiUserData(fields as string, signIn as boolean, done as dynamic)
    ga = GetGlobalAA()
    if ga.uiUserAsk <> invalid then uiUserDataEnd(invalid)
    store = uiNode("ChannelStore")
    if uiFlagSaid("fakestore", false) then store.fakeServer = true
    store.requestedUserData = fields
    if signIn
        info = uiNode("ContentNode")
        info.addFields({ context: "signin" })
        store.requestedUserDataInfo = info
    end if
    store.observeField("userData", "uiOnUserData")
    ga.uiUserAsk = { store: store, done: done, fields: fields }
    said = "[account] asking for " + fields
    if signIn then said = said + " (sign-in)"
    print said
    store.command = "getUserData"
end sub

' The store's answer: a ContentNode of the fields shared, or invalid where the person declined.
sub uiOnUserData(event as object)
    ga = GetGlobalAA()
    asked = ga.uiUserAsk
    if asked = invalid or not asked.store.isSameNode(event.getRoSGNode()) then return
    data = event.getData()
    shared = invalid
    kind = type(data)
    if kind = "roSGNode" or kind = "roAssociativeArray"
        shared = {}
        shared.SetModeCaseSensitive()
        for each named in asked.fields.Split(",")
            name = named.Trim()
            value = invalid
            if kind = "roSGNode"
                if name <> "" and data.hasField(name) then value = data.getField(name)
            else if name <> ""
                value = data.Lookup(name)
            end if
            if value <> invalid and GetInterface(value, "ifString") <> invalid
                if value <> "" then shared[name] = value
            end if
        end for
        if shared.Count() = 0 then shared = invalid
    end if
    uiUserDataEnd(shared)
end sub

' The request ended: the node let go and `done` handed `shared` (a table, or invalid).
sub uiUserDataEnd(shared as dynamic)
    ga = GetGlobalAA()
    asked = ga.uiUserAsk
    if asked = invalid then return
    ga.uiUserAsk = invalid
    asked.store.unobserveField("userData")
    if shared = invalid
        print "[account] nothing shared"
    else
        print "[account] shared " + shared.Count().ToStr() + " of " + asked.fields
    end if
    RtGuard(asked.done, [shared])
end sub

' Clip a Group to width by height (a scroll viewport).
sub uiSetClip(node as object, width as float, height as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    node.clippingRect = [0, 0, width, height]
end sub

' A linear gradient behind a node's children, which SceneGraph cannot draw: a strip of it, 256 pixels along the
' gradient and one across, drawn on the device the first time it is asked for and stretched over the editBox. `angle` is
' CSS's (0 bottom to top, 90 left to right), taken to the nearest side; `stops` is "#rrggbbaa at,#rrggbbaa at".
function uiGradient(parent as object, existing as dynamic, angle as float, stops as string, width as float, height as float) as object
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    p = existing
    if p = invalid
        p = uiNode("Poster")
        p.loadDisplayMode = "scaleToFill"
        parent.insertChild(p, 0)
    end if
    a = angle mod 360
    if a < 0 then a = a + 360
    side = Int((a + 45) / 90) mod 4
    uri = uiGradientStrip(stops, side)
    if p.uri <> uri then p.uri = uri
    p.width = width
    p.height = height
    return p
end function

' The strip for `stops` running toward `side` (0 up, 1 right, 2 down, 3 left), as a PNG in tmp:/, kept by its key.
function uiGradientStrip(stops as string, side as integer) as string
    ga = GetGlobalAA()
    if ga.uiGradients = invalid then ga.uiGradients = {}
    key = side.ToStr() + ":" + stops
    if ga.uiGradients[key] <> invalid then return ga.uiGradients[key]
    colors = []
    ats = []
    for each entry in stops.Split(",")
        parts = entry.Trim().Split(" ")
        hex = Mid(parts[0], 2)
        if Len(hex) = 6 then hex = hex + "FF"
        colors.push([Val(Mid(hex, 1, 2), 16), Val(Mid(hex, 3, 2), 16), Val(Mid(hex, 5, 2), 16), Val(Mid(hex, 7, 2), 16)])
        at = 0.0
        if parts.count() > 1 then at = Val(parts[1])
        ats.push(at)
    end for
    if colors.count() = 0 then return ""
    n = 256
    horizontal = side = 1 or side = 3
    w = 1 : h = n
    if horizontal then w = n : h = 1
    b = CreateObject("roBitmap", { width: w, height: h, AlphaEnable: false })
    if b = invalid then return ""
    for i = 0 to n - 1
        t = (i + 0.5) / n
        ' along the gradient: from the left for "right", from the top for "down", reversed for the other two
        if side = 0 or side = 3 then t = 1 - t
        c = colors[colors.count() - 1]
        if t <= ats[0] then c = colors[0]
        for k = 0 to colors.count() - 2
            if t > ats[k] and t <= ats[k + 1]
                span = ats[k + 1] - ats[k]
                f = 0.0
                if span > 0 then f = (t - ats[k]) / span
                c = []
                for ch = 0 to 3
                    c.push(Int(colors[k][ch] + (colors[k + 1][ch] - colors[k][ch]) * f + 0.5))
                end for
            end if
        end for
        ' &hRRGGBBAA as the signed 32-bit Integer it is, so a red of 128 or more does not overflow on the way
        red = c[0]
        if red >= 128 then red = red - 256
        rgba = red * 16777216 + c[1] * 65536 + c[2] * 256 + c[3]
        if horizontal then b.DrawRect(i, 0, 1, 1, rgba) else b.DrawRect(0, i, 1, 1, rgba)
    end for
    b.Finish()
    png = b.GetPng(0, 0, w, h)
    if png = invalid then return ""
    path = "tmp:/everyui-gradient-" + ga.uiGradients.Count().ToStr() + ".png"
    if not png.WriteFile(path) then return ""
    ga.uiGradients[key] = path
    return path
end function

' Cut a node's children to a rounded rectangle, which a SceneGraph clip cannot: the children are moved into a
' MaskGroup (made once, `existing` after) whose mask is a white rounded rectangle of exactly this size. A mask is
' stretched to its maskSize, never 9-patched, so it is drawn for each size, in panel pixels as maskSize is, and kept.
function uiRoundClip(node as object, existing as dynamic, width as float, height as float, radius as float) as object
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    group = existing
    if group = invalid
        group = uiNode("MaskGroup")
        kids = []
        for i = 0 to node.getChildCount() - 1
            kids.push(node.getChild(i))
        end for
        for each kid in kids
            group.appendChild(kid)
        end for
        node.appendChild(group)
    end if
    ps = uiPanelScale()
    w = Int(width * ps + 0.5)
    h = Int(height * ps + 0.5)
    r = Int(radius * ps + 0.5)
    if w < 1 or h < 1 then return group
    ' a round one (a face) is cut by the package's disc, stretched to it: one drawn here came out empty
    if w = h and r * 2 >= w - 1
        uri = "pkg:/images/rr/circle.png"
    else
        uri = uiRoundMask(w, h, r)
    end if
    if uri <> "" and group.maskUri <> uri
        group.maskUri = uri
        group.maskSize = [w, h]
    end if
    return group
end function

' A white rounded rectangle, w by h with corners of radius r, as a PNG in tmp:/ (made the first time it is asked for).
' The corners are antialiased: each pixel is as opaque as the share of it inside the circle.
function uiRoundMask(w as integer, h as integer, r as integer) as string
    ga = GetGlobalAA()
    if ga.uiMasks = invalid then ga.uiMasks = {}
    key = w.ToStr() + "x" + h.ToStr() + "r" + r.ToStr()
    if ga.uiMasks[key] <> invalid then return ga.uiMasks[key]
    path = "tmp:/everyui-mask-" + key + ".png"
    if r > w / 2 then r = Int(w / 2)
    if r > h / 2 then r = Int(h / 2)
    b = CreateObject("roBitmap", { width: w, height: h, AlphaEnable: false })
    if b = invalid then return ""
    b.Clear(&h00000000)
    b.DrawRect(r, 0, w - 2 * r, h, &hFFFFFFFF)
    b.DrawRect(0, r, w, h - 2 * r, &hFFFFFFFF)
    for y = 0 to r - 1
        for x = 0 to r - 1
            dx = r - x - 0.5
            dy = r - y - 0.5
            a = r - Sqr(dx * dx + dy * dy) + 0.5
            if a > 0
                if a > 1 then a = 1
                c = &hFFFFFF00 + Int(a * 255)
                b.DrawRect(x, y, 1, 1, c)
                b.DrawRect(w - 1 - x, y, 1, 1, c)
                b.DrawRect(x, h - 1 - y, 1, 1, c)
                b.DrawRect(w - 1 - x, h - 1 - y, 1, 1, c)
            end if
        end for
    end for
    b.Finish()
    png = b.GetPng(0, 0, w, h)
    if png = invalid then return ""
    if not png.WriteFile(path) then return ""
    ga.uiMasks[key] = path
    return path
end function

' The pixels between a Label's lines, rather than the theme's.
sub uiSetLineSpacing(node as object, spacing as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid or not node.hasField("lineSpacing") then return
    node.lineSpacing = spacing
end sub

' A Label wraps its words at its width (and draws every line), or keeps one line and cuts it short.
sub uiSetWrap(node as object, wrap as boolean)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid or not node.hasField("wrap") then return
    node.wrap = wrap
    if wrap then node.maxLines = 0
end sub

' Whether a node and everything under it is drawn: what a scroll has out of sight costs a Roku nothing.
sub uiSetVisible(node as object, visible as boolean)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    node.visible = visible
end sub

sub uiSetOpacity(node as object, opacity as float)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node = invalid then return
    node.opacity = opacity
end sub

' The character at a Unicode code point, as a string.
function uiChr(codePoint as integer) as string
    return Chr(codePoint)
end function

' A field of `node` moving through keyframes, over and over (Track, everyui.motion): an Animation node with one
' interpolator, a child of the node itself. Positions and scales are pairs (`a`, `b`); a rotation is already
' SceneGraph's way round.
function uiMotion(node as object, property as string, seconds as float, delay as float, fractions as object, a as object, b as object, owner as dynamic) as object
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if node.id = "" then node.id = "uiMotion" + RtNextId().ToStr()
    anim = uiNode("Animation")
    anim.duration = seconds
    anim.delay = delay
    anim.repeat = true
    anim.easeFunction = "linear"
    values = []
    if property = "position" or property = "scale"
        interp = uiNode("Vector2DFieldInterpolator")
        for i = 0 to a.Count() - 1
            values.Push([a[i], b[i]])
        end for
        field = "scale"
        if property = "position" then field = "translation"
    else
        interp = uiNode("FloatFieldInterpolator")
        for each value in a
            values.Push(value)
        end for
        field = "opacity"
        if property = "rotation" then field = "rotation"
    end if
    interp.key = fractions
    interp.keyValue = values
    interp.fieldToInterp = node.id + "." + field
    anim.appendChild(interp)
    node.appendChild(anim)
    anim.id = "uiAnim" + RtNextId().ToStr()
    ' (started at the end of the turn, if its node is on the scene then: see uiMotionsSettle)
    motions = uiMotions()
    motions[anim.id] = { anim: anim, node: node, field: field, owner: owner, running: false, started: false }
    if uiMotionDebug() then uiMotionLog("made", motions[anim.id])
    return anim
end function

' A motion taken away: paused, never stopped, before it goes. Stopping an Animation has its interpolators set their
' field once more, and by then the node is often off the scene or leaving it (a spinner taken away as what it waited
' for arrives, and disposed of in the same turn), which is "Could not find node uiMotion…" again.
sub uiStopMotion(anim as object)
    if m.uiPerf <> invalid then m.uiPerf.writes = m.uiPerf.writes + 1
    if anim = invalid then return
    motions = uiMotions()
    e = motions[anim.id]
    if e <> invalid and uiMotionDebug() then uiMotionLog("taken away", e)
    if e <> invalid and e.running then anim.control = "pause"
    motions.Delete(anim.id)
    uiRemove(anim)
end sub

' The motions (uiMotion), by their Animation's id. SceneGraph finds a motion's node through the scene, so a motion runs
' only while its node is on it: one whose node is off it logs "Could not find node uiMotion…" as it runs. A node joins
' and leaves the scene in many ways in one turn (built off it and added, moved under a parent not yet added, taken
' away), so the motions are settled when the turn is over, before the scene is drawn: at the end of every handler the
' platform calls (RtAfterTurn, from RtGuard and the scene's own).
function uiMotions() as object
    ga = GetGlobalAA()
    if ga.uiMotions = invalid
        ga.uiMotions = {}
        ga.rtAfterTurn = { fn: uiTurnEnded }
    end if
    return ga.uiMotions
end function
' Whether a node is in the app's tree on the scene: under the root group (appRoot), which is. Not whether its topmost
' ancestor is the scene: a node made in the scene's script and put under nothing answers getParent() with the scene
' itself (seen on Roku OS 15.3), so a spinner still being built, or a subtree not yet added, looked on the scene while
' the scene could not find it.
function uiOnScene(node as object) as boolean
    appRoot = GetGlobalAA().appRoot
    at = node
    while at <> invalid
        if at.isSameNode(appRoot) then return true
        if at.isSubtype("Scene") then return false
        at = at.getParent()
    end while
    return false
end function
' Each motion running if its node is on the scene, and paused (or not yet started) if it is not.
' The end of a turn (RtAfterTurn), before the scene is drawn: what was asked to be painted then (uiPaintSoon), and the
' motions started or paused as their nodes came on or went off the scene.
sub uiTurnEnded(_env as dynamic)
    ga = GetGlobalAA()
    paint = ga.uiPaint
    if paint <> invalid
        ga.uiPaint = invalid
        try
            RtInvoke(paint, [])
        catch e
            RtReportError(e)
        end try
    end if
    if ga.uiMotions <> invalid then uiMotionsSettle(ga.uiMotions)
end sub

' `f` at the end of the turn under way (uiTurnEnded), once.
sub uiPaintSoon(f as dynamic)
    ga = GetGlobalAA()
    ga.uiPaint = f
    ga.rtAfterTurn = { fn: uiTurnEnded }
end sub

sub uiMotionsSettle(motions as object)
    for each id in motions
        e = motions[id]
        shown = uiOnScene(e.node)
        if shown and not e.running
            if e.started then e.anim.control = "resume" else e.anim.control = "start"
            e.started = true
            e.running = true
            if uiMotionDebug() then uiMotionLog("started", e)
        else if e.running and not shown
            e.anim.control = "pause"
            e.running = false
            if uiMotionDebug() then uiMotionLog("paused", e)
        end if
        ' (what its interpolator will look for: a running motion whose node the scene cannot find is the error itself)
        if e.running and uiMotionDebug()
            ga = GetGlobalAA()
            if ga.appScene <> invalid and ga.appScene.findNode(e.node.id) = invalid then uiMotionLog("NOT FOUND from the scene", e)
        end if
    end for
end sub

' everyui_debug_motion=1 (a launch or deep-link parameter: ECP /launch/dev?everyui_debug_motion=1): every motion made,
' started, paused and taken away is told on the console with its target's id, the field, what it is for (the component
' and those it sits in), whether the node is on the scene, and the node's path up to the scene.
' (on from a cold start: a launch parameter, a file everyui_debug_motion.txt in the channel holding 1, which Main
' reads as one and emitRoku writes under -Deveryui.debugMotion=true, or the registry, where a deep link leaves it)
function uiMotionDebug() as boolean
    ga = GetGlobalAA()
    if ga.uiMotionDebug = invalid
        said = invalid
        params = ga.uiLaunchParams
        if params <> invalid and params.everyui_debug_motion <> invalid then said = params.everyui_debug_motion
        if said = invalid
            section = CreateObject("roRegistrySection", "everyui")
            said = section.Read("debug_motion")
        end if
        text = _str(said)
        ga.uiMotionDebug = (text.Trim() = "1")
    end if
    return ga.uiMotionDebug
end function
sub uiMotionLog(what as string, e as object)
    owner = ""
    if e.owner <> invalid then owner = _str(RtInvoke(e.owner, []))
    print "[motion] "; what; " "; e.anim.id; " target="; e.node.id; " field="; e.field; " onScene="; uiOnScene(e.node); " running="; e.running; " owner="; owner
    print "[motion]   path="; uiNodePath(e.node)
end sub
' A node and those it sits in, up to the root: subtype#id each.
function uiNodePath(node as object) as string
    parts = []
    at = node
    while at <> invalid
        name = at.subtype()
        if at.id <> "" then name = name + "#" + at.id
        parts.Push(name)
        at = at.getParent()
    end while
    return parts.Join(" < ")
end function

' ---- media: a Video node (a video) or an Audio node (sound) ----
' Each carries what it was last asked (uiPlaying, uiLoop) so a source set later still starts and loops as
' asked, and an id its state listener is found by. SceneGraph's Video letterboxes: it has no cover mode.

function uiVideo() as object
    n = uiNode("Video")
    n.enableUI = false
    n.translation = [0.0, 0.0]
    uiMediaFields(n)
    return n
end function

function uiAudio() as object
    n = uiNode("Audio")
    uiMediaFields(n)
    return n
end function

sub uiMediaFields(n as object)
    ga = GetGlobalAA()
    if ga.uiMediaSeq = invalid then ga.uiMediaSeq = 0
    ga.uiMediaSeq = ga.uiMediaSeq + 1
    n.id = "uiMedia" + ga.uiMediaSeq.ToStr()
    n.addFields({ uiAskedAt: 0.0, uiPlaying: true, uiLoop: false, uiReady: false, uiHandoff: false, uiStart: 0.0, uiHeaders: [], uiSidecars: [], uiAudioLanguage: "", uiTextLanguage: "" })
end sub

sub uiSetMediaSource(node as object, url as string)
    if node = invalid then return
    print "[video] "; node.id; " source "; Left(url, 120)
    at = uiTicks()
    ' a player with nothing in it has nothing to stop, and a stop is done before the render thread goes on
    if node.content <> invalid then node.control = "stop"
    node.uiReady = false
    if url = "" then return
    c = uiNode("ContentNode")
    c.url = url
    ' where it starts, said with the content so the stream opens there rather than seeking after it; once
    if node.uiStart > 0 then c.PlayStart = Int(node.uiStart)
    node.uiStart = 0.0
    format = uiStreamFormat(url)
    if format <> "" then c.streamFormat = format
    ' the headers go with every request the player makes for it, an HLS playlist's segments and keys as much as the
    ' playlist (a Jellyfin or Audiobookshelf stream that takes its token no other way)
    if node.uiHeaders.Count() > 0 then c.HttpHeaders = node.uiHeaders
    ' and subtitle files that are not inside it, offered beside its own tracks
    if node.uiSidecars.Count() > 0 then c.SubtitleTracks = node.uiSidecars
    node.content = c
    node.loop = node.uiLoop
    if node.uiPlaying then node.control = "play"
    ' when it was asked for, for how long it takes to play (uiOnMediaState), and how long asking took the render thread
    node.uiAskedAt = uiTicks()
    print "[video] "; node.id; " asked to play in "; Int(node.uiAskedAt - at); " ms"
end sub

' Where the next source starts, in seconds.
sub uiSetMediaStart(node as object, seconds as float)
    if node <> invalid then node.uiStart = seconds
end sub

sub uiSetMediaPlaying(node as object, playing as boolean)
    if node = invalid then return
    node.uiPlaying = playing
    if node.content = invalid then return
    if playing
        if node.state = "paused"
            node.control = "resume"
        else if node.state <> "playing" and node.state <> "buffering"
            node.control = "play"
        end if
    else if node.state = "playing" or node.state = "buffering"
        ' an Audio node stops rather than pausing, its place kept: a paused one may still hold the player another needs
        if node.subtype() = "Audio" then uiMediaYield(node) else node.control = "pause"
    end if
end sub

sub uiSetMediaLoop(node as object, loop as boolean)
    if node = invalid then return
    node.uiLoop = loop
    node.loop = loop
end sub

sub uiSetMediaMuted(node as object, muted as boolean)
    if node <> invalid and node.hasField("mute") then node.mute = muted
end sub

function uiGetMediaPosition(node as object) as float
    if node = invalid then return 0.0
    return node.position
end function

' How long the content runs. A Video node has it once the stream is open; before that it is invalid or 0, which
' means "not known yet" rather than "empty".
function uiGetMediaDuration(node as object) as float
    if node = invalid then return 0.0
    d = node.duration
    if d = invalid then return 0.0
    return d
end function

sub uiSeekMedia(node as object, seconds as float)
    if node <> invalid then node.seek = seconds
end sub

' `f` hears "ready" (first playing), "ended" and "error".
sub uiOnMediaEvent(node as object, f as dynamic)
    if node = invalid then return
    ga = GetGlobalAA()
    if ga.uiMediaListeners = invalid then ga.uiMediaListeners = {}
    ga.uiMediaListeners[node.id] = f
    node.observeField("state", "uiOnMediaState")
end sub

sub uiOnMediaState(event as object)
    state = event.getData()
    n = event.getRoSGNode()
    ' what a player does is otherwise invisible, and a picture of the screen shows no video to tell by
    if state = "playing" and not n.uiReady and n.uiAskedAt > 0
        said = ""
        if n.hasField("timeToStartStreaming") then said = " (the device: " + _str(n.timeToStartStreaming) + " s)"
        print "[video] "; n.id; " playing "; Int(uiTicks() - n.uiAskedAt); " ms after it was asked"; said
    else
        print "[video] "; n.id; " "; state
    end if
    if state = "error" then print "[video] "; n.id; " failed: "; n.errorCode; " "; n.errorMsg; " "; n.errorStr
    if state = "playing" then n.uiHandoff = false
    ' refused the player just handed to it, the one before not yet let go of it: asked again, once, a moment later
    if state = "error" and n.uiHandoff and n.errorCode = -5
        n.uiHandoff = false
        print "[video] "; n.id; " asks again for the player it was handed"
        uiMediaAgain(n)
        return
    end if
    if state = "error" and n.subtype() = "Audio"
        RtReportError({ _cls: "AudioError", message: "audio: " + n.id + " failed: " + _str(n.errorCode) + " " + _str(n.errorMsg) + " (" + uiMediaUrl(n) + ")" })
    end if
    ga = GetGlobalAA()
    if ga.uiMediaListeners = invalid then return
    f = ga.uiMediaListeners[n.id]
    if f = invalid then return
    if state = "playing" and not n.uiReady
        n.uiReady = true
        RtGuard(f, ["ready"])
    else if state = "finished"
        RtGuard(f, ["ended"])
    else if state = "error"
        RtGuard(f, ["error"])
    end if
end sub

sub uiReleaseMedia(node as object)
    if node = invalid then return
    ' stopped without holding the render thread while it does (Roku OS 12.5): the node is not used again
    if node.content <> invalid
        if node.hasField("asyncStopSemantics") then node.asyncStopSemantics = true
        node.control = "stop"
    end if
    node.unobserveField("state")
    ga = GetGlobalAA()
    if ga.uiMediaListeners <> invalid then ga.uiMediaListeners.Delete(node.id)
end sub

' ---- turns at the player: SceneGraph plays one Audio node at a time, and a second one asked to play fails ("player:
' only one playing instance supported"), so they take turns (everyui.audio.PlayerStack, in the Scala) ----

' The player handed to another: stopped, where it had reached kept as where it starts again, its listener told nothing.
sub uiMediaYield(node as object)
    if node = invalid or node.content = invalid then return
    at = node.position
    node.control = "stop"
    if at <> invalid and at > 0 then node.content.PlayStart = Int(at)
    print "[video] "; node.id; " stopped at "; at; " s, its place kept"
end sub

' The player back: on from where it stopped, if it is still to play.
sub uiMediaReclaim(node as object)
    if node = invalid or node.content = invalid or not node.uiPlaying then return
    node.uiHandoff = true
    node.control = "play"
end sub

' About to play with the player just taken from another, which may not have let it go yet.
sub uiMediaHandoff(node as object)
    if node <> invalid then node.uiHandoff = true
end sub

' Play again in a moment, if it is still to.
sub uiMediaAgain(node as object)
    t = uiNode("Timer")
    t.repeat = false
    t.duration = 0.25
    t.addField("uiTarget", "node", false)
    t.uiTarget = node
    t.observeField("fire", "uiOnMediaAgain")
    scene = GetGlobalAA().appScene
    scene.appendChild(t)
    t.control = "start"
end sub

sub uiOnMediaAgain(event as object)
    t = event.getRoSGNode()
    n = t.uiTarget
    scene = GetGlobalAA().appScene
    scene.removeChild(t)
    if n <> invalid and n.uiPlaying and n.content <> invalid then n.control = "play"
end sub

' What it plays, the address as given.
function uiMediaUrl(node as object) as string
    if node = invalid or node.content = invalid then return ""
    return _str(node.content.url)
end function

' Its id, for the log.
function uiMediaId(node as object) as string
    if node = invalid then return ""
    return node.id
end function

' ---- sound effects: SceneGraph's SoundEffect node, which mixes with whatever an Audio or a Video node plays ----
' It plays a WAV on the device (pkg:/, tmp:/, cachefs:/) and nothing else, at a volume of 0 to 100 that is a share of
' the device's menu volume, and only so many at once (its state says "toomanysounds" of one past them). Each source's
' node is kept, loaded, for the next play, the uiEffectsKept used last at most.

function uiEffectsKept() as integer
    return 8
end function

function uiEffect(uri as string) as object
    ga = GetGlobalAA()
    if ga.uiEffects = invalid then ga.uiEffects = { nodes: {}, order: [] }
    fx = ga.uiEffects
    n = fx.nodes[uri]
    order = []
    for each u in fx.order
        if u <> uri then order.Push(u)
    end for
    order.Push(uri)
    if n = invalid
        n = uiNode("SoundEffect")
        n.addFields({ uiPending: false, uiVolume: 100 })
        n.observeField("loadStatus", "uiOnEffectLoad")
        n.observeField("state", "uiOnEffectState")
        n.uri = uri
        ga.appScene.appendChild(n)
        fx.nodes[uri] = n
        ' the one used longest ago let go, unless it is sounding
        if order.Count() > uiEffectsKept()
            old = fx.nodes[order[0]]
            if old <> invalid and old.state <> "playing"
                fx.nodes.Delete(order[0])
                ga.appScene.removeChild(old)
                order.Shift()
            end if
        end if
    end if
    fx.order = order
    return n
end function

sub uiLoadEffect(uri as string)
    if uri <> "" then uiEffect(uri)
end sub

sub uiPlayEffect(uri as string, volume as float)
    if uri = "" then return
    n = uiEffect(uri)
    v = Int(volume * 100 + 0.5)
    if v < 0 then v = 0
    if v > 100 then v = 100
    n.volume = v
    print "[video] effect "; uri; " "; n.loadStatus; ", volume "; v
    if n.loadStatus = "ready"
        n.control = "play"
    else
        n.uiPending = true
    end if
end sub

sub uiStopEffect(uri as string)
    ga = GetGlobalAA()
    if uri = "" or ga.uiEffects = invalid then return
    n = ga.uiEffects.nodes[uri]
    if n = invalid then return
    n.uiPending = false
    if n.state = "playing" then n.control = "stop"
end sub

sub uiOnEffectLoad(event as object)
    n = event.getRoSGNode()
    status = event.getData()
    if status = "ready" and n.uiPending
        n.uiPending = false
        n.control = "play"
    else if status = "failed"
        n.uiPending = false
        RtReportError({ _cls: "AudioError", message: "audio: effect " + n.uri + " failed to load" })
    end if
end sub

sub uiOnEffectState(event as object)
    n = event.getRoSGNode()
    if event.getData() = "toomanysounds" then print "[video] effect "; n.uri; " not played: too many sounding at once"
end sub

' An error reported as one nothing caught is (RtReportError: "[error] …", and the app's error hook, which counts it),
' the first time `key` is.
sub uiReportOnce(key as string, message as string)
    ga = GetGlobalAA()
    if ga.uiReported = invalid then ga.uiReported = {}
    if ga.uiReported.DoesExist(key) then return
    ga.uiReported[key] = true
    RtReportError({ _cls: "AudioError", message: message })
end sub

' ── which build this is ──

' The build's number as the app's other platforms count it (the manifest's app_version_code, which the build writes),
' or 0 where the manifest has none.
function uiAppVersion() as integer
    code = CreateObject("roAppInfo").GetValue("app_version_code")
    if code = invalid or code = "" then return 0
    return code.ToInt()
end function

' The release, as the manifest has it (major.minor.build).
function uiAppVersionName() as string
    return CreateObject("roAppInfo").GetVersion()
end function

' "dev" for a sideloaded channel, "roku" for one the channel store installed.
function uiInstallStore() as string
    if CreateObject("roAppInfo").IsDev() then return "dev"
    return "roku"
end function

' ── emoji, which a Roku has no face for ──
' Its own font draws a star and a dot and nothing else of the emoji an app writes (a date's calendar, a streak's fire, a
' country's flag), each as an empty editBox. What has a Material icon of the same meaning is drawn as that icon, in the
' text's colour (uiEmojiIcon); the rest is left out, with the space it leaves (uiEmojiDropped). Platform.emoji says so,
' for an app to put a word where the emoji carried one (a country's code for its flag).

' The Material Icons glyph an emoji is drawn as, or 0.
function uiEmojiIcon(c as integer) as integer
    ga = GetGlobalAA()
    if ga.uiEmojiIcons = invalid
        m = {}
        m["128197"] = &hE878 ' calendar: event
        m["128198"] = &hE878
        m["128172"] = &hE0B7 ' speech balloon: chat
        m["127942"] = &hEA23 ' trophy: emoji_events
        m["128293"] = &hEF55 ' fire: local_fire_department
        m["9889"] = &hEA0B ' high voltage: bolt
        m["10052"] = &hEB3B ' snowflake: ac_unit
        m["127769"] = &hE51C ' crescent moon: dark_mode
        m["127937"] = &hF06E ' chequered flag: sports_score
        m["128200"] = &hE8E5 ' chart increasing: trending_up
        m["127922"] = &hEB40 ' die: casino
        m["127919"] = &hE1B3 ' direct hit: gps_fixed
        m["128337"] = &hE8B5 ' clock face: schedule
        ga.uiEmojiIcons = m
    end if
    icon = ga.uiEmojiIcons[c.ToStr()]
    if icon = invalid then return 0
    return icon
end function

' Whether `c` is an emoji, or a part of one (a variation selector, a joiner, a skin tone, a flag's letters), with no
' glyph here.
function uiEmojiDropped(c as integer) as boolean
    if c >= &h1F000 and c <= &h1FAFF then return true
    if c >= &h2600 and c <= &h27BF then return true
    if c >= &h2B00 and c <= &h2BFF then return true
    if c = &hFE0F or c = &h200D or c = &h20E3 then return true
    return c >= &hE0020 and c <= &hE007F
end function

' `text` with each emoji drawn as its icon, apart from the words beside it, or left out; a space the emoji leaves
' twice over is one, and none is left at either end.
function uiEmojiText(text as string) as string
    out = ""
    n = Len(text)
    for i = 1 to n
        ch = Mid(text, i, 1)
        c = Asc(ch)
        icon = 0
        dropped = false
        if c >= &h2000 and not ((c >= &h2600 and c <= &h2603) or c = &h2605 or c = &h2606 or (c >= &h2660 and c <= &h266F) or c = &h2713)
            icon = uiEmojiIcon(c)
            if icon = 0 then dropped = uiEmojiDropped(c)
        end if
        if icon <> 0
            if out <> "" and Right(out, 1) <> " " then out = out + " "
            out = out + Chr(icon) + " "
        else if dropped
            ' nothing
        else if ch = " " and (out = "" or Right(out, 1) = " ")
            ' a space the emoji left behind
        else
            out = out + ch
        end if
    end for
    return out.Trim()
end function

' ── what a player is told before its source, and what it says once playing ──

' What a stream is, from its address: an HLS playlist wherever its path or query says so (Jellyfin's master.m3u8,
' Audiobookshelf's /hls/, a transcoder's format=hls), DASH, or a file by its extension; "" leaves it to the player.
function uiStreamFormat(url as string) as string
    lower = LCase(url)
    q = Instr(1, lower, "?")
    path = lower
    if q > 0 then path = Left(lower, q - 1)
    if Instr(1, lower, ".m3u8") > 0 or Instr(1, path, "/hls/") > 0 or Instr(1, lower, "format=hls") > 0 or Instr(1, lower, "protocol=hls") > 0 or Instr(1, lower, "container=m3u8") > 0 then return "hls"
    if Instr(1, lower, ".mpd") > 0 or Instr(1, lower, "format=dash") > 0 then return "dash"
    if Right(path, 4) = ".mp3" then return "mp3"
    if Right(path, 4) = ".mp4" or Right(path, 4) = ".m4v" or Right(path, 4) = ".mov" then return "mp4"
    if Right(path, 4) = ".m4a" or Right(path, 4) = ".m4b" then return "mp4"
    if Right(path, 4) = ".mkv" then return "mkv"
    if Right(path, 5) = ".flac" then return "flac"
    return ""
end function

' The headers for the next source, as "Name:Value" lines.
sub uiSetMediaHeaders(node as object, headers as object)
    if node <> invalid then node.uiHeaders = headers
end sub

' Subtitle files for the next source: each [url, label, language].
sub uiSetMediaSidecars(node as object, sidecars as object)
    if node = invalid then return
    tracks = []
    for each s in sidecars
        tracks.push({ TrackName: s[0], Description: s[1], Language: s[2] })
    end for
    node.uiSidecars = tracks
end sub

' The languages to hear and read it in, chosen from its tracks once it is playing (uiMediaLanguages); "" for its own.
sub uiSetMediaLanguages(node as object, audio as string, text as string)
    if node = invalid then return
    node.uiAudioLanguage = audio
    node.uiTextLanguage = text
end sub

' Its tracks, each "kind|id|language|label": the audio a Video node lists, and its subtitles.
function uiMediaTracks(node as object) as object
    out = []
    if node = invalid or not node.hasField("availableAudioTracks") then return out
    audio = node.availableAudioTracks
    if audio <> invalid
        for each t in audio
            out.push("audio|" + uiStrOf(t.Track) + "|" + uiStrOf(t.Language) + "|" + uiStrOf(t.Name))
        end for
    end if
    text = node.availableSubtitleTracks
    if text <> invalid
        for each t in text
            out.push("text|" + uiStrOf(t.TrackName) + "|" + uiStrOf(t.Language) + "|" + uiStrOf(t.Description))
        end for
    end if
    return out
end function

function uiStrOf(v as dynamic) as string
    if v = invalid then return ""
    if type(v) = "roString" or type(v) = "String" then return v.Replace("|", "/")
    return v.ToStr()
end function

' A track to hear or read by its id; text "" shows none (uiCaptionsChosen).
sub uiSelectMediaTrack(node as object, kind as string, id as string)
    if node = invalid or not node.hasField("availableAudioTracks") then return
    if kind = "audio"
        if id <> "" then node.audioTrack = id
    else
        uiCaptionsChosen(node, id)
    end if
end sub

' ── captions: the device's setting, and a video's by it ──
' A Roku shows captions by its caption mode, the device's own setting (Settings, Accessibility, Captions mode, and the
' options menu during a video), which outlives the video and the channel: a Video node's globalCaptionMode and
' roDeviceInfo.SetCaptionsMode both write it. As Roku's own caption guidance and its certification (4.8) have it, a
' channel's caption choice is that setting: a track the app chose writes it On, none chosen writes it Off, and with
' nothing chosen it is left as the viewer set it. Main hears the viewer change it (roDeviceInfoEvent
' isCaptionModeChanged) and tells the scene.

' The device's caption mode: "On", "Off", "Instant replay" or "When mute" (a Roku TV's).
function uiCaptionsMode() as string
    mode = CreateObject("roDeviceInfo").GetCaptionsMode()
    if mode = invalid then return ""
    return mode
end function

' The device's preferred caption language, two letters, or "" where the box does not say.
function uiCaptionsLanguage() as string
    info = CreateObject("roDeviceInfo")
    if FindMemberFunction(info, "GetPreferredCaptionLanguage") = invalid then return ""
    language = info.GetPreferredCaptionLanguage()
    if language = invalid then return ""
    return language
end function

' The device's caption appearance (its Accessibility > Captions style settings, GetCaptionsOption), as uiCaptionStyleOf
' reads it; empty where the box does not say.
function uiCaptionStyle() as object
    info = CreateObject("roDeviceInfo")
    if FindMemberFunction(info, "GetCaptionsOption") = invalid then return []
    said = {}
    for each name in ["Text/Color", "Text/Opacity", "Background/Color", "Background/Opacity", "Window/Color", "Window/Opacity", "Text/Effect", "Text/Size"]
        said[name] = info.GetCaptionsOption(name)
    end for
    return uiCaptionStyleOf(said)
end function

' Caption settings by their names ("Text/Color": "Red"…) as fourteen numbers: the words' red, green, blue and alpha
' (0 to 255), the background's, the window's, the edge (0 none, 1 outline, 2 a drop shadow, 3 raised, 4 depressed) and
' the size as a multiple of the usual (Medium's). Empty where every one is Default, which is the video's own look; where
' some are set, the rest are Roku's defaults: its white words, opaque, on black, opaque, no window, no edge, Medium.
' The colours and sizes are those the device draws (as brs-engine has them): its "White" a light grey, "Bright white"
' white.
function uiCaptionStyleOf(settings as object) as object
    chosen = false
    said = {}
    for each name in settings
        v = settings[name]
        if v = invalid then v = ""
        v = LCase(_str(v).Trim())
        if v = "" then v = "default"
        if v <> "default" then chosen = true
        said[name] = v
    end for
    if not chosen then return []
    style = []
    style.Append(uiCaptionColour(said["Text/Color"], "white", said["Text/Opacity"], "100%"))
    style.Append(uiCaptionColour(said["Background/Color"], "black", said["Background/Opacity"], "100%"))
    style.Append(uiCaptionColour(said["Window/Color"], "black", said["Window/Opacity"], "off"))
    effect = said["Text/Effect"]
    edge = 0
    if effect = "uniform" then edge = 1
    if Left(effect, 11) = "drop shadow" then edge = 2
    if effect = "raised" then edge = 3
    if effect = "depressed" then edge = 4
    style.Push(edge * 1.0)
    sizes = { "extra small": 20, "small": 21.5, "medium": 29, "large": 37.5, "extra large": 40.5 }
    size = sizes[said["Text/Size"]]
    if size = invalid then size = 29
    style.Push(size / 29.0)
    return style
end function

' A colour Roku names ("White", "Red"…; Default is `otherwise`) at an opacity it names ("75%", "Off"; Default is
' `usual`), as red, green, blue and alpha.
function uiCaptionColour(name as dynamic, otherwise as string, opacity as dynamic, usual as string) as object
    colours = { "bright white": [255, 255, 255], "white": [176, 176, 176], "black": [0, 0, 0], "red": [202, 0, 0], "green": [0, 187, 0], "blue": [3, 4, 208], "yellow": [178, 180, 0], "magenta": [197, 0, 197], "cyan": [0, 187, 185] }
    rgb = invalid
    if name <> invalid then rgb = colours[name]
    if rgb = invalid then rgb = colours[otherwise]
    if opacity = invalid or opacity = "default" then opacity = usual
    alpha = 255.0
    if opacity = "off"
        alpha = 0.0
    else
        percent = Val(opacity.Replace("%", ""))
        if percent >= 0 and percent <= 100 then alpha = Int(percent * 255.0 / 100.0 + 0.5) * 1.0
    end if
    return [rgb[0] * 1.0, rgb[1] * 1.0, rgb[2] * 1.0, alpha]
end function

' `changed()` each time the viewer changes the mode, until uiCaptionsUnlisten(the id answered).
function uiCaptionsListen(changed as dynamic) as integer
    ga = GetGlobalAA()
    if ga.uiCaptionHooks = invalid
        ga.uiCaptionHooks = {}
        ga.uiCaptionHookNext = 0
    end if
    if ga.uiCaptionLast = invalid then ga.uiCaptionLast = uiCaptionsMode()
    ga.uiCaptionHookNext = ga.uiCaptionHookNext + 1
    ga.uiCaptionHooks["h" + ga.uiCaptionHookNext.ToStr()] = changed
    return ga.uiCaptionHookNext
end function

sub uiCaptionsUnlisten(id as integer)
    ga = GetGlobalAA()
    if ga.uiCaptionHooks <> invalid then ga.uiCaptionHooks.Delete("h" + id.ToStr())
end sub

' The scene heard from Main that the mode changed. A change the channel made itself (uiCaptionsWrite) is not passed
' on; one the viewer made is heard.
sub uiCaptionsChanged()
    ga = GetGlobalAA()
    mode = uiCaptionsMode()
    if ga.uiCaptionLast <> invalid and ga.uiCaptionLast = mode then return
    print "[captions] device mode now "; mode
    ga.uiCaptionLast = mode
    if ga.uiCaptionWrote <> invalid and ga.uiCaptionWrote = mode
        ga.uiCaptionWrote = invalid
        return
    end if
    ga.uiCaptionWrote = invalid
    uiCaptionsHeard()
end sub

' Each listener told the mode changed.
sub uiCaptionsHeard()
    ga = GetGlobalAA()
    if ga.uiCaptionHooks = invalid then return
    hooks = []
    for each k in ga.uiCaptionHooks
        hooks.push(ga.uiCaptionHooks[k])
    end for
    for each hook in hooks
        RtGuard(hook, [])
    end for
end sub

' The device's mode written by the channel for a video's captions, remembered so its own change is not taken for the
' viewer's.
sub uiCaptionsWrite(node as object, mode as string)
    ga = GetGlobalAA()
    if ga.uiCaptionLast = invalid then ga.uiCaptionLast = uiCaptionsMode()
    if uiCaptionsMode() = mode then return
    print "[captions] device mode "; uiCaptionsMode(); " written "; mode
    ga.uiCaptionWrote = mode
    if node <> invalid and node.hasField("globalCaptionMode")
        node.globalCaptionMode = mode
    else
        info = CreateObject("roDeviceInfo")
        info.SetCaptionsMode(mode)
    end if
end sub

' The device's mode written as the viewer would write it in the options menu ("On", "Off", "Instant replay", "When
' mute"), for an app's own caption menu: every listener hears it at once, as the viewer's. False where the box refused.
function uiCaptionsSetMode(mode as string) as boolean
    ga = GetGlobalAA()
    written = CreateObject("roDeviceInfo").SetCaptionsMode(mode)
    print "[captions] device mode set "; mode; " by the app: "; written
    if written <> true then return false
    ga.uiCaptionLast = mode
    ga.uiCaptionWrote = invalid
    uiCaptionsHeard()
    return true
end function

' A video's captions as the app chose them: the track `id` shown and the device's mode written On, or "" for none and
' the mode written Off.
sub uiCaptionsChosen(node as object, id as string)
    if node = invalid or not node.hasField("globalCaptionMode") then return
    print "[captions] "; node.id; " chosen '"; id; "', device mode "; uiCaptionsMode(); ", language '"; uiCaptionsLanguage(); "'"
    if node.hasField("suppressCaptions") then node.suppressCaptions = false
    if id = ""
        uiCaptionsWrite(node, "Off")
    else
        node.subtitleTrack = id
        uiCaptionsWrite(node, "On")
    end if
end sub

' A video's captions as the device has them: its mode as it is (On replay and When mute are the Video node's own to
' follow), reading `id` where a track is known ("" leaves the choice to the box, by its caption language).
sub uiCaptionsFollow(node as object, id as string)
    if node = invalid or not node.hasField("globalCaptionMode") then return
    print "[captions] "; node.id; " the device's, mode "; uiCaptionsMode(); ", language '"; uiCaptionsLanguage(); "', track '"; id; "'"
    if node.hasField("suppressCaptions") then node.suppressCaptions = false
    if id <> "" then node.subtitleTrack = id
end sub

' Whether it is waiting for more of the stream rather than playing.
function uiMediaBuffering(node as object) as boolean
    if node = invalid then return false
    return node.state = "buffering"
end function

' What the link was measured carrying when the stream was chosen, in bits a second; 0 where it has not said.
function uiMediaBandwidth(node as object) as dynamic
    if node = invalid or not node.hasField("streamInfo") then return 0
    info = node.streamInfo
    if info = invalid then return 0
    b = info.measuredBitrate
    if b = invalid then return 0
    return b
end function

' The picture's size as the segment playing has it: [width, height], or [0, 0].
function uiMediaSize(node as object) as object
    if node = invalid or not node.hasField("streamingSegment") then return [0.0, 0.0]
    seg = node.streamingSegment
    if seg = invalid or seg.width = invalid or seg.height = invalid then return [0.0, 0.0]
    return [seg.width * 1.0, seg.height * 1.0]
end function

' ── errors nothing caught ──

' `f(message, stack)` for every error a handler the platform calls throws (RtGuard, in the Scala runtime).
sub uiOnError(f as dynamic)
    GetGlobalAA().uiErrorHook = f
end sub


' ── the cycle collector ──

' Run BrightScript's cycle collector two seconds after the last call: a screen or a scene let go refers to itself and is
' freed only by it, and a run costs half a second or more, all of it a frame the render thread does not draw, so it
' waits for things to go quiet: two seconds after the last call, and then for as long as the remote is in use, until two
' seconds have passed with no key. How long each run took is said.
sub uiCollect()
    ga = GetGlobalAA()
    if ga.uiCollector = invalid
        t = uiNode("Timer")
        t.repeat = false
        t.duration = 2
        t.observeField("fire", "uiOnCollect")
        ga.appScene.appendChild(t)
        ga.uiCollector = t
    end if
    ga.uiCollector.control = "stop"
    ga.uiCollector.control = "start"
end sub

sub uiOnCollect()
    ga = GetGlobalAA()
    if ga.uiKeyAt <> invalid and uiTicks() - ga.uiKeyAt < 2000
        ga.uiCollector.control = "start"
        return
    end if
    at = uiTicks()
    freed = RunGarbageCollector()
    if freed <> invalid and freed.ORPHANED <> invalid then print "[gc] freed "; freed.ORPHANED; " of "; freed.COUNT; " in "; Int(uiTicks() - at); " ms"
end sub

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

' A SceneGraph Label, sized for 1080p and visible against the generated
' scene's light background.
function uiText() as object
    n = CreateObject("roSGNode", "Label")
    n.color = "0x1C1B1FFF"
    f = CreateObject("roSGNode", "Font")
    f.uri = "font:BoldSystemFontFile"
    f.size = 48
    n.font = f
    n.translation = [0.0, 0.0]
    return n
end function

function uiGetText(node as object) as string
    if node = invalid then return ""
    return node.text
end function

sub uiSetText(node as object, text as string)
    if node <> invalid then node.text = text
end sub

function uiGetX(node as object) as float
    if node = invalid then return 0.0
    t = node.translation
    if t = invalid then return 0.0
    return t[0]
end function

sub uiSetX(node as object, x as float)
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
    n = CreateObject("roSGNode", "Poster")
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
    if node <> invalid then node.rotation = -radians
end sub

sub uiSetFontSize(node as object, size as float)
    if node = invalid then return
    f = CreateObject("roSGNode", "Font")
    f.uri = "font:BoldSystemFontFile"
    f.size = Int(size)
    node.font = f
end sub

' The design resolution declared in the manifest (ui_resolutions=fhd), which is
' the coordinate space SceneGraph lays out in regardless of the panel's own
' resolution.
function uiScreenWidth() as float
    return 1920.0
end function

function uiScreenHeight() as float
    return 1080.0
end function

sub uiAppend(parent as object, child as object)
    if parent <> invalid and child <> invalid then parent.appendChild(child)
end sub

sub uiLog(message as dynamic)
    print "[ui] "; message
end sub

' Milliseconds since the first call — the animation clock. roTimespan is the
' only sub-second clock Roku exposes.
function uiTicks() as float
    ga = GetGlobalAA()
    if ga.uiClock = invalid then ga.uiClock = CreateObject("roTimespan")
    return ga.uiClock.TotalMilliseconds()
end function

' Drive `tick` (a defunctionalised Scala closure) once per render frame. A
' SceneGraph Timer with duration 0 fires on every frame the renderer draws,
' which is what makes this a real frame-rate measurement rather than a
' setInterval count.
sub uiAnimate(tick as dynamic)
    ga = GetGlobalAA()
    ga.uiTick = tick
    t = CreateObject("roSGNode", "Timer")
    t.repeat = true
    t.duration = 0.0
    ga.appScene.appendChild(t)
    ga.uiTimer = t
    t.observeField("fire", "uiOnFrame")
    t.control = "start"
end sub

sub uiOnFrame()
    ga = GetGlobalAA()
    if ga.uiTick <> invalid then RtInvoke(ga.uiTick, [])
end sub

' Math intrinsics for Platform.sqrt / pow / sin / cos.
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
    probe = CreateObject("roSGNode", "Label")
    probe.text = "Hamburgefonstiv Example 0123"
    big = CreateObject("roSGNode", "Font")
    big.uri = uri
    big.size = Int(size * 4.0 + 0.5)
    probe.font = big
    target = probe.boundingRect().width / 4.0
    best = Int(size + 0.5)
    bestErr = 1.0e9
    for candidate = Int(size + 0.5) to Int(size * 1.25 + 1.0)
        f = CreateObject("roSGNode", "Font")
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

sub uiSetFont(node as object, family as string, weight as integer, size as float)
    if node = invalid then return
    f = CreateObject("roSGNode", "Font")
    f.uri = uiFontUri(family, weight)
    f.size = uiCalibratedSize(f.uri, size)
    node.font = f
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
    if node = invalid then return 0.0
    ga = GetGlobalAA()
    if ga.uiMeasureLabel = invalid
        ga.uiMeasureLabel = CreateObject("roSGNode", "Label")
        ga.uiMeasureFonts = {}
    end if
    big = Int(size * 32.0 + 0.5)
    key = uri + "@" + big.ToStr()
    f = ga.uiMeasureFonts[key]
    if f = invalid
        f = CreateObject("roSGNode", "Font")
        f.uri = uri
        f.size = big
        ga.uiMeasureFonts[key] = f
    end if
    probe = ga.uiMeasureLabel
    probe.font = f
    probe.text = node.text
    return probe.boundingRect().width * size / big
end function

' A container: a Rectangle is a Group that can also paint a background.
function uiContainer() as object
    n = CreateObject("roSGNode", "Rectangle")
    n.color = "0x00000000"
    n.width = 0
    n.height = 0
    n.translation = [0.0, 0.0]
    return n
end function

' Posters keep rotating about their centre as their size changes.
sub uiSetWidth(node as object, width as float)
    if node = invalid then return
    if not node.hasField("width") then return
    node.width = width
    if node.subtype() = "Poster" then node.scaleRotateCenter = [node.width / 2.0, node.height / 2.0]
end sub

sub uiSetHeight(node as object, height as float)
    if node = invalid then return
    if not node.hasField("height") then return
    node.height = height
    if node.subtype() = "Poster" then node.scaleRotateCenter = [node.width / 2.0, node.height / 2.0]
end sub

sub uiSetBackgroundColor(node as object, hex as string)
    if node = invalid then return
    if node.subtype() <> "Rectangle" then return
    if hex = ""
        node.color = "0x00000000"
    else
        if Len(hex) = 9 then node.color = "0x" + UCase(Mid(hex, 2, 8)) else node.color = "0x" + UCase(Mid(hex, 2, 6)) + "FF"
    end if
end sub

sub uiSetImageSource(node as object, uri as string)
    if node = invalid then return
    if node.hasField("uri") then node.uri = uri
end sub

sub uiRemove(node as object)
    if node = invalid then return
    p = node.getParent()
    if p <> invalid then p.removeChild(node)
end sub

' Load an image off-screen to learn its natural size, then call `loaded(width,
' height)`. SceneGraph reports it through the Poster's loadStatus field.
sub uiLoadImage(uri as string, loaded as dynamic)
    ga = GetGlobalAA()
    if ga.uiImageLoads = invalid then ga.uiImageLoads = {}
    if ga.uiImageSeq = invalid then ga.uiImageSeq = 0
    ga.uiImageSeq = ga.uiImageSeq + 1
    p = CreateObject("roSGNode", "Poster")
    p.id = "uiImageLoad" + ga.uiImageSeq.ToStr()
    p.visible = false
    ga.uiImageLoads[p.id] = { poster: p, loaded: loaded }
    ga.appScene.appendChild(p)
    p.observeField("loadStatus", "uiOnImageLoadStatus")
    p.uri = uri
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
    ' bitmapWidth/Height come back scaled from the device's UI resolution to the
    ' channel's 1080p design space; report the image's own pixels, as a browser does.
    scale = CreateObject("roDeviceInfo").GetUIResolution().height / 1080.0
    RtInvoke(entry.loaded, [p.bitmapWidth * scale, p.bitmapHeight * scale])
end sub

sub uiSetTranslation(node as object, x as float, y as float)
    if node <> invalid then node.translation = [x, y]
end sub

' tools/check.sh adds pkg:/freeze.txt to a test sideload: how many seconds of
' animation to step before freezing the frame. -1 (run live) when absent.
function uiFreezeSeconds() as float
    text = ReadAsciiFile("pkg:/freeze.txt")
    if text = "" then return -1.0
    return Val(text)
end function

' `pkg:/<name>.txt`, or "" — a test sideload adds it (Checks roku-install --add name.txt=value).
function uiLaunchArg(name as string) as string
    return ReadAsciiFile("pkg:/" + name + ".txt")
end function

' ── the network module (network/native): sockets and HTTP through Task nodes ──────
' The render thread cannot own an roStreamSocket or an roUrlTransfer, so each socket is an EveryUISocketTask node
' and each request an EveryUIHttpTask node (roku/runtime/*Task.brs): their fields carry the traffic, and the
' callbacks run here, on the render thread, like every other facade.
function uiSocketOpen(url as string, onOpen as dynamic, onText as dynamic, onClose as dynamic) as object
    ga = GetGlobalAA()
    if ga.uiSockets = invalid
        ga.uiSockets = {}
        ga.uiSocketSeq = 0
    end if
    ga.uiSocketSeq = ga.uiSocketSeq + 1
    t = CreateObject("roSGNode", "EveryUISocketTask")
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
        RtInvoke(h.onOpen, [])
    else if ev.kind = "text"
        RtInvoke(h.onText, [ev.data])
    else if ev.kind = "close"
        ga.uiSockets.Delete(t.id)
        RtInvoke(h.onClose, [ev.code, ev.reason])
    end if
end sub

sub uiSocketSend(t as object, text as string)
    t.outbox = text
end sub

sub uiSocketClose(t as object)
    t.command = "close"
end sub

sub uiHttp(method as string, url as string, headersJson as string, body as string, hasBody as boolean, done as dynamic)
    ga = GetGlobalAA()
    if ga.uiHttps = invalid
        ga.uiHttps = {}
        ga.uiHttpSeq = 0
    end if
    ga.uiHttpSeq = ga.uiHttpSeq + 1
    t = CreateObject("roSGNode", "EveryUIHttpTask")
    t.id = "uiHttp" + ga.uiHttpSeq.ToStr()
    ga.uiHttps[t.id] = { done: done, node: t }
    t.observeField("result", "uiOnHttpResult")
    t.url = url
    t.method = method
    headers = ParseJson(headersJson)
    if headers = invalid then headers = {}
    t.headers = headers
    t.body = body
    t.control = "RUN"
end sub

sub uiOnHttpResult(event as object)
    ga = GetGlobalAA()
    t = event.getRoSGNode()
    e = ga.uiHttps[t.id]
    if e = invalid then return
    ga.uiHttps.Delete(t.id)
    r = event.getData()
    RtInvoke(e.done, [r.status, r.headers, r.body])
end sub

' A SceneGraph Rectangle has no corner radius.
sub uiSetRadius(node as object, radius as float)
end sub

' Milliseconds since the epoch as local "yyyy-MM-dd HH:mm:ss".
function uiLocalDateTime(millis as float) as string
    dt = CreateObject("roDateTime")
    dt.FromSeconds(Int(millis / 1000.0))
    dt.ToLocalTime()
    return Right("0" + dt.GetYear().ToStr(), 4) + "-" + Right("0" + dt.GetMonth().ToStr(), 2) + "-" + Right("0" + dt.GetDayOfMonth().ToStr(), 2) + " " + Right("0" + dt.GetHours().ToStr(), 2) + ":" + Right("0" + dt.GetMinutes().ToStr(), 2) + ":" + Right("0" + dt.GetSeconds().ToStr(), 2)
end function

' A Roku has no pointer: nothing ever fires.
sub uiOnPointer(node as object, kind as string, f as dynamic)
end sub

' The key hook (everyui.focus.Focus); the generated runtime's RtKeyEvent feeds it from AppScene's onKeyEvent.
sub uiOnKey(f as dynamic)
    ga = GetGlobalAA()
    ga.uiKey = f
end sub

' Clip a Group to width by height (a scroll viewport).
sub uiSetClip(node as object, width as float, height as float)
    if node = invalid then return
    node.clippingRect = [0, 0, width, height]
end sub

' The character at a Unicode code point, as a string.
function uiChr(codePoint as integer) as string
    return Chr(codePoint)
end function

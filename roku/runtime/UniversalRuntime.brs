' universal-ui — Roku support runtime.
'
' The render seam's primitives as plain BrightScript globals. `emitRoku`
' copies this into the emitted channel's components/ and adds a <script> line
' for it to the generated AppScene.xml, beside the compiler's ScalaRuntime.brs
' (the Scala semantics BrightScript lacks). This file is the backend's own:
' what a render node and the platform can do.
'
' Every function here is declared as an @native facade in
' `universal.roku.facades` — the Scala side and this file are one API,
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

function uiGetRotation(node as object) as float
    if node = invalid then return 0.0
    r = node.rotation
    if r = invalid then return 0.0
    return r
end function

sub uiSetRotation(node as object, radians as float)
    if node <> invalid then node.rotation = radians
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

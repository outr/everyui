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
        if ReadAsciiFile("pkg:/viewport.txt") <> "" then ga.uiScaleValue = 1.0
    end if
    return ga.uiScaleValue
end function

' A SceneGraph Label, sized for 1080p and visible against the generated
' scene's light background.
function uiText() as object
    n = CreateObject("roSGNode", "Label")
    n.color = "0x1C1B1FFF"
    ga = GetGlobalAA()
    if ga.uiDefaultFont = invalid
        ga.uiDefaultFont = CreateObject("roSGNode", "Font")
        ga.uiDefaultFont.uri = "font:BoldSystemFontFile"
        ga.uiDefaultFont.size = 48
    end if
    n.font = ga.uiDefaultFont
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

' Larger or smaller about the node's own centre, which uiSetWidth and uiSetHeight keep as its pivot,
' so a focused card grows where the DOM and the view systems grow it.
sub uiSetScale(node as object, factor as float)
    if node = invalid then return
    node.scale = [factor, factor]
end sub

sub uiSetFontSize(node as object, size as float)
    if node = invalid then return
    f = CreateObject("roSGNode", "Font")
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

' Run `f` on a later turn of the render thread's event loop, on a fresh stack: a one-shot Timer drains the queue.
sub uiLater(f as dynamic)
    ga = GetGlobalAA()
    if ga.uiLaterQueue = invalid then ga.uiLaterQueue = []
    ga.uiLaterQueue.push(f)
    if ga.uiLaterTimer = invalid
        t = CreateObject("roSGNode", "Timer")
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

sub uiOnLater()
    ga = GetGlobalAA()
    ga.uiLaterArmed = false
    queue = ga.uiLaterQueue
    ga.uiLaterQueue = []
    for each f in queue
        RtInvoke(f, [])
    end for
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
        f = CreateObject("roSGNode", "Font")
        f.uri = uri
        f.size = uiCalibratedSize(uri, size)
        ga.uiFontNodes[key] = f
    end if
    return f
end function

' How wide and tall `text` is in a face at a size in pixels.
function uiMeasureText(text as string, uri as string, size as float) as object
    ga = GetGlobalAA()
    if ga.uiRunProbe = invalid then ga.uiRunProbe = CreateObject("roSGNode", "Label")
    probe = ga.uiRunProbe
    probe.font = uiFontNode(uri, size)
    probe.text = text
    rect = probe.boundingRect()
    return { width: rect.width, height: rect.height }
end function

' Text a Roku Label cannot draw alone: in more than one script, or in one its face has no glyphs for. It is drawn as
' runs of one script each, Labels inside `label`, each in a face that has its glyphs (uiScriptFace), in the order they
' read on screen. An Arabic run is shaped and reversed (uiShapeArabic), and in a paragraph that begins right to left (its
' first letter is Arabic) so is the order of the runs. Returns the width drawn in pixels, or -1 for text in the Latin
' script alone, which the label draws itself.
function uiRichText(label as object, text as string, baseUri as string, weight as integer, size as float, color as string) as float
    if label = invalid then return -1.0
    ' words, each with the script its letters are in ("" for a word of spaces-and-punctuation only)
    words = []
    complex = false
    rtl = invalid
    for each piece in text.Split(" ")
        script = ""
        n = Len(piece)
        for i = 1 to n
            s = uiScriptOf(Asc(Mid(piece, i, 1)))
            if s = "arabic" or s = "cjk"
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
    ' runs of consecutive words in one script; a word with no letters joins the run it follows
    runs = []
    for each w in words
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
    tint = color
    if color = "" then tint = label.color
    base = uiMeasureText("Ag", baseUri, size)
    space = uiMeasureText("A A", baseUri, size).width - uiMeasureText("AA", baseUri, size).width
    x = 0.0
    index = 0
    for each segment in runs
        runText = segment.words.Join(" ")
        if segment.script = "arabic" then runText = uiShapeArabic(runText)
        uri = uiScriptFace(segment.script, baseUri, weight)
        node = invalid
        if index < children then node = label.getChild(index)
        if node = invalid
            node = CreateObject("roSGNode", "Label")
            label.appendChild(node)
        end if
        node.font = uiFontNode(uri, size)
        node.text = runText
        node.color = tint
        measured = uiMeasureText(runText, uri, size)
        own = uiMeasureText("Ag", uri, size)
        ' the runs share a middle, whatever each face's line box
        node.translation = [x, (base.height - own.height) / 2.0]
        x = x + measured.width + space
        index = index + 1
    end for
    count = label.getChildCount()
    if count > index then label.removeChildrenIndex(count - index, index)
    return x - space
end function

' A face by its file (pkg:/fonts/<file>.ttf): the one the text is measured by, whatever its family.
sub uiSetFontFile(node as object, uri as string, size as float)
    if node = invalid then return
    node.font = uiFontNode(uri, size)
end sub

sub uiSetFont(node as object, family as string, weight as integer, size as float)
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
    node.width = uiPosterSide(node, width)
    if node.hasField("scaleRotateCenter") then node.scaleRotateCenter = [node.width / 2.0, node.height / 2.0]
end sub

sub uiSetHeight(node as object, height as float)
    if node = invalid then return
    if not node.hasField("height") then return
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

' The device's language as a BCP 47 tag ("en-US"; the Roku says "en_US").
function uiLanguage() as string
    return CreateObject("roDeviceInfo").GetCurrentLocale().Replace("_", "-")
end function

' What the person calls this Roku ("Living Room Roku"), else its model's name.
function uiDeviceName() as string
    info = CreateObject("roDeviceInfo")
    name = info.GetFriendlyName()
    if name = invalid or name = "" then name = info.GetModelDisplayName()
    return name
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

' Requests wait in a queue for one of a few EveryUIHttpTask workers (uiHttpWorkers), which keep their connections.
sub uiHttp(method as string, url as string, headersJson as string, body as string, hasBody as boolean, done as dynamic)
    ga = GetGlobalAA()
    if ga.uiHttps = invalid
        ga.uiHttps = {}
        ga.uiHttpSeq = 0
        ga.uiHttpQueue = []
        ga.uiHttpIdle = []
        ga.uiHttpPool = []
        for i = 1 to uiHttpWorkers()
            t = CreateObject("roSGNode", "EveryUIHttpTask")
            t.observeField("result", "uiOnHttpResult")
            t.observeField("ready", "uiOnHttpReady")
            t.control = "RUN"
            ga.uiHttpPool.Push(t)
        end for
    end if
    ga.uiHttpSeq = ga.uiHttpSeq + 1
    id = "uiHttp" + ga.uiHttpSeq.ToStr()
    ga.uiHttps[id] = done
    headers = ParseJson(headersJson)
    if headers = invalid then headers = {}
    ga.uiHttpQueue.Push({ id: id, url: url, method: method, headers: headers, body: body })
    uiHttpNext()
end sub

function uiHttpWorkers() as integer
    return 4
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

sub uiOnHttpResult(event as object)
    ga = GetGlobalAA()
    ga.uiHttpIdle.Push(event.getRoSGNode())
    r = event.getData()
    done = ga.uiHttps[r.id]
    ga.uiHttps.Delete(r.id)
    uiHttpNext()
    if done <> invalid then RtInvoke(done, [r.status, r.headers, r.body])
end sub

' A SceneGraph Rectangle has no corner radius.
sub uiSetRadius(node as object, radius as float)
end sub

' A shape behind a node's children, tinted: a rounded fill or edge (a white 9-patch in images/rr, made by the channel
' build), created the first time at `index` among the children, then updated. "#rrggbb" or "#rrggbbaa".
function uiShape(parent as object, existing as dynamic, uri as string, hex as string, width as float, height as float, index as integer) as object
    p = existing
    if p = invalid
        p = CreateObject("roSGNode", "Poster")
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

' A linear gradient behind a node's children, which SceneGraph cannot draw: a strip of it, 256 pixels along the
' gradient and one across, drawn on the device the first time it is asked for and stretched over the box. `angle` is
' CSS's (0 bottom to top, 90 left to right), taken to the nearest side; `stops` is "#rrggbbaa at,#rrggbbaa at".
function uiGradient(parent as object, existing as dynamic, angle as float, stops as string, width as float, height as float) as object
    p = existing
    if p = invalid
        p = CreateObject("roSGNode", "Poster")
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
    group = existing
    if group = invalid
        group = CreateObject("roSGNode", "MaskGroup")
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
    uri = uiRoundMask(w, h, r)
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
    if node = invalid or not node.hasField("lineSpacing") then return
    node.lineSpacing = spacing
end sub

' A Label wraps its words at its width (and draws every line), or keeps one line and cuts it short.
sub uiSetWrap(node as object, wrap as boolean)
    if node = invalid or not node.hasField("wrap") then return
    node.wrap = wrap
    if wrap then node.maxLines = 0
end sub

' Whether a node and everything under it is drawn: what a scroll has out of sight costs a Roku nothing.
sub uiSetVisible(node as object, visible as boolean)
    if node = invalid then return
    node.visible = visible
end sub

' The character at a Unicode code point, as a string.
function uiChr(codePoint as integer) as string
    return Chr(codePoint)
end function

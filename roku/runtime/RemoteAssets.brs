' everyui — a Roku channel's remote assets.
'
' A channel's package is held to 4 MB (certification 3.7), so the build (RokuChannel.remote) ships only what the
' channel needs to start and moves the rest -- pictures, fonts, sounds, translations, as RokuRemote says -- to a
' bundle the app's server hosts, each file named by the SHA-256 of its bytes. The channel carries the table of them,
' pkg:/everyui-assets.json: each remote file's package path with its hash, extension and size, a font's stand-in, a
' sound's compressed form, the base they are fetched from and what is fetched at start.
'
' A packaged path (pkg:/images/x.png) the build sent away resolves here to its copy in cachefs: once fetched (kept
' across launches while the device has room; tmp: where cachefs: cannot be written), and otherwise:
'   - a picture to its address, <base>/<hash>.<ext>, which a Poster reads over https itself, with a copy fetched in
'     the background for the next launch (uiAssetUrl);
'   - a sound to its address, always (a SoundEffect's WAV, an Audio node's compressed form): Roku documents a
'     SoundEffect as playing the package's WAVs and a server's, not a file in cachefs:, and keeps what it downloads;
'   - a font or a translation to nothing yet: it is fetched at once, a font drawn in its stand-in meanwhile and the
'     words in it drawn again as it arrives (uiFontWait), a translation handed over as it arrives (uiReadLater).
' Every fetch goes through the HTTP workers (HttpTask.brs, `save`), never the render thread, which checks its size and
' SHA-256 before giving it its name. A file that cannot be had is said once and left out: a picture not drawn, a font's
' stand-in kept, a translation empty (the app's own words). With no table, every path is the package's.
'
' The base is the build's, unless the registry's `assets_base` (Platform.saveSetting) or a launch's `everyui_assets`
' says otherwise; it is https, or http where the build allowed it for a test.

' The table, read the first time a path is resolved: { none, base, files, prefetch } and what is under way.
function uiAssets() as object
    ga = GetGlobalAA()
    t = ga.uiAssetTable
    if t <> invalid then return t
    t = { none: true, base: "", files: {}, prefetch: [], fetching: {}, failed: {}, waits: {}, fonts: {} }
    t.fontWaits = {}
    t.queue = []
    t.busy = false
    t.awaiting = 0
    ' (looked for first: a read of a file that is not there is an error on the console)
    text = ""
    listing = ListDir("pkg:/")
    if listing <> invalid
        for each name in listing
            if name = "everyui-assets.json" then text = ReadAsciiFile("pkg:/everyui-assets.json")
        end for
    end if
    if text <> ""
        parsed = ParseJson(text)
        if type(parsed) = "roAssociativeArray" and type(parsed.files) = "roAssociativeArray"
            t.none = false
            t.files = parsed.files
            if type(parsed.prefetch) = "roArray" then t.prefetch = parsed.prefetch
            http = false
            if type(parsed.http) = "roBoolean" or type(parsed.http) = "Boolean" then http = parsed.http
            t.base = uiAssetBase(_str(parsed.base), http)
        end if
    end if
    ga.uiAssetTable = t
    ' what is fetched at start, a few seconds in, behind what the first screen asks for
    if not t.none then uiAfter(5.0, { fn: uiAssetStartFn })
    return t
end function

' Where the files are fetched from: a launch's everyui_assets, else the registry's assets_base, else the build's; ""
' (nothing fetched, said once) where it is not https, or http where the build allowed it.
function uiAssetBase(baked as string, http as boolean) as string
    base = baked
    params = GetGlobalAA().uiLaunchParams
    given = invalid
    if type(params) = "roAssociativeArray" then given = params.everyui_assets
    if given <> invalid
        base = _str(given)
    else
        kept = uiSetting("assets_base")
        if kept <> "" then base = kept
    end if
    if Right(base, 1) = "/" then base = Left(base, Len(base) - 1)
    lower = LCase(base)
    if Left(lower, 8) = "https://" then return base
    if http and Left(lower, 7) = "http://" then return base
    said = "[assets] the base '" + base + "' is not https: no remote asset is fetched"
    print said
    return ""
end function

' The table's entry for a packaged path, or invalid where it ships in the package (or there is no table).
function uiAssetEntry(uri as string) as dynamic
    t = GetGlobalAA().uiAssetTable
    if t = invalid then t = uiAssets()
    if t.none then return invalid
    if Left(uri, 5) <> "pkg:/" then return invalid
    return t.files[Mid(uri, 6)]
end function

' The copies fetched before, by name (everyui-a-<hash>.<ext>), each with where it is: cachefs: and tmp: listed once, and
' again once the channel is back from behind another (the device may have cleared cachefs: meanwhile).
function uiAssetHere() as object
    ga = GetGlobalAA()
    if ga.uiAssetHere <> invalid then return ga.uiAssetHere
    here = {}
    for each dir in ["tmp:/", "cachefs:/"]
        names = ListDir(dir)
        if names <> invalid
            for each name in names
                if Left(name, 10) = "everyui-a-" and Right(name, 5) <> ".part" then here[name] = dir + name
            end for
        end if
    end for
    ga.uiAssetHere = here
    return here
end function

' Where a packaged file is read from: `uri` itself unless the build sent it away, then its copy once fetched; "" while
' it is on its way (its fetch begun, at once) or could not be had.
function uiAssetFile(uri as string) as string
    e = uiAssetEntry(uri)
    if e = invalid then return uri
    here = uiAssetHere()
    at = here["everyui-a-" + e.h + "." + e.e]
    if at <> invalid then return at
    uiAssetFetch(Mid(uri, 6), e, false, true)
    return ""
end function

' The same for a picture or a sound, which a Poster, a SoundEffect and an Audio node read over https themselves. A
' picture: its copy if there is one, else its address, a copy then fetched in the background for the next launch. A
' sound: its address, always, as Roku documents a SoundEffect's source (the package, or a WAV on a server; it keeps
' those it downloaded itself), never a copy of it; `media` asks for its compressed form, for an Audio node (a
' SoundEffect plays only the WAV). "" where nothing can be fetched.
function uiAssetUrl(uri as string, media as boolean) as string
    e = uiAssetEntry(uri)
    if e = invalid then return uri
    h = e.h
    x = e.e
    if media and e.mh <> invalid
        h = e.mh
        x = e.me
    end if
    t = GetGlobalAA().uiAssetTable
    sound = Left(uri, 12) = "pkg:/sounds/"
    if not sound
        here = uiAssetHere()
        at = here["everyui-a-" + h + "." + x]
        if at <> invalid then return at
    end if
    if t.base = "" then return ""
    if sound then return t.base + "/" + h + "." + x
    uiAssetFetch(Mid(uri, 6), e, media, false)
    return t.base + "/" + h + "." + x
end function

' Fetch a remote file into the cache, once however often it is asked for, one file at a time: next (`now`: a font or a
' translation that something waits for) or behind the rest (a picture read over https meanwhile, what is fetched at
' start). One at a time keeps the HTTP workers for the app's own requests, and a file whole before the next is begun
' (brs-engine, whose workers share one file system, mixed up files written at once). A file that failed is not asked
' for again this run.
sub uiAssetFetch(path as string, e as object, media as boolean, now as boolean)
    t = uiAssets()
    h = e.h
    x = e.e
    size = e.s
    if media and e.mh <> invalid
        h = e.mh
        x = e.me
        size = e.ms
    end if
    name = "everyui-a-" + h + "." + x
    job = { path: path, name: name, url: t.base + "/" + h + "." + x, hash: h, size: size, now: now }
    if t.failed[name] <> invalid
        if now then uiAssetMissing(job, -1, "failed before")
        return
    end if
    if t.base = ""
        t.failed[name] = true
        uiAssetMissing(job, -1, "no base")
        return
    end if
    under = t.fetching[name]
    if under = "going" then return
    if under = "queued"
        if not now then return
        ' wanted now: to the front of the queue
        kept = []
        for each queued in t.queue
            if queued.name <> name then kept.Push(queued)
        end for
        t.queue = kept
    end if
    t.fetching[name] = "queued"
    if now then t.queue.Unshift(job) else t.queue.Push(job)
    uiAssetNext()
end sub

' The next fetch, if none is under way.
sub uiAssetNext()
    t = uiAssets()
    if t.busy or t.queue.Count() = 0 then return
    job = t.queue.Shift()
    t.busy = true
    t.fetching[job.name] = "going"
    uiAssetStart(job)
end sub

' A fetch handed to the HTTP workers, which write it to the cache under its name (HttpTask.brs: fetchSave).
sub uiAssetStart(job as object)
    request = { url: job.url, method: "GET", headers: {}, body: "", hasBody: false }
    request.save = job.name
    request.sha = job.hash
    request.size = job.size
    uiHttpEnqueue(request, { fn: uiAssetFetchedFn, job: job })
end sub

' A worker's answer (status, headers, where the copy is): the copy noted, and what waited for it told.
function uiAssetFetchedFn(cl as object, args as object) as dynamic
    job = cl.job
    t = uiAssets()
    t.fetching.Delete(job.name)
    status = args[0]
    at = _str(args[2])
    if status = 200 and at <> ""
        here = uiAssetHere()
        here[job.name] = at
        uiAssetArrived(job, at)
    else
        t.failed[job.name] = true
        uiAssetMissing(job, status, at)
    end if
    t.busy = false
    uiAssetNext()
    return invalid
end function

' A file arrived at `at`: a font's Font nodes given it, and what waited for it told.
sub uiAssetArrived(job as object, at as string)
    if Left(job.path, 6) = "fonts/" then uiFontArrived(job.path, at)
    uiAssetTell(job.path, at)
end sub

' A file that could not be had, said once: what waited for it goes on without it (a font's stand-in kept).
sub uiAssetMissing(job as object, status as dynamic, why as string)
    said = "[assets] " + job.path + " could not be fetched (" + _str(status) + " " + why + ") from " + job.url
    said = said + ": left out"
    uiReportOnceSaid("asset " + job.path, said)
    if Left(job.path, 6) = "fonts/" then uiFontArrived(job.path, "")
    uiAssetTell(job.path, "")
end sub

' Say `said` once a run for `key`.
sub uiReportOnceSaid(key as string, said as string)
    ga = GetGlobalAA()
    if ga.uiAssetSaid = invalid then ga.uiAssetSaid = {}
    if ga.uiAssetSaid.DoesExist(key) then return
    ga.uiAssetSaid[key] = true
    print said
end sub

' Those waiting for a file's text (uiReadLater) handed it, read from its copy at `at`, "" where it could not be had.
sub uiAssetTell(path as string, at as string)
    t = uiAssets()
    waiting = t.waits[path]
    if waiting = invalid then return
    t.waits.Delete(path)
    text = ""
    if at <> "" then text = ReadAsciiFile(at)
    for each done in waiting
        RtGuard(done, [text])
    end for
end sub

' Whether a packaged text file (a translation) is one the build sent away and is not here yet: its fetch begun, and
' Platform.readText waiting for it (uiReadLater) rather than reading it now.
function uiAssetDue(path as string) as boolean
    return uiAssetFile(path) = ""
end function

' `done` handed the text of a packaged file the build sent away, as it arrives ("" where it cannot be had).
sub uiReadLater(path as string, done as dynamic)
    e = uiAssetEntry(path)
    if e = invalid
        RtGuard(done, [ReadAsciiFile(path)])
        return
    end if
    t = uiAssets()
    key = Mid(path, 6)
    if t.failed["everyui-a-" + e.h + "." + e.e] <> invalid
        RtGuard(done, [""])
        return
    end if
    if t.waits[key] = invalid then t.waits[key] = []
    t.waits[key].Push(done)
    uiAssetFetch(key, e, false, true)
end sub

' ── fonts ──
' A Font node's uri takes a file the device can open; Roku's documents speak only of the package's, so a fetched face is
' told on the console as it is first drawn from (uiFontArrived), its width against its stand-in's, for a device to show
' whether it took it.

' The file a face is drawn from: its own (`uri`, or its fetched copy); while a remote one is on its way, its stand-in,
' with `f` (the Font node made for it) marked as waiting (uiAwaits) and kept to be given its own as it arrives.
function uiAssetFont(uri as string, f as object, size as float, key as string) as string
    e = uiAssetEntry(uri)
    if e = invalid then return uri
    file = uiAssetFile(uri)
    if file <> "" then return file
    standIn = "font:SystemFontFile"
    if e.f <> invalid and e.f <> "" then standIn = "pkg:/" + e.f
    t = uiAssets()
    if t.failed["everyui-a-" + e.h + "." + e.e] <> invalid then return standIn
    path = Mid(uri, 6)
    f.addFields({ uiAwaits: path, uiAsked: size, uiKey: key })
    if t.fonts[path] = invalid then t.fonts[path] = []
    t.fonts[path].Push(f)
    t.awaiting = t.awaiting + 1
    return standIn
end function

' The file a face is measured in, without waiting: its own or its copy, else its stand-in (uiTextWidth's Font nodes,
' dropped as a face arrives).
function uiAssetFontFile(uri as string) as string
    e = uiAssetEntry(uri)
    if e = invalid then return uri
    file = uiAssetFile(uri)
    if file <> "" then return file
    if e.f <> invalid and e.f <> "" then return "pkg:/" + e.f
    return "font:SystemFontFile"
end function

' A size calibrated for a face, with a system face (a stand-in where the channel has none) at its own size.
function uiAssetFontSize(file as string, size as float) as integer
    if Left(file, 5) = "font:" then return Int(size + 0.5)
    return uiCalibratedSize(file, size)
end function

' The faces the channel fetches, beside those it ships, for uiScriptFace's listing (a script's fallback-* face).
sub uiAssetListFonts(listing as object)
    t = uiAssets()
    if t.none then return
    for each path in t.files
        if Left(path, 6) = "fonts/" then listing[Mid(path, 7)] = true
    end for
end sub

' A face arrived at `at` ("" where it could not be had): each Font node drawn in its stand-in meanwhile given it, at its
' size calibrated for it, and let go of the caches (a Label given its font again gets a new one); what was measured in
' the stand-in measured again; and each text that waited for it (uiFontWait) told, to be drawn again.
sub uiFontArrived(path as string, at as string)
    ga = GetGlobalAA()
    t = uiAssets()
    nodes = t.fonts[path]
    t.fonts.Delete(path)
    if nodes <> invalid
        for each f in nodes
            if at <> ""
                f.uri = at
                f.size = uiCalibratedSize(at, f.uiAsked)
            end if
            f.uiAwaits = ""
            if ga.uiFontNodes <> invalid then ga.uiFontNodes.Delete(f.uiKey)
            t.awaiting = t.awaiting - 1
        end for
    end if
    ga.uiLineWidths = invalid
    ga.uiLineBoxes = invalid
    ga.uiMeasureFonts = {}
    if at <> "" then uiFontSaid(path, at)
    waiting = t.fontWaits[path]
    t.fontWaits.Delete(path)
    if waiting = invalid then return
    for each ready in waiting
        RtGuard(ready, [])
    end for
end sub

' The console told a fetched face's width beside its stand-in's: the same width is a device that drew the stand-in.
sub uiFontSaid(path as string, at as string)
    t = uiAssets()
    e = t.files[path]
    standIn = "font:SystemFontFile"
    if e <> invalid and e.f <> invalid and e.f <> "" then standIn = "pkg:/" + e.f
    probe = uiNode("Label")
    probe.text = "Hamburgefonstiv 0123"
    f = uiNode("Font")
    f.uri = at
    f.size = 40
    probe.font = f
    own = probe.boundingRect().width
    g = uiNode("Font")
    g.uri = standIn
    g.size = 40
    probe.font = g
    other = probe.boundingRect().width
    said = "[assets] " + path + " drawn from " + at + ": " + Int(own).ToStr() + " wide where its stand-in is "
    said = said + Int(other).ToStr()
    print said
end sub

' Whether `label`'s words are drawn in the faces they are in: false where it, or a run of it in another script, is drawn
' in a stand-in while its face is on its way; `ready` is then called once that face has arrived (or will not), for the
' words to be drawn and measured again.
function uiFontWait(label as object, ready as dynamic) as boolean
    t = GetGlobalAA().uiAssetTable
    if t = invalid or t.none or t.awaiting <= 0 or label = invalid then return true
    due = uiFontDue(label.font)
    if due = ""
        n = label.getChildCount()
        for i = 0 to n - 1
            child = label.getChild(i)
            if child <> invalid and due = "" then due = uiFontDue(child.font)
        end for
    end if
    if due = "" then return true
    if t.fontWaits[due] = invalid then t.fontWaits[due] = []
    t.fontWaits[due].Push(ready)
    return false
end function

' The remote face a Font node waits for, or "".
function uiFontDue(f as dynamic) as string
    if type(f) <> "roSGNode" then return ""
    if not f.hasField("uiAwaits") then return ""
    return f.uiAwaits
end function

' The app's language read: the face for its script fetched now, where the build sent it away (Chinese, Japanese and
' Korean the CJK face, Traditional for zh_Hant, Taiwan and Hong Kong; Arabic, Persian and Urdu the Arabic), so it is
' here by the time its words are drawn.
sub uiAssetLanguage(code as string)
    t = uiAssets()
    if t.none then return
    lower = LCase(code)
    two = Left(lower, 2)
    name = ""
    if two = "zh" or two = "ja" or two = "ko"
        name = "cjk-sc"
        if lower = "zh_hant" or Left(lower, 5) = "zh_tw" or Left(lower, 5) = "zh_hk" then name = "cjk-tc"
    else if two = "ar" or two = "fa" or two = "ur"
        name = "arabic"
    end if
    if name = "" then return
    for each suffix in ["", "-bold"]
        for each ext in [".ttf", ".otf"]
            uri = "pkg:/fonts/fallback-" + name + suffix + ext
            if uiAssetEntry(uri) <> invalid then uiAssetFile(uri)
        end for
    end for
end sub

' A few seconds in: what the build said to fetch at start, in the background, and the copies of files no longer in the
' table (an earlier release's) taken out of the cache.
function uiAssetStartFn(_cl as object, _args as object) as dynamic
    t = uiAssets()
    wanted = {}
    for each path in t.files
        e = t.files[path]
        wanted["everyui-a-" + e.h + "." + e.e] = true
        if e.mh <> invalid then wanted["everyui-a-" + e.mh + "." + e.me] = true
    end for
    here = uiAssetHere()
    gone = []
    for each name in here
        if not wanted.DoesExist(name) then gone.Push(name)
    end for
    for each name in gone
        DeleteFile(here[name])
        here.Delete(name)
    end for
    for each path in t.prefetch
        e = t.files[path]
        if e <> invalid and here["everyui-a-" + e.h + "." + e.e] = invalid then uiAssetFetch(path, e, false, false)
    end for
    return invalid
end function

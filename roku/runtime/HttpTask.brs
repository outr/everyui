' EveryUI's HTTP worker on the Roku (the network module): the render thread cannot own an roUrlTransfer, so a few of
' these run for the life of the channel, each taking requests one at a time on its `request` field ({id, url, method,
' headers, body}, and bytes, binary and bodyHex for bytes) and answering on `result` ({id, status, headers (JSON), body},
' and hex). Each keeps its roUrlTransfer, and so its connection: a request of its own would pay for a TLS handshake every
' time, which on a Roku is most of the wait.
sub init()
    m.top.functionName = "runTask"
end sub

sub runTask()
    port = CreateObject("roMessagePort")
    m.top.observeField("request", port)
    m.top.ready = true ' only now can a request be seen
    u = invalid
    while true
        msg = wait(0, port)
        if type(msg) = "roSGNodeEvent" and msg.getField() = "request"
            req = msg.getData()
            if u = invalid then u = newTransfer(req.url)
            r = fetch(u, req)
            ' a transfer that will not start is one still busy with something it lost track of (the channel held in the
            ' debugger through a long poll, say): it is let go and the request made once more on a new one, rather than
            ' every request after it failing on the same busy transfer
            if r.status = -1 and r.body = "request not started"
                u.AsyncCancel()
                u = newTransfer(req.url)
                r = fetch(u, req)
            end if
            m.top.result = r
        end if
    end while
end sub

function newTransfer(url as string) as object
    u = CreateObject("roUrlTransfer")
    u.SetMessagePort(CreateObject("roMessagePort"))
    u.EnableEncodings(true)
    u.RetainBodyOnError(true)
    if Left(url, 6) = "https:"
        u.SetCertificatesFile("common:/certs/ca-bundle.crt")
        u.InitClientCertificates()
    end if
    return u
end function

function fetch(u as object, req as object) as object
    u.SetUrl(req.url)
    headers = req.headers
    if headers = invalid then headers = {}
    u.SetHeaders(headers)
    method = UCase(req.method)
    u.SetRequest(method)
    if req.bytes <> invalid then return fetchBytes(u, req, method)
    started = false
    if method = "GET"
        started = u.AsyncGetToString()
    else if method = "HEAD"
        started = u.AsyncHead()
    else
        started = u.AsyncPostFromString(req.body)
    end if
    if not started then return { id: req.id, status: -1, headers: "{}", body: "request not started" }
    msg = wait(60000, u.GetMessagePort())
    if type(msg) = "roUrlEvent"
        return { id: req.id, status: msg.GetResponseCode(), headers: FormatJson(msg.GetResponseHeaders()), body: msg.GetString() }
    end if
    u.AsyncCancel()
    return { id: req.id, status: -1, headers: "{}", body: "timeout" }
end function

' A request with a body of bytes, or wanting its answer's (`binary`): sent from and taken into files in tmp:, which
' carry every byte where a string would stop at a NUL, and handed back as hex ({hex}), which a field can carry.
function fetchBytes(u as object, req as object, method as string) as object
    fs = CreateObject("roFileSystem")
    base = "tmp:/everyui-" + req.id
    sent = invalid
    if req.bodyHex <> invalid or method <> "GET"
        body = CreateObject("roByteArray")
        if req.bodyHex <> invalid then body.FromHexString(req.bodyHex)
        sent = base + "-body"
        body.WriteFile(sent)
    end if
    got = invalid
    if req.binary = true then got = base + "-answer"
    started = false
    if method = "HEAD"
        started = u.AsyncHead()
    else if sent = invalid and got <> invalid
        started = u.AsyncGetToFile(got)
    else if sent = invalid
        started = u.AsyncGetToString()
    else if got = invalid
        started = u.AsyncPostFromFile(sent)
    else
        started = u.AsyncPostFromFileToFile(sent, got)
    end if
    r = { id: req.id, bytes: true, status: -1, headers: "{}", body: "request not started" }
    if started
        msg = wait(60000, u.GetMessagePort())
        if type(msg) = "roUrlEvent"
            r.status = msg.GetResponseCode()
            r.headers = FormatJson(msg.GetResponseHeaders())
            r.body = msg.GetString()
            if got <> invalid and fs.Exists(got)
                answer = CreateObject("roByteArray")
                answer.ReadFile(got)
                r.hex = answer.ToHexString()
                r.body = answer.ToAsciiString()
            end if
        else
            u.AsyncCancel()
            r.body = "timeout"
        end if
    end if
    if sent <> invalid then fs.Delete(sent)
    if got <> invalid and fs.Exists(got) then fs.Delete(got)
    return r
end function

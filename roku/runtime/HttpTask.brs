' EveryUI's HTTP worker on the Roku (the network module): the render thread cannot own an roUrlTransfer, so a few of
' these run for the life of the channel, each taking requests one at a time on its `request` field ({id, url, method,
' headers, body}) and answering on `result` ({id, status, headers (JSON), body}). Each keeps its roUrlTransfer, and so
' its connection: a request of its own would pay for a TLS handshake every time, which on a Roku is most of the wait.
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
            m.top.result = fetch(u, req)
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

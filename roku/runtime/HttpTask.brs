' EveryUI's HTTP request on the Roku (the network module): one roUrlTransfer in a Task node, since the render
' thread cannot own one. url / method / headers / body in; result {status, headers (JSON), body} out.
sub init()
    m.top.functionName = "runTask"
end sub

sub runTask()
    port = CreateObject("roMessagePort")
    u = CreateObject("roUrlTransfer")
    u.SetMessagePort(port)
    u.SetUrl(m.top.url)
    u.EnableEncodings(true)
    if Left(m.top.url, 6) = "https:"
        u.SetCertificatesFile("common:/certs/ca-bundle.crt")
        u.InitClientCertificates()
    end if
    headers = m.top.headers
    if headers <> invalid
        for each k in headers
            u.AddHeader(k, headers[k])
        end for
    end if
    method = UCase(m.top.method)
    started = false
    if method = "GET"
        started = u.AsyncGetToString()
    else if method = "HEAD"
        started = u.AsyncHead()
    else
        u.SetRequest(method)
        started = u.AsyncPostFromString(m.top.body)
    end if
    if not started
        m.top.result = { status: -1, headers: "{}", body: "request not started" }
        return
    end if
    msg = wait(60000, port)
    if type(msg) = "roUrlEvent"
        m.top.result = { status: msg.GetResponseCode(), headers: FormatJson(msg.GetResponseHeaders()), body: msg.GetString() }
    else
        u.AsyncCancel()
        m.top.result = { status: -1, headers: "{}", body: "timeout" }
    end if
end sub

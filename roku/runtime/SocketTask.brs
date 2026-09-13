' EveryUI's WebSocket on the Roku (the network module): an roStreamSocket in a Task node, since the render thread
' cannot own one, speaking RFC 6455 on the client side — text frames, fragments, ping/pong, close. ws:// only:
' roStreamSocket has no TLS.
'   url          ws://host[:port]/path, set before RUN
'   outbox       a text message to send (set from the render thread; alwaysNotify)
'   command      "close"
'   socketEvent  {kind: "open"} | {kind: "text", data} | {kind: "close", code, reason} — the scene observes it
sub init()
    m.top.functionName = "runTask"
end sub

sub emitEvent(ev as object)
    m.top.socketEvent = ev
end sub

sub runTask()
    m.port = CreateObject("roMessagePort")
    m.top.observeField("outbox", m.port)
    m.top.observeField("command", m.port)
    url = m.top.url
    if Left(url, 5) <> "ws://"
        emitEvent({ kind: "close", code: 1006, reason: "only ws:// on the Roku (roStreamSocket has no TLS): " + url })
        return
    end if
    rest = Mid(url, 6)
    slash = Instr(1, rest, "/")
    if slash = 0
        hostPort = rest
        path = "/"
    else
        hostPort = Left(rest, slash - 1)
        path = Mid(rest, slash)
    end if
    colon = Instr(1, hostPort, ":")
    if colon = 0
        host = hostPort
        port = 80
    else
        host = Left(hostPort, colon - 1)
        port = Val(Mid(hostPort, colon + 1), 10)
    end if

    m.sock = CreateObject("roStreamSocket")
    addr = CreateObject("roSocketAddress")
    addr.SetHostName(host)
    addr.SetPort(port)
    m.sock.SetSendToAddress(addr)
    m.sock.SetMessagePort(m.port)
    m.sock.NotifyReadable(true)
    if not m.sock.Connect()
        emitEvent({ kind: "close", code: 1006, reason: "connect failed: " + host + ":" + port.ToStr() })
        return
    end if

    ' the upgrade
    nonce = CreateObject("roByteArray")
    nonce.FromAsciiString(Left(CreateObject("roDeviceInfo").GetRandomUUID(), 16))
    crlf = Chr(13) + Chr(10)
    request = "GET " + path + " HTTP/1.1" + crlf + "Host: " + hostPort + crlf + "Upgrade: websocket" + crlf
    request = request + "Connection: Upgrade" + crlf + "Sec-WebSocket-Key: " + nonce.ToBase64String() + crlf
    request = request + "Sec-WebSocket-Version: 13" + crlf + crlf
    bytes = CreateObject("roByteArray")
    bytes.FromAsciiString(request)
    if not sendAll(bytes)
        emitEvent({ kind: "close", code: 1006, reason: "send failed during the handshake" })
        return
    end if

    m.buf = CreateObject("roByteArray")
    m.pos = 0
    m.open = false
    m.fragment = ""
    while true
        msg = wait(0, m.port)
        if type(msg) = "roSocketEvent"
            if not readSome()
                m.sock.Close()
                emitEvent({ kind: "close", code: 1006, reason: "connection closed" })
                return
            end if
            if m.open
                if not parseFrames() then return
            else
                if not finishHandshake() then return
            end if
        else if type(msg) = "roSGNodeEvent"
            field = msg.getField()
            if field = "outbox"
                data = CreateObject("roByteArray")
                data.FromAsciiString(msg.getData())
                sendFrame(1, data)
            else if field = "command" and msg.getData() = "close"
                closeData = CreateObject("roByteArray")
                closeData.Push(3)
                closeData.Push(232)  ' 1000
                sendFrame(8, closeData)
                m.sock.Close()
                emitEvent({ kind: "close", code: 1000, reason: "closed" })
                return
            end if
        end if
    end while
end sub

' Everything readable right now, appended to m.buf. False when the peer is gone.
function readSome() as boolean
    n = m.sock.GetCountRcvBuf()
    if n <= 0
        if m.sock.IsConnected() then return true
        return false
    end if
    chunk = CreateObject("roByteArray")
    chunk[n - 1] = 0
    got = m.sock.Receive(chunk, 0, n)
    if got <= 0 then return false
    for i = 0 to got - 1
        m.buf.Push(chunk[i])
    end for
    return true
end function

' The HTTP response to the upgrade, then any frames that came with it.
function finishHandshake() as boolean
    n = m.buf.Count()
    i = m.pos
    while i + 3 < n
        if m.buf[i] = 13 and m.buf[i + 1] = 10 and m.buf[i + 2] = 13 and m.buf[i + 3] = 10
            head = CreateObject("roByteArray")
            for k = m.pos to i - 1
                head.Push(m.buf[k])
            end for
            text = head.ToAsciiString()
            m.pos = i + 4
            if Instr(1, text, " 101 ") = 0
                m.sock.Close()
                emitEvent({ kind: "close", code: 1002, reason: "no upgrade: " + Left(text, 80) })
                return false
            end if
            m.open = true
            emitEvent({ kind: "open" })
            return parseFrames()
        end if
        i = i + 1
    end while
    return true
end function

' Every complete frame in m.buf. False once the connection is closed.
function parseFrames() as boolean
    while true
        n = m.buf.Count() - m.pos
        if n < 2 then exit while
        p = m.pos
        b0 = m.buf[p]
        b1 = m.buf[p + 1]
        fin = (b0 and 128) <> 0
        opcode = b0 and 15
        masked = (b1 and 128) <> 0
        plen = b1 and 127
        off = 2
        if plen = 126
            if n < 4 then exit while
            plen = m.buf[p + 2] * 256 + m.buf[p + 3]
            off = 4
        else if plen = 127
            if n < 10 then exit while
            plen = ((m.buf[p + 6] * 256 + m.buf[p + 7]) * 256 + m.buf[p + 8]) * 256 + m.buf[p + 9]
            off = 10
        end if
        if masked then off = off + 4
        if n < off + plen then exit while
        payload = CreateObject("roByteArray")
        if plen > 0
            payload[plen - 1] = 0
            if masked
                for i = 0 to plen - 1
                    payload[i] = bxor(m.buf[p + off + i], m.buf[p + off - 4 + (i mod 4)])
                end for
            else
                for i = 0 to plen - 1
                    payload[i] = m.buf[p + off + i]
                end for
            end if
        end if
        m.pos = p + off + plen
        if opcode = 1 or opcode = 0
            text = payload.ToAsciiString()
            if not fin
                m.fragment = m.fragment + text
            else
                if m.fragment <> ""
                    text = m.fragment + text
                    m.fragment = ""
                end if
                emitEvent({ kind: "text", data: text })
            end if
        else if opcode = 8
            code = 1005
            if plen >= 2 then code = payload[0] * 256 + payload[1]
            sendFrame(8, payload)
            m.sock.Close()
            emitEvent({ kind: "close", code: code, reason: "closed by the server" })
            return false
        else if opcode = 9
            sendFrame(10, payload)
        end if
    end while
    if m.pos >= m.buf.Count()
        m.buf.Clear()
        m.pos = 0
    end if
    return true
end function

function bxor(a as integer, b as integer) as integer
    return (a or b) and (255 - (a and b))
end function

' One masked client frame.
sub sendFrame(opcode as integer, data as object)
    n = data.Count()
    frame = CreateObject("roByteArray")
    frame.Push(128 + opcode)
    if n < 126
        frame.Push(128 + n)
    else if n < 65536
        frame.Push(254)
        frame.Push(Int(n / 256))
        frame.Push(n mod 256)
    else
        frame.Push(255)
        frame.Push(0)
        frame.Push(0)
        frame.Push(0)
        frame.Push(0)
        frame.Push(Int(n / 16777216) mod 256)
        frame.Push(Int(n / 65536) mod 256)
        frame.Push(Int(n / 256) mod 256)
        frame.Push(n mod 256)
    end if
    mask = [Rnd(256) - 1, Rnd(256) - 1, Rnd(256) - 1, Rnd(256) - 1]
    for each b in mask
        frame.Push(b)
    end for
    for i = 0 to n - 1
        frame.Push(bxor(data[i], mask[i mod 4]))
    end for
    sendAll(frame)
end sub

function sendAll(frame as object) as boolean
    total = frame.Count()
    sent = 0
    while sent < total
        k = m.sock.Send(frame, sent, total - sent)
        if k < 0
            if not m.sock.eAgain() then return false
            k = 0
        end if
        sent = sent + k
    end while
    return true
end function

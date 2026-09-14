package everyui.network

import everyui.compiler.api.native

// The network module's facades on the emitted backends (Roku: roku/runtime/UniversalRuntime.brs and the
// EveryUISocketTask / EveryUIHttpTask nodes; tvOS: swift/runtime/UniversalRuntime.swift over URLSession).
// Every callback arrives on the UI thread.

/** Open a WebSocket to `url`; the handle is opaque. `onClose(code, reason)` also reports a failed connect (1006). */
@native object uiSocketOpen {
  def apply(url: String, onOpen: () => Unit, onText: String => Unit, onClose: (Int, String) => Unit): Any = native.value
}
@native object uiSocketSend {
  def apply(handle: Any, text: String): Unit = native.value
}
@native object uiSocketClose {
  def apply(handle: Any): Unit = native.value
}
/** One HTTP request; `done(status, headersJson, body)` — status -1 when the request itself failed. */
@native object uiHttp {
  def apply(method: String, url: String, headersJson: String, body: String, hasBody: Boolean,
            done: (Int, String, String) => Unit): Unit = native.value
}

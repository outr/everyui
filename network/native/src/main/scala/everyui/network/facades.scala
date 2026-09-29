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
/** One HTTP request whose body is bytes (`hasBody`), or whose answer is wanted as bytes (`binary`) as well as text:
  * `done(status, headersJson, body, bytes)`, `bytes` empty where they were not asked for; status -1 when the request
  * itself failed. The Roku's worker takes the bytes to and from files, so they come through whole. */
@native object uiHttpBytes {
  def apply(method: String, url: String, headersJson: String, body: Array[Byte], hasBody: Boolean, binary: Boolean,
            done: (Int, String, String, Array[Byte]) => Unit): Unit = native.value
}
/** `text` as the bytes of its UTF-8. */
@native object uiUtf8 {
  def apply(text: String): Array[Byte] = native.value
}
/** UTF-8 bytes as the text they are. */
@native object uiUtf8Text {
  def apply(bytes: Array[Byte]): String = native.value
}

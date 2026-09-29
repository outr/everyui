package everyui.render

import org.scalajs.dom

import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

/** base64url, as WebAuthn's JSON and a VAPID key write bytes: to the bytes (padded or not), and back (unpadded). */
object Base64Url {
  def decode(text: String): Uint8Array = {
    val base64 = text.replace('-', '+').replace('_', '/')
    val binary = dom.window.atob(base64 + "=" * ((4 - base64.length % 4) % 4))
    val out = new Uint8Array(binary.length)
    (0 until binary.length).foreach(i => out(i) = binary.charAt(i).toShort)
    out
  }

  def encode(buffer: ArrayBuffer): String = {
    val view = new Uint8Array(buffer)
    val binary = new java.lang.StringBuilder
    (0 until view.length).foreach(i => binary.append(view(i).toChar))
    dom.window.btoa(binary.toString).replace('+', '-').replace('/', '_').reverse.dropWhile(_ == '=').reverse
  }
}

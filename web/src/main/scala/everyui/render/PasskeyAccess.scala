package everyui.render

import everyui.passkey.PasskeyResult
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

/** Passkeys (`everyui.passkey.Passkeys`) through `navigator.credentials`: the server's options, JSON with base64url
  * fields, made into what `create` and `get` take (the browser's own `parse…OptionsFromJSON` where it has it), and the
  * credential answered as `toJSON()` writes it (written out field by field where the browser has no `toJSON`). A
  * request the person closes, lets time out, or that another request ends, is PasskeyCancelled. A page has one request
  * at a time, so each one ends the one still waiting (an autofill request, or a click's the person has not answered). */
object PasskeyAccess {
  // the request waiting for the person, ended by the next
  private var waiting: Option[js.Dynamic] = None

  private def credentials: js.Dynamic = dom.window.navigator.asInstanceOf[js.Dynamic].credentials

  private def publicKeyCredential: js.Dynamic = dom.window.asInstanceOf[js.Dynamic].PublicKeyCredential

  def supported(): Boolean = dom.window.asInstanceOf[js.Dynamic].isSecureContext.asInstanceOf[Boolean] &&
    js.typeOf(publicKeyCredential) == "function" && !js.isUndefined(credentials)

  def create(options: String, done: PasskeyResult => Unit): Unit =
    ask(options, done) { json =>
      js.Dynamic.literal(publicKey = parsed(json, "parseCreationOptionsFromJSON", decodeCreation))
    }(credentials.create(_))

  def get(options: String, done: PasskeyResult => Unit): Unit =
    ask(options, done) { json =>
      js.Dynamic.literal(publicKey = parsed(json, "parseRequestOptionsFromJSON", decodeRequest))
    }(credentials.get(_))

  def autofillable(done: Boolean => Unit): Unit =
    if (!supported() || js.typeOf(publicKeyCredential.isConditionalMediationAvailable) != "function") done(false)
    else publicKeyCredential.isConditionalMediationAvailable().asInstanceOf[js.Promise[Boolean]]
      .`then`[Unit]((available: Boolean) => done(available))
      .`catch`[Unit]((_: Any) => done(false))

  def autofill(options: String, done: PasskeyResult => Unit): Unit =
    autofillable { available =>
      if (!available) done(PasskeyResult.PasskeyUnsupported)
      else ask(options, done) { json =>
        js.Dynamic.literal(publicKey = parsed(json, "parseRequestOptionsFromJSON", decodeRequest), mediation = "conditional")
      }(credentials.get(_))
    }

  /** The request made from `options` by `request` and started by `start`, answered once. The request waiting, if
    * any, is ended first: a page has one request at a time. */
  private def ask(options: String, done: PasskeyResult => Unit)
                 (request: js.Dynamic => js.Dynamic)(start: js.Dynamic => js.Any): Unit =
    if (!supported()) done(PasskeyResult.PasskeyUnsupported)
    else {
      waiting.foreach(w => w.abort())
      waiting = None
      val made = try Right(request(js.JSON.parse(options))) catch { case t: Throwable => Left(WebErrors.message(t)) }
      made match {
        case Left(why) => done(PasskeyResult.PasskeyFailed("Options not understood: " + why))
        case Right(req) =>
          val abort = js.Dynamic.newInstance(js.Dynamic.global.AbortController)()
          req.signal = abort.signal
          waiting = Some(abort)
          start(req).asInstanceOf[js.Promise[js.Dynamic]]
            .`then`[Unit] { (credential: js.Dynamic) =>
              if (waiting.exists(_ eq abort)) waiting = None
              done(if (credential == null) PasskeyResult.PasskeyCancelled else PasskeyResult.Credential(json(credential)))
            }
            .`catch`[Unit] { (e: Any) =>
              if (waiting.exists(_ eq abort)) waiting = None
              val kind = WebErrors.name(e)
              done(
                if (kind == "NotAllowedError" || kind == "AbortError") PasskeyResult.PasskeyCancelled
                else if (kind == "NotSupportedError") PasskeyResult.PasskeyUnsupported
                else PasskeyResult.PasskeyFailed(WebErrors.message(e)))
            }
      }
    }

  /** The options inside `json` (bare, or under "publicKey"), parsed by the browser's `parser` where it has one and
    * otherwise decoded here. */
  private def parsed(json: js.Dynamic, parser: String, decode: js.Dynamic => js.Dynamic): js.Dynamic = {
    val options = if (js.isUndefined(json.publicKey)) json else json.publicKey
    val own = publicKeyCredential.selectDynamic(parser)
    if (js.typeOf(own) == "function") publicKeyCredential.applyDynamic(parser)(options) else decode(options)
  }

  private def decodeCreation(o: js.Dynamic): js.Dynamic = {
    o.challenge = bytes(o.challenge)
    o.user.id = bytes(o.user.id)
    decodeIds(o, "excludeCredentials")
    o
  }

  private def decodeRequest(o: js.Dynamic): js.Dynamic = {
    o.challenge = bytes(o.challenge)
    decodeIds(o, "allowCredentials")
    o
  }

  private def decodeIds(o: js.Dynamic, list: String): Unit = {
    val ids = o.selectDynamic(list)
    if (!js.isUndefined(ids)) ids.asInstanceOf[js.Array[js.Dynamic]].foreach(c => c.id = bytes(c.id))
  }

  /** The credential as JSON: the browser's own `toJSON()`, or the same written out here. */
  private def json(credential: js.Dynamic): String =
    if (js.typeOf(credential.toJSON) == "function") js.JSON.stringify(credential.toJSON())
    else {
      val r = credential.response
      val response = js.Dynamic.literal(clientDataJSON = text(r.clientDataJSON))
      if (!js.isUndefined(r.attestationObject)) {
        response.attestationObject = text(r.attestationObject)
        response.transports = if (js.typeOf(r.getTransports) == "function") r.getTransports() else js.Array()
        if (js.typeOf(r.getAuthenticatorData) == "function") response.authenticatorData = text(r.getAuthenticatorData())
        if (js.typeOf(r.getPublicKeyAlgorithm) == "function") response.publicKeyAlgorithm = r.getPublicKeyAlgorithm()
        if (js.typeOf(r.getPublicKey) == "function") {
          val key = r.getPublicKey()
          if (key != null) response.publicKey = text(key)
        }
      }
      else {
        response.authenticatorData = text(r.authenticatorData)
        response.signature = text(r.signature)
        response.userHandle = if (r.userHandle == null || js.isUndefined(r.userHandle)) null else text(r.userHandle)
      }
      js.JSON.stringify(js.Dynamic.literal(id = credential.id, rawId = text(credential.rawId), `type` = credential.`type`,
        authenticatorAttachment = if (js.isUndefined(credential.authenticatorAttachment)) null else credential.authenticatorAttachment,
        response = response, clientExtensionResults = credential.getClientExtensionResults()))
    }

  /** base64url (padded or not) to the bytes it holds. */
  private def bytes(value: js.Dynamic): ArrayBuffer = {
    val base64 = value.asInstanceOf[String].replace('-', '+').replace('_', '/')
    val binary = dom.window.atob(base64 + "=" * ((4 - base64.length % 4) % 4))
    val out = new Uint8Array(binary.length)
    (0 until binary.length).foreach(i => out(i) = binary.charAt(i).toShort)
    out.buffer
  }

  /** Bytes (an ArrayBuffer) as unpadded base64url. */
  private def text(buffer: js.Dynamic): String = {
    val view = new Uint8Array(buffer.asInstanceOf[ArrayBuffer])
    val binary = new java.lang.StringBuilder
    (0 until view.length).foreach(i => binary.append(view(i).toChar))
    dom.window.btoa(binary.toString).replace('+', '-').replace('/', '_').reverse.dropWhile(_ == '=').reverse
  }
}

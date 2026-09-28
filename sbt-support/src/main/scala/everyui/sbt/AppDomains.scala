package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** The web domains an app is the same as, so a password saved on the site is offered in the app and one saved in the
  * app on the site, and a passkey made on either works on both: Android's Digital Asset Links and Apple's associated
  * domains, written from one place in the app's build.
  *
  * `androidPackage` and the SHA-256 fingerprints of the keys the app is signed with (the store's, not only the upload
  * key's: `keytool -list -v`, or Play's App signing page) name the Android app; `appleAppIds` name the Apple apps as
  * "TEAMID.bundle.id". `links` also claims the domains' links for the app. `changePassword`, `passkeyEnroll` and
  * `passkeyManage` are the site's pages for them, which password managers send a user to.
  *
  * {{{
  *   val domains = AppDomains(Seq("nabo.tv"), androidPackage = "tv.nabo.app",
  *     androidFingerprints = Seq("AB:CD:..."), appleAppIds = Seq("TEAMID.tv.nabo.apple"),
  *     changePassword = "https://nabo.tv/account/password")
  *   AppDomains.write(webOut, domains)                    // webOut/.well-known/, for the server (everyui.seo.WellKnown)
  *   IO.write(entitlements, domains.entitlements)          // the Apple app's associated domains
  *   IO.write(res / "values" / "asset_statements.xml", domains.assetStatementsXml)   // with androidManifestMeta
  * }}} */
case class AppDomains(domains: Seq[String],
                      androidPackage: String = "",
                      androidFingerprints: Seq[String] = Nil,
                      appleAppIds: Seq[String] = Nil,
                      links: Boolean = false,
                      changePassword: String = "",
                      passkeyEnroll: String = "",
                      passkeyManage: String = "") {
  private def q(s: String): String = "\"" + s.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case c => c.toString
  } + "\""
  private def list(xs: Seq[String]): String = xs.map(q).mkString("[", ", ", "]")

  /** `/.well-known/assetlinks.json`: the Android app shares the site's sign-ins (and, with `links`, its links). */
  def assetLinks: String = if (androidPackage.isEmpty) "[]\n" else {
    val relations = "delegate_permission/common.get_login_creds" +: (if (links) Seq("delegate_permission/common.handle_all_urls") else Nil)
    s"""[{
       |  "relation": ${list(relations)},
       |  "target": {"namespace": "android_app", "package_name": ${q(androidPackage)}, "sha256_cert_fingerprints": ${list(androidFingerprints)}}
       |}]
       |""".stripMargin
  }

  /** `/.well-known/apple-app-site-association`: the Apple apps share the site's sign-ins and passkeys (and, with
    * `links`, its links). */
  def appleAppSiteAssociation: String = {
    val applinks = if (links && appleAppIds.nonEmpty)
      s""",\n  "applinks": {"details": [${appleAppIds.map(id => s"""{"appIDs": [${q(id)}], "components": [{"/": "*"}]}""").mkString(", ")}]}"""
    else ""
    s"""{
       |  "webcredentials": {"apps": ${list(appleAppIds)}}$applinks
       |}
       |""".stripMargin
  }

  /** `/.well-known/passkey-endpoints`: where the site makes and manages passkeys, "" when it has no such pages. */
  def passkeyEndpoints: String =
    if (passkeyEnroll.isEmpty && passkeyManage.isEmpty) ""
    else s"""{"enroll": ${q(passkeyEnroll)}, "manage": ${q(passkeyManage)}}\n"""

  /** The Apple app's associated domains, as an entitlements plist (`com.apple.developer.associated-domains`). */
  def entitlements: String = {
    val entries = domains.flatMap(d => ("webcredentials:" + d) +: (if (links) Seq("applinks:" + d) else Nil))
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
       |<plist version="1.0">
       |<dict>
       |  <key>com.apple.developer.associated-domains</key>
       |  <array>
       |${entries.map(e => s"    <string>$e</string>").mkString("\n")}
       |  </array>
       |</dict>
       |</plist>
       |""".stripMargin
  }

  /** The Android app's side of the association: a string resource naming the sites whose asset links it trusts, which
    * `androidManifestMeta` points the manifest at. */
  def assetStatementsXml: String = {
    val statements = domains.map(d => s"""{\\"include\\": \\"https://$d/.well-known/assetlinks.json\\"}""").mkString(", ")
    s"""<?xml version="1.0" encoding="utf-8"?>
       |<resources>
       |  <string name="asset_statements" translatable="false">[$statements]</string>
       |</resources>
       |""".stripMargin
  }

  /** The `<meta-data>` the Android manifest's `<application>` carries for `assetStatementsXml`. */
  def androidManifestMeta: String =
    """<meta-data android:name="asset_statements" android:resource="@string/asset_statements" />"""
}

object AppDomains {
  /** The site's side: `out/.well-known/` gets assetlinks.json, apple-app-site-association, passkey-endpoints (when the
    * site has the pages) and change-password (the page's address, which the server redirects to), for the app's server
    * to answer with (`everyui.seo.WellKnown`). */
  def write(out: File, domains: AppDomains): Unit = {
    val dir = new File(out, ".well-known")
    dir.mkdirs()
    def file(name: String, content: String): Unit = {
      val f = new File(dir, name)
      if (content.isEmpty) f.delete() else Files.write(f.toPath, content.getBytes(UTF_8))
    }
    file("assetlinks.json", domains.assetLinks)
    file("apple-app-site-association", domains.appleAppSiteAssociation)
    file("passkey-endpoints", domains.passkeyEndpoints)
    file("change-password", domains.changePassword)
  }
}

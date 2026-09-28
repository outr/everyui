package everyui.seo

import everyui.sbt.AppDomains
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

/** An app's web domains as the build writes them (AppDomains) and the server answers them (WellKnown). */
class WellKnownSpec extends AnyWordSpec with Matchers {
  private val domains = AppDomains(Seq("example.com"), androidPackage = "com.example.app",
    androidFingerprints = Seq("AB:CD"), appleAppIds = Seq("TEAM123.com.example.app"),
    changePassword = "https://example.com/account/password", passkeyEnroll = "https://example.com/passkeys/new",
    passkeyManage = "https://example.com/passkeys")

  "An app's web domains" should {
    "be answered at their well-known addresses as the build wrote them" in {
      val root = Files.createTempDirectory("well-known")
      AppDomains.write(root.toFile, domains)
      val links = WellKnown.answer(root, "/.well-known/assetlinks.json").get
      links.contentType shouldBe "application/json"
      links.body should include("\"delegate_permission/common.get_login_creds\"")
      links.body should include("\"package_name\": \"com.example.app\"")
      links.body should include("\"sha256_cert_fingerprints\": [\"AB:CD\"]")
      WellKnown.answer(root, "/.well-known/apple-app-site-association").get.body should
        include("\"webcredentials\": {\"apps\": [\"TEAM123.com.example.app\"]}")
      WellKnown.answer(root, "/.well-known/passkey-endpoints").get.body shouldBe
        "{\"enroll\": \"https://example.com/passkeys/new\", \"manage\": \"https://example.com/passkeys\"}\n"
      WellKnown.answer(root, "/.well-known/change-password") shouldBe
        Some(WellKnownAnswer(302, "text/plain", "", "https://example.com/account/password"))
      WellKnown.answer(root, "/.well-known/other") shouldBe None
      WellKnown.answer(root, "/assetlinks.json") shouldBe None
    }
    "leave out what the site does not have" in {
      val root = Files.createTempDirectory("well-known")
      AppDomains.write(root.toFile, AppDomains(Seq("example.com"), appleAppIds = Seq("TEAM123.com.example.app")))
      WellKnown.answer(root, "/.well-known/passkey-endpoints") shouldBe None
      WellKnown.answer(root, "/.well-known/change-password") shouldBe None
      WellKnown.answer(root, "/.well-known/assetlinks.json").get.body shouldBe "[]\n"
    }
    "give the apps their side of it" in {
      val linked = domains.copy(links = true)
      linked.entitlements should include("<string>webcredentials:example.com</string>")
      linked.entitlements should include("<string>applinks:example.com</string>")
      linked.appleAppSiteAssociation should include("\"applinks\"")
      linked.assetLinks should include("delegate_permission/common.handle_all_urls")
      domains.assetStatementsXml should include("https://example.com/.well-known/assetlinks.json")
      domains.androidManifestMeta should include("@string/asset_statements")
    }
  }
}

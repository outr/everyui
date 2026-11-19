package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** A webOS 3.x TV reached over plain SSH (WebOSSsh, WebOSDevice), against a fake `ssh` that answers as the TV does:
  * the command lines it is given, the package sent through it, the install's states followed to the end,
  * `luna-send-pub` answering nothing where its input is at its end (as on the TV), the key decrypted into a copy of
  * mode 600 that is removed after, and the passphrase never said. */
class WebOSSshSpec extends AnyWordSpec with Matchers {
  // a fake ssh: each call's arguments in call-<n> (one a line), a package sent kept as sent.ipk, and luna-send-pub
  // answered from luna.txt (each line after a moment) only while its input is held open, as the TV's ends at the end
  // of its input saying nothing; with -i it then waits for its input to end
  private val fakeSsh =
    """#!/bin/bash
      |D=$(dirname "$0")
      |n=$(ls "$D" | grep -c '^call-'); printf '%s\n' "$@" > "$D/call-$n"
      |command="${@: -1}"
      |case "$command" in
      |  cat\ \>*) cat > "$D/sent.ipk" ;;
      |  rm\ *) ;;
      |  /usr/bin/luna-send-pub*)
      |    read -t 1 line; rc=$?
      |    if [ $rc -eq 1 ]; then echo eof > "$D/eof-$n"; exit 0; fi
      |    while IFS= read -r l; do echo "$l"; sleep 0.05; done < "$D/luna.txt"
      |    case "$command" in *" -i "*) cat > /dev/null ;; esac ;;
      |esac
      |""".stripMargin
  // a fake ssh-keygen: its arguments, and the copy's mode, then the copy "decrypted"
  private val fakeKeygen =
    """#!/bin/bash
      |D=$(dirname "$0")
      |f="${@: -1}"
      |printf '%s\n' "$@" > "$D/keygen"; stat -c %a "$f" > "$D/keymode"; echo decrypted > "$f"
      |""".stripMargin

  private def fixture(luna: String): (File, WebOSSsh) = {
    val dir = Files.createTempDirectory("webos-ssh-").toFile
    def script(name: String, text: String): String = {
      val f = new File(dir, name)
      Files.write(f.toPath, text.getBytes(UTF_8))
      f.setExecutable(true)
      f.getAbsolutePath
    }
    val ssh = script("ssh", fakeSsh)
    val keygen = script("ssh-keygen", fakeKeygen)
    Files.write(new File(dir, "luna.txt").toPath, (luna + "\n").getBytes(UTF_8))
    val key = new File(dir, "tv_webos")
    Files.write(key.toPath, "-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\n".getBytes(UTF_8))
    val device = WebOSDevice("192.168.1.118", 9922, "prisoner", key, Some("S3CR3T"))
    (dir, WebOSSsh(device, new File(dir, "scratch"), ssh, keygen, seconds = 5))
  }
  private def calls(dir: File): List[List[String]] =
    dir.listFiles().map(_.getName).filter(_.startsWith("call-")).sortBy(_.drop(5).toInt).toList
      .map(n => new String(Files.readAllBytes(new File(dir, n).toPath), UTF_8).linesIterator.toList)
  private val options: List[String] = List("-T", "-o", "BatchMode=yes", "-o", "LogLevel=ERROR",
    "-o", "StrictHostKeyChecking=accept-new", "-o", "HostKeyAlgorithms=+ssh-rsa",
    "-o", "PubkeyAcceptedAlgorithms=+ssh-rsa")

  private val states = List("installing : start", "installing : installing", "installing : service installing",
    "installing : service installing", "installing : service installing", "installed")
    .map(s => s"""{"id":"com.example.app","statusValue":30,"details":{"packageId":"com.example.app","state":"$s"},""" +
      """"subscribed":true}""")

  "install" should {
    "send the package through ssh, install it by luna-send-pub with its input held open, and follow it to the end" in {
      val (dir, tv) = fixture(("""{"returnValue":true,"subscribed":true}""" :: states).mkString("\n"))
      val ipk = new File(dir, "com.example.app_1.0.0_all.ipk")
      Files.write(ipk.toPath, Array[Byte](1, 2, 3, 4))
      var said = List.empty[String]
      tv.install(l => said :+= l, ipk, "com.example.app")
      val all = calls(dir)
      all.map(_.last) shouldBe List(
        "cat > '/media/developer/temp/com.example.app_1.0.0_all.ipk'",
        "/usr/bin/luna-send-pub -i luna://com.webos.appInstallService/dev/install " +
          "'{\"id\":\"com.example.app\",\"ipkUrl\":\"/media/developer/temp/com.example.app_1.0.0_all.ipk\"," +
          "\"subscribe\":true}'",
        "rm -f '/media/developer/temp/com.example.app_1.0.0_all.ipk'")
      all.foreach { c =>
        c.take(11) shouldBe options
        c(11) shouldBe "-i"
        c(12) should startWith(new File(dir, "scratch").getAbsolutePath + "/webos-key-")
        c.slice(13, 16) shouldBe List("-p", "9922", "prisoner@192.168.1.118")
      }
      Files.readAllBytes(new File(dir, "sent.ipk").toPath).toList shouldBe List[Byte](1, 2, 3, 4)
      dir.listFiles().exists(_.getName.startsWith("eof-")) shouldBe false
      said should contain allOf ("webOS: installing : start", "webOS: installed", "webOS: com.example.app installed")
    }
    "decrypt the key into a copy of its own, mode 600, removed after, and never say the passphrase" in {
      val (dir, tv) = fixture(states.mkString("\n"))
      val ipk = new File(dir, "app.ipk")
      Files.write(ipk.toPath, Array[Byte](9))
      var said = List.empty[String]
      tv.install(l => said :+= l, ipk, "com.example.app")
      val keygen = new String(Files.readAllBytes(new File(dir, "keygen").toPath), UTF_8).linesIterator.toList
      keygen.take(5) shouldBe List("-p", "-P", "S3CR3T", "-N", "")
      keygen(5) shouldBe "-f"
      new String(Files.readAllBytes(new File(dir, "keymode").toPath), UTF_8).trim shouldBe "600"
      new File(dir, "scratch").listFiles() shouldBe empty
      new String(Files.readAllBytes(new File(dir, "tv_webos").toPath), UTF_8) should include("ENCRYPTED")
      said.exists(_.contains("S3CR3T")) shouldBe false
      tv.device.toString should not include "S3CR3T"
    }
    "fail where the TV says the install failed" in {
      val (dir, tv) = fixture(states.head + "\n" +
        """{"returnValue":false,"errorCode":-1,"errorText":"FAILED_IPKG_INSTALL","subscribed":false}""")
      val ipk = new File(dir, "app.ipk")
      Files.write(ipk.toPath, Array[Byte](9))
      the[RuntimeException] thrownBy tv.install(_ => (), ipk, "com.example.app") should have message
        "webOS: com.example.app was not installed: FAILED_IPKG_INSTALL"
      calls(dir).last.last shouldBe "rm -f '/media/developer/temp/app.ipk'"
    }
  }

  "launch and close" should {
    "launch by the Application Manager with the parameters, one answer read" in {
      val (dir, tv) = fixture("""{"returnValue":true}""")
      var said = List.empty[String]
      tv.launch(l => said :+= l, "com.example.app", WebOSCli.params(Seq("open=/title/12")))
      calls(dir).map(_.last) shouldBe List("/usr/bin/luna-send-pub -n 1 luna://com.webos.applicationManager/launch " +
        "'{\"id\":\"com.example.app\",\"params\":{\"open\": \"/title/12\"}}'")
      said.last shouldBe "webOS: launched com.example.app"
    }
    "close by the developer's closeByAppId, which webOS 3 has" in {
      val (dir, tv) = fixture("""{"returnValue":true}""")
      tv.close(_ => (), "com.example.app")
      calls(dir).map(_.last) shouldBe List("/usr/bin/luna-send-pub -n 1 " +
        "luna://com.webos.applicationManager/dev/closeByAppId '{\"id\":\"com.example.app\"}'")
    }
    "fail where the TV refuses" in {
      val (_, tv) = fixture("""{"returnValue":false,"errorCode":-101,"errorText":"Unknown method"}""")
      the[RuntimeException] thrownBy tv.launch(_ => (), "com.example.app") should have message
        ("webOS: not launched com.example.app: the TV said " +
          """{"returnValue":false,"errorCode":-101,"errorText":"Unknown method"}""")
    }
  }

  "state" should {
    "read the install's states, its failures, and when it is over" in {
      WebOSSsh.state(states.head) shouldBe Some(Right("installing : start"))
      WebOSSsh.state("""{"returnValue":true,"subscribed":true}""") shouldBe None
      WebOSSsh.state("""{"details":{"state":"install failed","errorCode":-5}}""") shouldBe Some(Left("install failed"))
      WebOSSsh.state("""{"returnValue": false, "errorText": "no space"}""") shouldBe Some(Left("no space"))
      WebOSSsh.finished(states(2)) shouldBe None
      WebOSSsh.finished(states.last) shouldBe Some(Right("installed"))
    }
    "quote a word for the TV's shell" in {
      WebOSSsh.quote("""{"a":"it's"}""") shouldBe """'{"a":"it'\''s"}'"""
    }
  }

  "WebOSDevice.ares" should {
    "read a device from LG's CLI's list, its key where --getkey put it" in {
      val home = Files.createTempDirectory("webos-home-").toFile
      new File(home, ".webos/tv").mkdirs()
      Files.write(new File(home, ".webos/tv/novacom-devices.json").toPath,
        ("""[{"order":"0","name":"emulator","host":"127.0.0.1","port":6622,"username":"developer",""" +
          """"privateKey":{"openSsh":"webos_emul"}},""" +
          """{"host":"192.168.1.118","port":9922,"username":"prisoner","name":"lgtv","profile":"tv",""" +
          """"default":false,"password":"","passphrase":"ABC123"}]""").getBytes(UTF_8))
      WebOSDevice.ares("lgtv", home) shouldBe
        WebOSDevice("192.168.1.118", 9922, "prisoner", new File(home, ".ssh/lgtv_webos"), Some("ABC123"))
      WebOSDevice.ares("emulator", home) shouldBe
        WebOSDevice("127.0.0.1", 6622, "developer", new File(home, ".ssh/webos_emul"), None)
      an[RuntimeException] should be thrownBy WebOSDevice.ares("missing", home)
    }
  }
}

package everyui.access

import everyui.component.*
import everyui.component.types.{Live, SemanticRange}
import everyui.i18n.Words
import everyui.kit.{Spinner, Toast}
import everyui.theme.Theme
import everyui.{FxSpec, ui}

/** What a part of the screen says is read out as it comes and as it changes, politely or at once, held while it is
  * hidden or being updated; a component's wrong value, its being updated and its being out of use reach whatever reads
  * the screen; and EveryUI's own notices, toasts, page spinner and progress bars say what they are. */
class LiveRegionSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private var heard = List.empty[Announcement]
  private lazy val listening = LiveRegions.announced.attach(a => heard = heard :+ a)

  // what was read out while `test` ran and the screen settled after it
  private def said(test: => Unit): List[Announcement] = {
    listening
    settle()
    heard = Nil
    test
    settle()
    heard
  }

  // a few turns of the FX thread: the layout pass after a change, and the look at what the live parts say after that
  private def settle(): Unit = (1 to 6).foreach { _ =>
    fx(())
    Thread.sleep(5L)
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      everyui.focus.Focus.focused @= None
    }
  }

  "A live part" should {
    "be read out as it comes with something to say, and again only as that changes" in {
      val words = fx(TextView("3 results"))
      val status = fx {
        val c = new Container
        c.size.width @= 200.0
        c.size.height @= 20.0
        c.children += words
        c.liveRegion @= Live.Polite
        c
      }
      said(fx(ui.children ++= List(status))) shouldBe List(Announcement("3 results", Live.Polite))
      said(fx(words.content @= "5 results")) shouldBe List(Announcement("5 results", Live.Polite))
      said(fx(words.content @= "5 results")) shouldBe Nil
      fx(ui.children.clear())
    }
    "say nothing while hidden, and be read again as it is shown" in {
      val status = fx(TextView("Saved"))
      fx(status.liveRegion @= Live.Polite)
      onScreen(status) {
        settle()
        said(fx(status.visible @= false)) shouldBe Nil
        said(fx(status.content @= "Saved again")) shouldBe Nil
        said(fx(status.visible @= true)) shouldBe List(Announcement("Saved again", Live.Polite))
      }
    }
    "hold what it says while it is being updated" in {
      val status = fx(TextView("Loading"))
      fx(status.liveRegion @= Live.Polite)
      onScreen(status) {
        settle()
        said(fx {
          status.updating @= true
          status.content @= "12 of 40"
        }) shouldBe Nil
        said(fx(status.content @= "40 files")) shouldBe Nil
        said(fx(status.updating @= false)) shouldBe List(Announcement("40 files", Live.Polite))
      }
    }
    "cut in where it is assertive" in {
      val error = fx(TextView(""))
      fx(error.liveRegion @= Live.Assertive)
      onScreen(error) {
        said(fx(error.content @= "Connection lost")) shouldBe List(Announcement("Connection lost", Live.Assertive))
      }
    }
    "be read out no longer once it is off, as a plain part never is" in {
      val (status, plain) = fx((TextView("One"), TextView("Two")))
      fx(status.liveRegion @= Live.Polite)
      onScreen(status, plain) {
        settle()
        said(fx(plain.content @= "Three")) shouldBe Nil
        fx(status.liveRegion @= Live.Off)
        said(fx(status.content @= "Four")) shouldBe Nil
      }
    }
  }

  "A wrong value" should {
    "be said by its description, and read out at once only while the focus is in it" in {
      val (field, other) = fx {
        val f = new Container
        f.size.width @= 100.0
        f.size.height @= 40.0
        f.focusable @= true
        f.accessibleDescription @= "Your work address"
        val o = new Container
        o.position.top @= 60.0
        o.size.width @= 100.0
        o.size.height @= 40.0
        o.focusable @= true
        (f, o)
      }
      onScreen(field, other) {
        fx(other.focus())
        said(fx(field.invalid @= "Enter an email address")) shouldBe Nil
        fx(field.node.fx.getAccessibleHelp) shouldBe "Your work address. Enter an email address"
        fx(field.focus())
        said(fx(field.invalid @= "Enter a whole address")) shouldBe
          List(Announcement("Enter a whole address", Live.Assertive))
        fx(field.invalid @= "")
        fx(field.node.fx.getAccessibleHelp) shouldBe "Your work address"
      }
    }
    "mark a text field's edge and label, and reach its own field" in {
      val input = fx(new TextInput("Email"))
      onScreen(input) {
        fx(input.invalid @= "Enter an email address")
        fx(input.borderColor()) shouldBe fx(Theme.danger())
        fx(input.field.get.node.fx.getAccessibleHelp) shouldBe "Enter an email address"
        fx(input.invalid @= "")
        fx(input.borderColor()) should not be fx(Theme.danger())
        fx(input.field.get.node.fx.getAccessibleHelp) shouldBe null
      }
    }
  }

  "Being updated and out of use" should {
    "be said in the desktop's help text" in {
      val c = fx {
        val c = new Container
        c.size.width @= 50.0
        c.size.height @= 50.0
        c
      }
      onScreen(c) {
        fx(c.updating @= true)
        fx(c.node.fx.getAccessibleHelp) shouldBe Words.busy
        fx(c.disabled @= true)
        fx(c.node.fx.getAccessibleHelp) shouldBe Words.unavailable + ". " + Words.busy
        fx {
          c.updating @= false
          c.disabled @= false
        }
        fx(c.node.fx.getAccessibleHelp) shouldBe null
      }
    }
  }

  "EveryUI's own parts" should {
    "read out a notice as it comes, a toast as it shows, and a page's spinner" in {
      said(fx(ui.children += new Notice("Couldn't save the file", Theme.danger(), 300.0))) shouldBe
        List(Announcement("Couldn't save the file", Live.Polite))
      fx(ui.children.clear())
      said(fx(Toast.show("Copied"))) shouldBe List(Announcement("Copied", Live.Polite))
      fx(Toast.hide())
      said(fx(ui.children += Spinner.centred(200.0))) shouldBe List(Announcement(Words.loading, Live.Polite))
      fx(ui.children.clear())
    }
    "say how far a progress bar is" in {
      val bar = fx(ProgressBar(0.426, 200.0))
      fx(bar.semantics().role) shouldBe "progressbar"
      fx(bar.semantics().range) shouldBe Some(SemanticRange(43.0, 0.0, 100.0, "43%"))
      fx(bar.fraction @= 1.0)
      fx(bar.semantics().range.map(_.text)) shouldBe Some("100%")
    }
  }
}

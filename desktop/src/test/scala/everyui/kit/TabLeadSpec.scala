package everyui.kit

import everyui.chat.{Avatar, Participant}
import everyui.component.{Component, ImageView, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.{FxSpec, LeakPaths, ui}
import reactify.*

import java.io.File
import java.lang.ref.WeakReference

/** A tab's lead changed after the tab is made (`TabItem.lead`): a person renamed or given a portrait, and an icon for
  * another, shown in the view they had, the tab as wide and its label where it was; another kind made afresh and the
  * one it replaces taken apart, the tab widening or narrowing by the difference; the busy ring carried to an avatar
  * made while busy; nothing kept of the views it has had after many changes; and the same through `TabGroups.tab`. */
class TabLeadSpec extends FxSpec {
  private val helper = Participant("agent-1", "Robot Analyst")

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    super.afterAll()
  }

  /** A bar with an Overview tab before `lead`'s, on the screen. */
  private def barWith(lead: TabLead): TabItem = fx {
    ui.children.clear()
    val bar = new TabBar(Var("plan"), 900.0, showOnFocus = false)
    bar.add("overview", "Overview")
    val tab = bar.add("plan", "Release plan", closable = true, lead = lead)
    ui.children += bar
    tab
  }

  private def row(tab: TabItem): Component = tab.childComponents.head
  private def label(tab: TabItem): TextView = row(tab).childComponents.collectFirst {
    case t: TextView if !t.isInstanceOf[MaterialIconView] => t
  }.get
  private def avatar(tab: TabItem): Avatar = fx(tab.leadView.get.childComponents.head.asInstanceOf[Avatar])
  /** The tab's width and its label's place, once laid out. */
  private def placed(tab: TabItem): (Double, Double) = {
    fx(())
    fx((tab.size.width(), label(tab).position.left()))
  }

  /** A square picture of one colour, as a file's URL. */
  private def portrait(name: String, rgb: Int): String = {
    val dir = new File("target/tablead")
    dir.mkdirs()
    val file = new File(dir, name + ".png")
    val image = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB)
    (0 until 40).foreach(x => (0 until 40).foreach(y => image.setRGB(x, y, rgb)))
    javax.imageio.ImageIO.write(image, "png", file)
    "file://" + file.getAbsolutePath
  }
  private def picture(face: Avatar): Option[String] = face.childComponents.collectFirst {
    case p: ImageView if p.visible() => p.image().source
  }

  "A tab's lead" should {
    "show a person renamed in the avatar it had, the tab as wide and its label where it was" in {
      val tab = barWith(TabLead.Person(helper))
      val view = fx(tab.leadView.get)
      val face = avatar(tab)
      fx(face.kind) shouldBe "Avatar(Robot Analyst)"
      val before = placed(tab)
      fx(tab.lead @= TabLead.Person(helper.copy(name = "Helix")))
      fx(tab.leadView.get) should be theSameInstanceAs view
      fx(face.kind) shouldBe "Avatar(Helix)"
      fx(face.childComponents.collectFirst { case t: TextView => t.content() }) shouldBe Some("H")
      fx(face.backgroundColor()) shouldBe helper.colour
      placed(tab) shouldBe before
      // somebody else altogether: their letter and their own colour
      val ada = Participant("p-ada", "Ada")
      fx(tab.lead @= TabLead.Person(ada))
      fx(face.kind) shouldBe "Avatar(Ada)"
      fx(face.backgroundColor()) shouldBe ada.colour
      placed(tab) shouldBe before
    }

    "show a person's new portrait, and another after it, and their letter again without one" in {
      val (red, blue) = (portrait("red", 0xcc2222), portrait("blue", 0x2244cc))
      val tab = barWith(TabLead.Person(helper))
      val face = avatar(tab)
      val before = placed(tab)
      fx(picture(face)) shouldBe None
      fx(tab.lead @= TabLead.Person(helper.copy(avatar = Some(red))))
      eventually(picture(face).contains(red)) shouldBe true
      fx(tab.lead @= TabLead.Person(helper.copy(name = "Helix", avatar = Some(blue))))
      eventually(picture(face).contains(blue)) shouldBe true
      fx(face.kind) shouldBe "Avatar(Helix)"
      placed(tab) shouldBe before
      fx(tab.lead @= TabLead.Person(helper.copy(name = "Helix")))
      fx(picture(face)) shouldBe None
      avatar(tab) should be theSameInstanceAs face
      placed(tab) shouldBe before
    }

    "show another icon in the icon it had, the tab as wide" in {
      val tab = barWith(TabLead.Icon(MaterialIcon.Description))
      val view = fx(tab.leadView.get.asInstanceOf[MaterialIconView])
      val before = placed(tab)
      fx(tab.lead @= TabLead.Icon(MaterialIcon.Terminal))
      fx(tab.leadView.get) should be theSameInstanceAs view
      fx(view.icon()) shouldBe MaterialIcon.Terminal
      placed(tab) shouldBe before
    }

    "make a lead of another kind afresh, take apart the one it replaces, and widen or narrow by the difference" in {
      val tab = barWith(TabLead.None)
      val (none, noneAt) = placed(tab)
      fx(tab.leadView) shouldBe None
      noneAt shouldBe 0.0
      // none to a person: the avatar's room and the gap after it
      fx(tab.lead @= TabLead.Person(helper))
      val room = TabItem.face + 2.0 * BusyRing.outset(TabItem.face)
      val (person, personAt) = placed(tab)
      personAt shouldBe room + 6.0
      person shouldBe none + room + 6.0
      val face = fx(tab.leadView.get)
      fx(row(tab).childComponents.head) should be theSameInstanceAs face
      // a person to an icon: the avatar taken apart, the icon in its place, the tab narrower by the difference
      fx(tab.lead @= TabLead.Icon(MaterialIcon.Description))
      val icon = fx(tab.leadView.get)
      icon shouldBe a[MaterialIconView]
      fx(face.takenApart) shouldBe true
      fx(face.parent()) shouldBe None
      val iconWidth = fx(icon.size.width())
      val (iconTab, iconAt) = placed(tab)
      iconAt shouldBe iconWidth + 6.0
      iconTab shouldBe person - room + iconWidth
      // and back to none: as it was at first
      fx(tab.lead @= TabLead.None)
      fx(tab.leadView) shouldBe None
      fx(icon.takenApart) shouldBe true
      placed(tab) shouldBe ((none, noneAt))
    }

    "carry a busy tab's ring to an avatar made while it is busy" in {
      val tab = barWith(TabLead.Icon(MaterialIcon.Description))
      fx {
        tab.busyDetail @= "Running bash"
        tab.busy @= true
      }
      fx(tab.lead @= TabLead.Person(helper))
      val face = avatar(tab)
      fx(face.busyRing.exists(_.visible())) shouldBe true
      fx(face.tooltip()) shouldBe "Running bash"
      fx(tab.lead @= TabLead.Person(helper.copy(name = "Helix")))
      fx(face.busyRing.exists(_.visible())) shouldBe true
      fx(tab.busy @= false)
      fx(face.busyRing.exists(_.visible())) shouldBe false
    }

    "keep nothing of the views it has had after many changes" in {
      val tab = barWith(TabLead.Person(helper))
      var views = 0
      val held = fx {
        (0 until 120).toList.flatMap { i =>
          val was = tab.leadView
          tab.lead @= (i % 4 match {
            case 0 => TabLead.Icon(if (i % 8 == 0) MaterialIcon.Description else MaterialIcon.Terminal)
            case 1 => TabLead.None
            case 2 => TabLead.Person(helper.copy(name = "Helper " + i.toString))
            case _ => TabLead.Person(helper.copy(name = "Helix " + i.toString))
          })
          if (tab.leadView.exists(v => !was.exists(_ eq v))) views += 1
          tab.leadView.toList.map(v => new WeakReference(v))
        }
      }
      val last = fx(tab.leadView.get)
      val now = avatar(tab)
      fx(now.kind) shouldBe "Avatar(Helix 119)"
      // a person for a person kept its avatar: one view per change of kind
      views shouldBe 60
      def count = held.map(_.get()).distinct.count(v => v != null && !(v eq last))
      val until = System.currentTimeMillis() + 5000L
      while (count > 0 && System.currentTimeMillis() < until) {
        fx(())
        System.gc()
        Thread.sleep(50L)
      }
      if (count > 0) LeakPaths.find(held.flatMap(r => Option(r.get())).filterNot(_ eq last))
        .foreach(p => info("held: " + p))
      count shouldBe 0
      // it, and the ×
      fx(row(tab).childComponents.count(c => c.isInstanceOf[MaterialIconView] || (c eq last))) shouldBe 2
    }
  }

  "A tab group's tab" should {
    "have its lead changed through TabGroups.tab" in {
      var name = "Robot Analyst"
      val groups = fx {
        ui.children.clear()
        val made = new TabGroups[String](label = k => k, content = _ => new everyui.component.Container, id = identity,
          lead = _ => TabLead.Person(helper.copy(name = name)))
        made.size.width @= 900.0
        made.size.height @= 500.0
        ui.children += made
        made.open("chat")
        made
      }
      val tab = fx(groups.tab("chat").head)
      val face = avatar(tab)
      val before = placed(tab)
      fx(face.kind) shouldBe "Avatar(Robot Analyst)"
      name = "Helix"
      fx(groups.tab("chat").foreach(_.lead @= groups.lead("chat")))
      fx(face.kind) shouldBe "Avatar(Helix)"
      placed(tab) shouldBe before
      fx(ui.children.clear())
    }
  }
}

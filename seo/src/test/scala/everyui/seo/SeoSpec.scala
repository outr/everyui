package everyui.seo

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SeoSpec extends AnyWordSpec with Matchers {
  private val absolute: String => String = p => if (p.contains("://")) p else "https://example.com" + p

  private val post = Doc.fragment(Doc.header(Doc.paragraph("Site header")), Doc.main(
    Doc.navigation("Site", Doc.link("/", "Home"), Doc.link("/blog/", "Blog")),
    Doc.article(
      Doc.heading(1, "Tags & <Things>"),
      Doc.paragraph(Doc.text("Read "), Doc.link("/about/", "about me"), Doc.text(" or "), Doc.strong("not")),
      Doc.image("/uploads/a.webp", "A \"quoted\" picture", 640, 360),
      Doc.list(Doc.text("one"), Doc.link("https://other.org/", "two")),
      Doc.trusted("<p>Body <b>html</b></p>", "Body **html**"))), Doc.footer(Doc.paragraph("Site footer")))

  "DocHtml" should {
    "write semantic elements with their words escaped" in {
      val html = DocHtml.render(post)
      html should include("<main><nav aria-label=\"Site\"><a href=\"/\">Home</a>")
      html should include("<h1>Tags &amp; &lt;Things&gt;</h1>")
      html should include("<p>Read <a href=\"/about/\">about me</a> or <strong>not</strong></p>")
      html should include("<img src=\"/uploads/a.webp\" alt=\"A &quot;quoted&quot; picture\" width=\"640\" height=\"360\">")
      html should not include "</img>"
      html should include("<ul><li>one</li><li><a href=\"https://other.org/\">two</a></li></ul>")
      html should include("<div><p>Body <b>html</b></p></div>")
    }
    "put the snapshot in the element the app removes" in {
      DocHtml.snapshot(Doc.paragraph("x")) shouldBe "<div id=\"everyui-snapshot\"><p>x</p></div>"
    }
  }

  "DocMarkdown" should {
    "write the content without the page around it, links made whole" in {
      DocMarkdown.render(post, absolute) shouldBe
        """# Tags & <Things>
          |
          |Read [about me](https://example.com/about/) or **not**
          |
          |![A "quoted" picture](https://example.com/uploads/a.webp)
          |
          |- one
          |- [two](https://other.org/)
          |
          |Body **html**
          |""".stripMargin
    }
    "fall back to the words of markup that has no source" in {
      DocMarkdown.render(Doc.trusted("<p>One &amp; two</p><p>Three</p>")) shouldBe "One & two\n\nThree\n"
    }
  }

  "PageHead" should {
    "write every tag the app later replaces, and escape what goes in them" in {
      val head = PageHead.render(PageMeta("A \"title\" & more", "Says <this>", canonical = "/post/", image = "/i.png",
        kind = "article", siteName = "Site", jsonLd = List(JsonLd.person("Me", "https://example.com/")),
        alternates = List(Alternate("/post/index.md", "text/markdown", "Markdown"))), absolute)
      head should startWith("<title>A \"title\" &amp; more</title>")
      head should include("<meta name=\"description\" content=\"Says &lt;this&gt;\" data-everyui-meta>")
      head should include("<link rel=\"canonical\" href=\"https://example.com/post/\" data-everyui-meta>")
      head should include("<meta property=\"og:image\" content=\"https://example.com/i.png\" data-everyui-meta>")
      head should include("<meta name=\"twitter:card\" content=\"summary_large_image\" data-everyui-meta>")
      head should include("<link rel=\"alternate\" type=\"text/markdown\" href=\"/post/index.md\" title=\"Markdown\" data-everyui-meta>")
      head should include("<script type=\"application/ld+json\" data-everyui-meta>{\"@context\":\"https://schema.org\",\"@type\":\"Person\"")
      head should not include "robots"
    }
    "keep a page out of search results when asked" in {
      PageHead.render(PageMeta("Admin", noindex = true), absolute) should include("<meta name=\"robots\" content=\"noindex\" data-everyui-meta>")
    }
  }

  "JsonLd" should {
    "leave out what is empty, and nest what is nested" in {
      JsonLd.article("Hi \"there\"", "https://example.com/p/", "Me", "2026-09-24") shouldBe
        "{\"@context\":\"https://schema.org\",\"@type\":\"BlogPosting\",\"headline\":\"Hi \\\"there\\\"\"," +
          "\"url\":\"https://example.com/p/\",\"mainEntityOfPage\":\"https://example.com/p/\"," +
          "\"datePublished\":\"2026-09-24\",\"dateModified\":\"2026-09-24\",\"author\":{\"@type\":\"Person\",\"name\":\"Me\"}}"
    }
    "number the crumbs from one" in {
      JsonLd.breadcrumbs(List("Home" -> "https://example.com/", "Post" -> "https://example.com/p/")) should include(
        "{\"@type\":\"ListItem\",\"position\":2,\"name\":\"Post\",\"item\":\"https://example.com/p/\"}")
    }
    "give a search box its query" in {
      JsonLd.website("Site", "https://example.com/", "https://example.com/search?q={q}") should include(
        "\"target\":\"https://example.com/search?q={search_term_string}\"")
    }
  }

  "The site's indexes" should {
    val entries = List(
      IndexEntry("/", "Home", "What I do", "2026-09-24", "Pages"),
      IndexEntry("/p/", "A post", "About\n  things", "2026-09-20", "Posts", "/p/index.md"))

    "list every page in the sitemap" in {
      val xml = Sitemap.render(entries, absolute)
      xml should include("<url><loc>https://example.com/p/</loc><lastmod>2026-09-20</lastmod></url>")
    }
    "say outright what answer engines may read" in {
      val open = Robots.render("https://example.com/sitemap.xml", List("/admin"))
      open should include("User-agent: GPTBot\nAllow: /\nDisallow: /admin")
      open should endWith("Sitemap: https://example.com/sitemap.xml\n")
      Robots.render("https://example.com/sitemap.xml", answerEngines = false) should include("User-agent: ClaudeBot\nDisallow: /")
    }
    "give an answer engine the site by section, linking the Markdown forms" in {
      LlmsTxt.render("Site", "A site.", "", entries, absolute) shouldBe
        """# Site
          |
          |> A site.
          |
          |## Pages
          |
          |- [Home](https://example.com/): What I do
          |
          |## Posts
          |
          |- [A post](https://example.com/p/index.md): About things
          |""".stripMargin
    }
  }
}

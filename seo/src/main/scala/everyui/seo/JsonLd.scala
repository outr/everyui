package everyui.seo

import scala.collection.immutable.ListMap

/** Structured data (schema.org, as JSON-LD) for `SEO.jsonLd`: what search engines show as rich results and what
  * answer engines take as fact — who wrote a page, when, what it is part of, what it answers.
  *
  * A value is written as JSON by what it is: a `ListMap` an object (in its order; an entry whose value is `None`,
  * `""` or empty is left out), a `Seq` an array, an `Option` its value or nothing, a number, boolean or text as such. */
object JsonLd {
  private val context = "https://schema.org"

  /** A schema.org thing of `kind` with `fields`, as the JSON text `SEO.jsonLd` holds. */
  def apply(kind: String, fields: (String, Any)*): String = write(node(kind, fields*), top = true)

  /** The same, as a value to nest inside another. */
  def node(kind: String, fields: (String, Any)*): ListMap[String, Any] = ListMap(("@type" -> kind) +: fields*)

  /** The site as a whole; with `search`, a search box in results (`search` has `{q}` where the words go). */
  def website(name: String, url: String, search: String = ""): String =
    apply("WebSite", "name" -> name, "url" -> url, "potentialAction" -> Option.when(search.nonEmpty)(
      node("SearchAction", "target" -> search.replace("{q}", "{search_term_string}"),
        "query-input" -> "required name=search_term_string")))

  def person(name: String, url: String, sameAs: Seq[String] = Nil, jobTitle: String = "", image: String = ""): String =
    apply("Person", "name" -> name, "url" -> url, "sameAs" -> sameAs, "jobTitle" -> jobTitle, "image" -> image)

  /** A post or article; dates as ISO 8601. */
  def article(headline: String, url: String, author: String, published: String, modified: String = "",
              description: String = "", image: String = "", kind: String = "BlogPosting"): String =
    apply(kind, "headline" -> headline, "url" -> url, "mainEntityOfPage" -> url, "description" -> description,
      "image" -> image, "datePublished" -> published, "dateModified" -> (if (modified.nonEmpty) modified else published),
      "author" -> node("Person", "name" -> author))

  /** Where a page sits: each crumb its name and whole URL, from the top down. */
  def breadcrumbs(crumbs: Seq[(String, String)]): String =
    apply("BreadcrumbList", "itemListElement" -> crumbs.zipWithIndex.map { case ((name, url), i) =>
      node("ListItem", "position" -> (i + 1), "name" -> name, "item" -> url)
    })

  /** An application someone can use or get. */
  def software(name: String, url: String, description: String = "", category: String = "", os: String = "",
               image: String = ""): String =
    apply("SoftwareApplication", "name" -> name, "url" -> url, "description" -> description,
      "applicationCategory" -> category, "operatingSystem" -> os, "image" -> image)

  /** Questions and their answers, as an answer engine quotes them. */
  def faq(entries: Seq[(String, String)]): String =
    apply("FAQPage", "mainEntity" -> entries.map { case (q, a) =>
      node("Question", "name" -> q, "acceptedAnswer" -> node("Answer", "text" -> a))
    })

  private def write(value: Any, top: Boolean = false): String = value match {
    case m: ListMap[?, ?] =>
      val entries = (if (top) List("@context" -> context) else Nil) ++
        m.toList.map { case (k, v) => k.toString -> v }.filter { case (_, v) => present(v) }
      entries.map { case (k, v) => quote(k) + ":" + write(v) }.mkString("{", ",", "}")
    case Some(v) => write(v)
    case s: Seq[?] => s.filter(present).map(write(_)).mkString("[", ",", "]")
    case b: Boolean => b.toString
    case i: Int => i.toString
    case l: Long => l.toString
    case d: Double => if (d == math.rint(d) && math.abs(d) < 1e15) d.toLong.toString else d.toString
    case other => quote(other.toString)
  }

  private def present(v: Any): Boolean = v match {
    case None | null => false
    case s: String => s.nonEmpty
    case s: Seq[?] => s.nonEmpty
    case _ => true
  }

  private def quote(s: String): String = {
    val b = new StringBuilder("\"")
    s.foreach {
      case '"' => b.append("\\\"")
      case '\\' => b.append("\\\\")
      case '\n' => b.append("\\n")
      case '\r' => b.append("\\r")
      case '\t' => b.append("\\t")
      case c if c < ' ' => b.append("\\u%04x".format(c.toInt))
      case c => b.append(c)
    }
    b.append('"').toString
  }
}

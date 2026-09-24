package everyui.seo

/** `robots.txt`: what crawlers may fetch, and where the sitemap is. The answer engines' crawlers are named one by one
  * (`answerEngines`), so whether a site wants to be read and quoted by them is said outright rather than left to the
  * catch-all; with `false` they are turned away while search engines are not. */
object Robots {
  /** The crawlers that feed AI answers and assistants, as they name themselves. */
  val answerEngineAgents: List[String] = List("GPTBot", "OAI-SearchBot", "ChatGPT-User", "ClaudeBot", "Claude-User",
    "Claude-SearchBot", "PerplexityBot", "Perplexity-User", "Google-Extended", "Applebot-Extended", "CCBot",
    "meta-externalagent", "Amazonbot", "DuckAssistBot", "MistralAI-User")

  def render(sitemap: String, disallow: Seq[String] = Nil, answerEngines: Boolean = true): String = {
    val rules = disallow.map("Disallow: " + _)
    val all = ("User-agent: *" :: "Allow: /" :: rules.toList).mkString("\n")
    val engines = answerEngineAgents.map { agent =>
      ("User-agent: " + agent :: (if (answerEngines) "Allow: /" :: rules.toList else List("Disallow: /"))).mkString("\n")
    }
    (all :: engines).mkString("\n\n") + "\n\nSitemap: " + sitemap + "\n"
  }
}

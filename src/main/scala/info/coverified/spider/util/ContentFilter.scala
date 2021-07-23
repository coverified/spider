/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import crawlercommons.robots.BaseRobotRules
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.util.UrlCleaner.cleanUrl
import info.coverified.spider.util.UrlFilter.wantedUrl
import org.jsoup.Connection.Response
import org.apache.commons.validator.routines.UrlValidator
import org.jsoup.nodes.{Document, Element}

import java.net.URL
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object ContentFilter {

  private val urlValidator = new UrlValidator()
  private val txtHtml = "text/html"

  private[util] def contentFilter(
      response: Response,
      robotsCfg: BaseRobotRules
  ): Option[SiteContent] = {

    // only index if header response has not been filtered
    // by the header filter
    val contentType: String = response.contentType
    if (contentType.startsWith(txtHtml)) {
      extractContentInformation(response.parse(), robotsCfg)
    } else {
      // unsupported content is indexed, but does not contain any other urls
      Some(SiteContent(None, Set.empty))
    }
  }

  private def extractContentInformation(
      doc: Document,
      robotsCfg: BaseRobotRules
  ): Option[SiteContent] = {
    val canonicalLink = canonicalLinkFromHead(doc)
    val links: mutable.Seq[String] = extractAllHref(doc) ++ extractAbsLinks(doc)
    val cLinks: mutable.Seq[String] = extractCanonicalLinksFromBody(doc)
    val hRefLang: mutable.Seq[String] = extractHRefLang(doc)
    val newUrls =
      (links ++ cLinks ++ hRefLang)
        .filter(url => robotsCfg.isAllowed(url))
        .filter(wantedUrl)
        .filterNot(canonicalLink.contains(_)) // if canonical link is available from head, do not include it in the set
        .map(cleanUrl)
        .map(new URL(_))
        .toSet

    Some(
      SiteContent(
        canonicalLink.filter(url => robotsCfg.isAllowed(url)).map(new URL(_)),
        newUrls
      )
    )
  }

  def extractAbsLinks(doc: Document): mutable.Buffer[String] =
    doc
      .getElementsByTag("a")
      .asScala
      .map(_.absUrl("href"))
      .filter(urlValidator.isValid)

  def extractAllHref(doc: Document): mutable.Buffer[String] =
    doc
      .getElementsByAttribute("href")
      .asScala
      .map(_.absUrl("href"))
      .filter(urlValidator.isValid)
      .filter(_.endsWith(".html"))

  def extractHRefLang(doc: Document): mutable.Buffer[String] =
    doc
      .getElementsByTag("link")
      .asScala
      .filter(
        e =>
          e.attributes().hasDeclaredValueForKeyIgnoreCase("rel") && e
            .attributes()
            .hasDeclaredValueForKeyIgnoreCase("hreflang") && e
            .attributes()
            .hasDeclaredValueForKeyIgnoreCase("href")
      )
      .map(_.absUrl("href"))
      .filter(urlValidator.isValid)

  def extractCanonicalLinksFromBody(doc: Document): mutable.Buffer[String] =
    canonicalLinks(doc.body())

  def canonicalLinkFromHead(doc: Document): Option[String] =
    canonicalLinks(doc.head()).headOption

  private def canonicalLinks(e: Element): mutable.Buffer[String] =
    e.getElementsByTag("link")
      .asScala
      .filter(
        e =>
          e.attributes().hasDeclaredValueForKeyIgnoreCase("rel") && e
            .attributes()
            .hasDeclaredValueForKeyIgnoreCase("href")
      )
      .filter(e => e.attributes().get("rel").equals("canonical"))
      .map(_.absUrl("href"))
      .filter(urlValidator.isValid)

}

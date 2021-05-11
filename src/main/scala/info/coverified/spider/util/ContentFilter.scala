/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import info.coverified.spider.SiteScraper.SiteContent
import org.jsoup.Connection.Response
import org.apache.commons.validator.routines.UrlValidator
import org.jsoup.nodes.{Document, Element}

import java.net.URL
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object ContentFilter {

  private val urlValidator = new UrlValidator()
  private val txtHtml = "text/html"

  private[util] def contentFilter(response: Response): Option[SiteContent] = {

    // only index if header response has not been filtered
    // by the header filter
    val contentType: String = response.contentType
    if (contentType.startsWith(txtHtml)) {
      extractContentInformation(response, response.url)
    } else {
      // unsupported content is indexed, but does not contain any other urls
      Some(SiteContent(Set.empty))
    }
  }

  private def extractContentInformation(
      siteResponse: Response,
      url: URL
  ): Option[SiteContent] = {
    val doc = siteResponse.parse()
    if (addToIndex(doc, url)) {
      val links: Set[URL] = extractAbsLinks(doc)
      val cLinks: Set[URL] = extractCanonicalLinksFromBody(doc)
      val hRefLang: Set[URL] = extractHRefLang(doc)
      Some(SiteContent(links ++ cLinks ++ hRefLang))
    } else {
      None
    }

  }

  private def addToIndex(doc: Document, url: URL): Boolean = {
    // no canonical = true, canonical == url = true, canonical != url = false
    canonicalLinkFromHead(doc).forall(_.equals(url))
  }

  def extractAbsLinks(doc: Document): Set[URL] =
    doc
      .getElementsByTag("a")
      .asScala
      .map(_.absUrl("href"))
      .filter(urlValidator.isValid)
      .map(new URL(_))
      .toSet

  def extractHRefLang(doc: Document): Set[URL] =
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
      .map(new URL(_))
      .toSet

  def extractCanonicalLinksFromBody(doc: Document): Set[URL] =
    canonicalLinks(doc.body()).toSet

  def canonicalLinkFromHead(doc: Document): Option[URL] =
    canonicalLinks(doc.head()).headOption

  private def canonicalLinks(e: Element): mutable.Buffer[URL] =
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
      .map(new URL(_))

}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import info.coverified.spider.SiteScraper.SiteContent
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

  private[util] def contentFilter(response: Response): Option[SiteContent] = {

    // only index if header response has not been filtered
    // by the header filter
    val contentType: String = response.contentType
    if (contentType.startsWith(txtHtml)) {
      extractContentInformation(response.parse(), response.url)
    } else {
      // unsupported content is indexed, but does not contain any other urls
      Some(SiteContent(Set.empty))
    }
  }

  private def extractContentInformation(
      doc: Document,
      url: URL
  ): Option[SiteContent] = {
    if (addToIndex(doc, url)) {
      val links: mutable.Seq[String] = extractAbsLinks(doc)
      val cLinks: mutable.Seq[String] = extractCanonicalLinksFromBody(doc)
      val hRefLang: mutable.Seq[String] = extractHRefLang(doc)

      val newUrls =
        (links ++ cLinks ++ hRefLang).filter(wantedUrl).map(new URL(_)).toSet

      Some(SiteContent(newUrls))
    } else {
      None
    }
  }

  private def addToIndex(doc: Document, url: URL): Boolean = {
    // no canonical = true, canonical == url = true, canonical != url = false
    canonicalLinkFromHead(doc).forall(
      link => link.equals(url.toString) || link.equals(s"${url.toString}/")
    )
  }

  def extractAbsLinks(doc: Document): mutable.Buffer[String] =
    doc
      .getElementsByTag("a")
      .asScala
      .map(_.absUrl("href"))
      .filter(urlValidator.isValid)

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

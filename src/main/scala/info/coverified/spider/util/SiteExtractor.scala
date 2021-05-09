/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import org.apache.commons.validator.routines.UrlValidator
import org.jsoup.nodes.Document

import java.net.URL
import scala.jdk.CollectionConverters._

object SiteExtractor {

  private val urlValidator = new UrlValidator()

  def extractAbsLinks(doc: Document): Set[URL] =
    doc
      .getElementsByTag("a")
      .asScala
      .map(e => e.absUrl("href"))
      .filter(urlValidator.isValid)
      .map(new URL(_))
      .toSet

  def canonicalLinkFromHead(doc: Document): Option[URL] = {
    doc
      .head()
      .getElementsByTag("link")
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
      .headOption
  }

}

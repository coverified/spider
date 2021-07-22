/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import com.typesafe.scalalogging.LazyLogging
import crawlercommons.sitemaps.{
  SiteMap,
  SiteMapIndex,
  SiteMapParser,
  SiteMapURL
}
import io.sentry.Sentry

import java.net.URL
import scala.jdk.CollectionConverters.CollectionHasAsScala

object SitemapInspector extends LazyLogging {

  private val siteMapParser = new SiteMapParser(false)

  private val siteNameHostSuffix = Vector("sitemap.xml")

  def inspect(url: URL): Iterable[URL] =
    buildSitemapLinks(url).flatMap(
      sitemapLink =>
        siteMapParser.parseSiteMap(new URL(sitemapLink)) match {
          case map: SiteMap =>
            map.getSiteMapUrls.asScala.map(_.getUrl)
          case _: SiteMapIndex =>
            val errorString = "Multiple nested sitemaps are not supported yet!"
            logger.error(errorString)
            Sentry.captureMessage(errorString)
            Vector.empty
          case invalid =>
            val errorString = s"Invalid sitemap received: $invalid"
            logger.error(errorString)
            Sentry.captureMessage(s"Invalid sitemap received: $invalid")
            Vector.empty
        }
    )

  private def buildSitemapLinks(url: URL): Seq[String] =
    siteNameHostSuffix.map(url.getProtocol + "://" + url.getHost + "/" + _)

}

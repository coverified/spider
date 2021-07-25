/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import com.typesafe.scalalogging.LazyLogging
import crawlercommons.sitemaps.{SiteMap, SiteMapIndex, SiteMapParser}
import io.sentry.{Sentry, SentryLevel}

import java.io.FileNotFoundException
import java.net.URL
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try}

object SitemapInspector extends LazyLogging {

  private val siteMapParser = new SiteMapParser(false)
  private val siteNameHostSuffix = Vector("sitemap.xml")

  def inspectFromHost(hostUrl: URL): Iterable[URL] =
    sitemapLinks(hostUrl).flatMap(inspectSitemap)

  def inspectSitemaps(sitemapUrls: Vector[String]): Seq[URL] =
    sitemapUrls.flatMap(inspectSitemap)

  def inspectSitemap(sitemapUrl: String): Iterable[URL] =
    inspectSitemap(new URL(sitemapUrl))

  def inspectSitemap(sitemap: URL): Iterable[URL] =
    Try(siteMapParser.parseSiteMap(sitemap)) match {
      case Failure(_: FileNotFoundException) =>
        logger.info(
          s"Cannot parse sitemap '${sitemap.toString}'. File not found."
        )
        Sentry.captureMessage(
          s"Cannot parse sitemap '${sitemap.toString}'. File not found.",
          SentryLevel.INFO
        )
        Vector.empty
      case Failure(exception) =>
        logger.warn(s"Cannot parse sitemap '${sitemap.toString}'.", exception)
        Vector.empty
      case Success(siteMap: SiteMap) =>
        siteMap.getSiteMapUrls.asScala.map(_.getUrl)
      case Success(index: SiteMapIndex) =>
        index.getSitemaps.asScala
          .flatMap(sitemap => inspectSitemap(sitemap.getUrl))
      case Success(invalid) =>
        val errorString = s"Invalid sitemap received: $invalid"
        logger.error(errorString)
        Sentry.captureMessage(errorString, SentryLevel.ERROR)
        Vector.empty
    }

  private def sitemapLinks(url: URL): Seq[String] =
    siteNameHostSuffix.map(url.getProtocol + "://" + url.getHost + "/" + _)

}

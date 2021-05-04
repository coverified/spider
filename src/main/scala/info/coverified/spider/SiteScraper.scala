/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.HostCrawler.HostCrawlerEvent
import info.coverified.spider.Indexer.IndexerEvent
import org.apache.commons.validator.routines.UrlValidator
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.net.URL
import scala.jdk.CollectionConverters._

object SiteScraper extends LazyLogging {

  sealed trait SiteScraperEvent

  final case class Scrap(url: URL, sender: ActorRef[HostCrawlerEvent])
      extends SiteScraperEvent

  final case class SiteContent(links: Set[URL])

  private val userAgent =
    "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1"
  private val txtHtml = "text/html"
  private val urlValidator = new UrlValidator()

  def apply(indexer: ActorRef[IndexerEvent]): Behavior[SiteScraperEvent] =
    idle(indexer)

  private def idle(
      indexer: ActorRef[IndexerEvent]
  ): Behavior[SiteScraperEvent] = Behaviors.receiveMessage {
    case Scrap(url, sender) =>
      logger.debug(s"Scraping '$url' ...")
      val maybeContent = scrape(url)

      maybeContent match {
        case Some(siteContent) =>
          sender ! HostCrawler.SiteScraperSuccessful(url)
          indexer ! Indexer.Index(url, siteContent)
        case None =>
          sender ! HostCrawler.SiteScraperFailure(
            url,
            new IllegalArgumentException(
              s"Cannot extract content data from '$url'"
            )
          )
      }
      idle(indexer)
  }

  private def scrape(url: URL): Option[SiteContent] = {
    val link: String = url.toString
    val response = Jsoup
      .connect(link)
      .ignoreContentType(true)
      .userAgent(userAgent)
      .execute()

    val contentType: String = response.contentType
    Option.when(contentType.startsWith(txtHtml)) {
      val doc = response.parse()
      val links: Set[URL] = extractAbsLinks(doc)
      SiteContent(links)
    }
  }

  private def extractAbsLinks(doc: Document): Set[URL] =
    doc
      .getElementsByTag("a")
      .asScala
      .map(e => e.absUrl("href"))
      .filter(s => urlValidator.isValid(s))
      .map(link => new URL(link))
      .toSet

}

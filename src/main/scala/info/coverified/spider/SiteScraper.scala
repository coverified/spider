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
import info.coverified.spider.util.{SiteExtractor, UserAgentProvider}
import org.jsoup.Connection.Response
import org.jsoup.nodes.Document
import org.jsoup.{Jsoup, UnsupportedMimeTypeException}

import java.io.IOException
import java.net.{MalformedURLException, URL}

object SiteScraper extends LazyLogging {

  sealed trait SiteScraperEvent

  final case class Scrap(url: URL, sender: ActorRef[HostCrawlerEvent])
      extends SiteScraperEvent

  final case class SiteContent(links: Set[URL], addToIndex: Boolean = true)

  private val txtHtml = "text/html"

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
          sender ! HostCrawler.SiteScrapeSuccessful(url)
          indexer ! Indexer.Index(url, siteContent)
        case None =>
          sender ! HostCrawler.SiteScrapeFailure(
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
    try {
      val response = Jsoup
        .connect(link)
        .ignoreContentType(true)
        .userAgent(UserAgentProvider.randomUserAgent)
        .execute()

      val contentType: String = response.contentType
      Option.when(contentType.startsWith(txtHtml)) {
        extractContentInformation(response, url)
      }
    } catch {
      case _: UnsupportedMimeTypeException =>
        // unsupported mime types are not re-scheduled, but indexed
        Some(SiteContent(Set.empty))
      case _: MalformedURLException =>
        // malformed urls are not re-scheduled, but indexed
        Some(SiteContent(Set.empty))
      case _: IOException =>
        // everything else (e.g. timeout) will be re-scheduled and not indexed yet
        None
    }
  }

  private def extractContentInformation(
      siteResponse: Response,
      url: URL
  ): SiteContent = {
    val doc = siteResponse.parse()
    val toIndex = addToIndex(doc, url)
    val links: Set[URL] = SiteExtractor.extractAbsLinks(doc)
    SiteContent(links, toIndex)
  }

  private def addToIndex(doc: Document, url: URL): Boolean = {
    // no canonical = true, canonical != url = false, true otherwise
    SiteExtractor.canonicalLinkFromHead(doc).forall(_.equals(url))
  }

}

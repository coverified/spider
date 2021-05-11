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
import info.coverified.spider.exception.UnsupportedContentTypeException
import info.coverified.spider.util.{SiteExtractor, UserAgentProvider}
import org.jsoup.Connection.Response
import org.jsoup.nodes.Document
import org.jsoup.{HttpStatusException, Jsoup, UnsupportedMimeTypeException}

import java.io.IOException
import java.net.{MalformedURLException, URL}
import scala.util.{Failure, Success, Try}

// todo scrape timeout to jsoup from config

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
        case Success(siteContent) =>
          indexer ! Indexer.Index(url, siteContent)
        case Failure(exception) =>
          // report back to host crawler
          sender ! HostCrawler.SiteScrapeFailure(
            url,
            exception
          )
      }
      idle(indexer)
  }

  private def scrape(url: URL): Try[SiteContent] = {
    val link: String = url.toString
    try {
      val response = Jsoup
        .connect(link)
        .followRedirects(true)
        .ignoreContentType(true)
        .userAgent(UserAgentProvider.randomUserAgent)
        .execute()

      val contentType: String = response.contentType
      if (contentType.startsWith(txtHtml)) {
        Success(extractContentInformation(response, url))
      } else {
        throw new UnsupportedContentTypeException(
          s"Unsupported content type: $contentType"
        ) // handled below
      }
    } catch {
      case _: UnsupportedMimeTypeException =>
        // unsupported mime types are not re-scheduled, but indexed
        Success(SiteContent(Set.empty))
      case _: MalformedURLException =>
        // malformed urls are not re-scheduled, but indexed
        Success(SiteContent(Set.empty))
      case ex: HttpStatusException if ex.getStatusCode != 200 =>
        // page not found is not re-scheduled and not indexed
        Success(SiteContent(Set.empty, addToIndex = false))
      case _: UnsupportedContentTypeException =>
        // unsupported content is not re-scheduled, but indexed
        Success(SiteContent(Set.empty))
      case ex: IOException =>
        // everything else (e.g. timeout) will be re-scheduled and not indexed yet
        Failure(ex)
    }
  }

  private def extractContentInformation(
      siteResponse: Response,
      url: URL
  ): SiteContent = {
    val doc = siteResponse.parse()
    val toIndex = addToIndex(doc, url)
    val links: Set[URL] = SiteExtractor.extractAbsLinks(doc)
    val cLinks: Set[URL] = SiteExtractor.extractCanonicalLinksFromBody(doc)
    val hRefLang: Set[URL] = SiteExtractor.extractHRefLang(doc)

    SiteContent(links ++ cLinks ++ hRefLang, toIndex)
  }

  private def addToIndex(doc: Document, url: URL): Boolean = {
    // no canonical = true, canonical == url = true, canonical != url = false
    SiteExtractor.canonicalLinkFromHead(doc).forall(_.equals(url))
  }

}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import crawlercommons.robots.BaseRobotRules
import info.coverified.spider.HostCrawler.HostCrawlerEvent
import info.coverified.spider.Indexer.IndexerEvent
import info.coverified.spider.util.CoVerifiedSpiderFilter.RichResponse
import org.jsoup.{HttpStatusException, Jsoup, UnsupportedMimeTypeException}

import java.io.IOException
import java.net.{MalformedURLException, URL}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object SiteScraper extends LazyLogging {

  val USER_AGENT = "CoVerifiedBot-Spider"

  sealed trait SiteScraperEvent

  final case class Scrape(url: URL, sender: ActorRef[HostCrawlerEvent])
      extends SiteScraperEvent

  final case class SiteContent(canonicalUrl: Option[URL], links: Set[URL])

  def apply(
      indexer: ActorRef[IndexerEvent],
      scrapeTimeout: FiniteDuration,
      robotsCfg: BaseRobotRules
  ): Behavior[SiteScraperEvent] =
    idle(indexer, scrapeTimeout, robotsCfg)

  private def idle(
      indexer: ActorRef[IndexerEvent],
      timeout: FiniteDuration,
      robotsCfg: BaseRobotRules
  ): Behavior[SiteScraperEvent] = Behaviors.receiveMessage {
    case Scrape(url, sender) =>
      logger.debug(s"Scraping '$url' ...")
      val maybeContent = scrape(url, timeout, robotsCfg)
      maybeContent match {
        case Success(Some(siteContent)) =>
          indexer ! Indexer
            .Index(url, siteContent.canonicalUrl, siteContent)
        case Success(None) =>
          // nothing to index, but no failure
          // we need to report this to the indexer,
          // in order to tell the supervisor that we processed this url
          // a direct msg to the supervisor would be possible but violates the protocol
          // -> room for performance optimization: send msg directly to supervisor
          indexer ! Indexer.NoIndex(url)
        case Failure(exception) =>
          // report failure back to host crawler
          // -> allows rescheduling
          sender ! HostCrawler.SiteScrapeFailure(
            url,
            exception
          )
      }

      idle(indexer, timeout, robotsCfg)
  }

  private def scrape(
      url: URL,
      timeout: FiniteDuration,
      robotsCfg: BaseRobotRules
  ): Try[Option[SiteContent]] = {
    val link: String = url.toString
    try {
      Jsoup
        .connect(link)
        .timeout(timeout.toMillis.toInt)
        .followRedirects(true)
        .ignoreContentType(true)
        .ignoreHttpErrors(false)
        .userAgent(USER_AGENT)
        .execute()
        .withCoVerifiedHeaderFilter
        .flatMap(_.asFilteredSiteContent(robotsCfg)) match {
        case siteContent @ Some(_) =>
          Success(siteContent)
        case None =>
          Success(None) // nothing to index
      }
    } catch {
      case _: UnsupportedMimeTypeException =>
        // unsupported mime types are not re-scheduled, but indexed
        Success(Some(SiteContent(None, Set.empty)))
      case _: MalformedURLException =>
        // malformed urls are not re-scheduled, but indexed
        Success(Some(SiteContent(None, Set.empty)))
      case ex: HttpStatusException if ex.getStatusCode != 200 =>
        // page not found is not re-scheduled and not indexed
        Success(None)
      case ex: IOException =>
        // everything else (e.g. timeout) will be re-scheduled and not indexed yet
        Failure(ex)
    }
  }
}

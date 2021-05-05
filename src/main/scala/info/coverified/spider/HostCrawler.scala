/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.Indexer.IndexerEvent
import info.coverified.spider.SiteScraper.SiteScraperEvent
import info.coverified.spider.Supervisor.SupervisorEvent

import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}
import java.net.URL
import java.nio.file.Paths
import scala.util.{Failure, Success}

object HostCrawler extends LazyLogging {

  sealed trait HostCrawlerEvent

  final case class Scrap(url: URL) extends HostCrawlerEvent

  final case class Process() extends HostCrawlerEvent

  final case class SiteScraperSuccessful(url: URL) extends HostCrawlerEvent

  final case class SiteScraperFailure(url: URL, reason: Throwable)
      extends HostCrawlerEvent

  final case class HostCrawlerData(
      noOfSiteScraper: Int,
      scrapeTimeout: Timeout,
      indexer: ActorRef[IndexerEvent],
      supervisor: ActorRef[SupervisorEvent],
      siteScraper: Seq[ActorRef[SiteScraperEvent]],
      siteQueue: List[URL] = List.empty
  )

  def apply(
      host: String,
      noOfSiteScraper: Int,
      scrapeInterval: FiniteDuration,
      scrapeTimeout: Timeout,
      supervisor: ActorRef[SupervisorEvent]
  ): Behavior[HostCrawlerEvent] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timer =>
        // self timer to trigger scraping process with delay
        timer.startTimerAtFixedRate(Process(), scrapeInterval)
        val indexer = ctx.spawn(
          Indexer(supervisor, Paths.get(host + ".txt")),
          s"Indexer_$host"
        )
        idle(
          HostCrawlerData(
            noOfSiteScraper,
            scrapeTimeout,
            indexer,
            supervisor,
            (0 until noOfSiteScraper).map(
              no => ctx.spawn(SiteScraper(indexer), s"Scraper_${no}_$host")
            )
          )
        )
      }
    }
  }

  def idle(data: HostCrawlerData): Behavior[HostCrawlerEvent] =
    Behaviors.receive {
      case (ctx, msg) =>
        msg match {
          case Scrap(url) =>
            logger.debug(s"Scheduled '$url' for scraping.")
            idle(
              data.copy(
                siteQueue = data.siteQueue :+ url
              )
            )
          case Process() =>
            process(data, ctx)
          case SiteScraperSuccessful(url) =>
            logger.debug(s"Finished scraping '$url'.")
            Behaviors.same
          case SiteScraperFailure(url, reason) =>
            data.supervisor ! Supervisor.ScrapFailure(url, reason)
            Behaviors.same
        }
    }

  private def process(
      data: HostCrawlerData,
      ctx: ActorContext[HostCrawlerEvent]
  ): Behavior[HostCrawlerEvent] = {
    // take all site scraper available
    implicit val responseTimeout = data.scrapeTimeout
    val processedUrls =
      data.siteQueue.take(data.noOfSiteScraper).zip(data.siteScraper).map {
        case (url, scraper) =>
          logger.debug(s"Scraping '$url'.")
          ctx.ask(scraper, sender => SiteScraper.Scrap(url, sender)) {
            case Success(res: SiteScraperSuccessful) =>
              res
            case Success(invalid) =>
              SiteScraperFailure(
                url,
                new IllegalArgumentException(
                  s"Invalid response from '${scraper.path}' on scraping request: $invalid"
                )
              )
            case Failure(exception) =>
              SiteScraperFailure(url, exception)
          }
          url
      }
    idle(data.copy(siteQueue = data.siteQueue.diff(processedUrls)))
  }
}

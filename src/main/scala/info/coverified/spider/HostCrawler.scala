/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.Indexer.IndexerEvent
import info.coverified.spider.SiteScraper.SiteScraperEvent
import info.coverified.spider.Supervisor.SupervisorEvent

import java.net.URL
import java.nio.file.Paths
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}

object HostCrawler extends LazyLogging {

  sealed trait HostCrawlerEvent

  final case class Scrape(url: URL) extends HostCrawlerEvent

  final case object Process extends HostCrawlerEvent

  final case class SiteScrapeFailure(url: URL, reason: Throwable)
      extends HostCrawlerEvent

  final case class HostCrawlerData(
      noOfSiteScraper: Int,
      indexer: ActorRef[IndexerEvent],
      supervisor: ActorRef[SupervisorEvent],
      siteScraper: ActorRef[SiteScraperEvent],
      siteQueue: ParVector[URL] = ParVector.empty
  )

  def apply(
      host: String,
      noOfSiteScraper: Int,
      scrapeInterval: FiniteDuration,
      scrapeTimeout: Int,
      supervisor: ActorRef[SupervisorEvent]
  ): Behavior[HostCrawlerEvent] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timer =>
        // self timer to trigger scraping process with delay
        timer.startTimerAtFixedRate(Process, scrapeInterval)
        val indexer = ctx.spawn(
          Indexer(supervisor, Paths.get(host + ".txt")),
          s"Indexer_$host"
        )
        val pool = Routers
          .pool(noOfSiteScraper) {
            SiteScraper(indexer, scrapeTimeout)
          }
          .withRoundRobinRouting()
        idle(
          HostCrawlerData(
            noOfSiteScraper,
            indexer,
            supervisor,
            ctx.spawn(pool, "SiteScraper-pool")
          )
        )
      }
    }
  }

  def idle(data: HostCrawlerData): Behavior[HostCrawlerEvent] =
    Behaviors.receive {
      case (ctx, msg) =>
        msg match {
          case Scrape(url) =>
            logger.debug(s"Scheduled '$url' for scraping.")
            idle(
              data.copy(
                siteQueue = data.siteQueue :+ url
              )
            )
          case Process =>
            process(data, ctx)
          case SiteScrapeFailure(url, reason) =>
            data.supervisor ! Supervisor.ScrapeFailure(url, reason)
            Behaviors.same
        }
    }

  private def process(
      data: HostCrawlerData,
      ctx: ActorContext[HostCrawlerEvent]
  ): Behavior[HostCrawlerEvent] = {
    // take all site scraper available
    val processedUrls = data.siteQueue.take(data.noOfSiteScraper)
    processedUrls.foreach { url =>
      data.siteScraper ! SiteScraper.Scrape(url, ctx.self)
    }
    idle(data.copy(siteQueue = data.siteQueue.diff(processedUrls)))
  }
}

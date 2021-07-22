/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import info.coverified.spider.Indexer.IndexerEvent
import info.coverified.spider.SiteScraper.SiteScraperEvent
import info.coverified.spider.Supervisor.SupervisorEvent
import info.coverified.spider.util.SitemapInspector
import sttp.model.Uri

import java.net.URL
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}

object HostCrawler extends LazyLogging {

  sealed trait HostCrawlerEvent

  final case class Scrape(url: URL) extends HostCrawlerEvent

  final case class QueueSitemap(url: URL) extends HostCrawlerEvent

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
      source: SourceView,
      noOfSiteScraper: Int,
      scrapeInterval: FiniteDuration,
      scrapeTimeout: FiniteDuration,
      apiUrl: Uri,
      authSecret: String,
      supervisor: ActorRef[SupervisorEvent]
  ): Behavior[HostCrawlerEvent] = {
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timer =>
        // self timer to trigger scraping process with delay
        timer.startTimerAtFixedRate(Process, scrapeInterval)
        source.url match {
          case Some(urlString) =>
            val host = new URL(urlString).getHost
            val indexer = ctx.spawn(
              Indexer(supervisor, source, apiUrl, authSecret),
              s"Indexer_$host"
            )
            val pool = Routers
              .pool(noOfSiteScraper) {
                SiteScraper(indexer, scrapeTimeout)
              }
              .withRoundRobinRouting()

            // we want to queue the sitemap if available
            ctx.self ! QueueSitemap(new URL(urlString))

            idle(
              HostCrawlerData(
                noOfSiteScraper,
                indexer,
                supervisor,
                ctx.spawn(pool, "SiteScraper-pool")
              )
            )
          case None =>
            logger.warn(
              s"Empty urlString in source: $source. Cannot start corresponding host crawler!"
            )
            Behaviors.stopped
        }
      }
    }
  }

  def idle(data: HostCrawlerData): Behavior[HostCrawlerEvent] =
    Behaviors.receive {
      case (ctx, msg) =>
        msg match {
          case QueueSitemap(url) =>
            logger.info(s"Inspecting and queuing sitemap of '$url'.")
            val siteMapUrls = SitemapInspector.inspect(url)
            idle(
              data.copy(
                siteQueue = data.siteQueue ++ siteMapUrls
              )
            )
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

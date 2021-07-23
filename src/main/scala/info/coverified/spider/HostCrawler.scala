/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import crawlercommons.robots.SimpleRobotRules.RobotRulesMode
import crawlercommons.robots.{BaseRobotRules, SimpleRobotRules}
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import info.coverified.spider.Indexer.IndexerEvent
import info.coverified.spider.SiteScraper.SiteScraperEvent
import info.coverified.spider.Supervisor.{SitemapFinished, SupervisorEvent}
import info.coverified.spider.util.{RobotsTxtInspector, SitemapInspector}
import sttp.model.Uri

import java.net.URL
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.duration._
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.language.{existentials, postfixOps}
import scala.util.{Failure, Success}

object HostCrawler extends LazyLogging {

  sealed trait HostCrawlerEvent

  final case class Scrape(url: URL) extends HostCrawlerEvent

  final case class QueueSitemaps(baseUrl: URL, sitemapUrls: Vector[String])
      extends HostCrawlerEvent

  final case object Process extends HostCrawlerEvent

  final case class SiteScrapeFailure(url: URL, reason: Throwable)
      extends HostCrawlerEvent

  final case class HostCrawlerData(
      sourceUrl: String,
      noOfSiteScraper: Int,
      indexer: ActorRef[IndexerEvent],
      supervisor: ActorRef[SupervisorEvent],
      siteScraper: ActorRef[SiteScraperEvent],
      robotsCfg: BaseRobotRules,
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
            val url = new URL(urlString)
            val host = url.getHost
            val indexer = ctx.spawn(
              Indexer(supervisor, source, apiUrl, authSecret),
              s"Indexer_$host"
            )

            // configure robots txt
            val robotsTxtCfg = RobotsTxtInspector.inspect(url) match {
              case Failure(exception) =>
                logger.warn(
                  s"Cannot process robots.txt from url '$url'. Configure everything as allowed. Exception:",
                  exception
                )
                new SimpleRobotRules(RobotRulesMode.ALLOW_ALL)
              case Success(robotsTxtCfg) =>
                robotsTxtCfg
            }

            val pool = Routers
              .pool(noOfSiteScraper) {
                SiteScraper(indexer, scrapeTimeout, robotsTxtCfg)
              }
              .withRoundRobinRouting()

            // we want to queue the sitemap if available
            ctx.self ! QueueSitemaps(
              url,
              robotsTxtCfg.getSitemaps.asScala.toVector
            )

            idle(
              HostCrawlerData(
                urlString,
                noOfSiteScraper,
                indexer,
                supervisor,
                ctx.spawn(pool, "SiteScraper-pool"),
                robotsTxtCfg
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
          case QueueSitemaps(baseUrl, sitemapUrls) =>
            logger.info(
              s"[${data.sourceUrl}] Inspecting and queuing sitemap of '$baseUrl'."
            )
            val siteMapUrls = (SitemapInspector.inspectFromHost(baseUrl) ++
              SitemapInspector.inspectSitemaps(sitemapUrls)).toSet
              .filter(url => data.robotsCfg.isAllowed(url.toString))

            // to avoid early shutdown, the supervisor needs to know about the urls
            data.supervisor ! SitemapFinished(siteMapUrls)

            idle(
              data.copy(
                siteQueue = data.siteQueue ++ siteMapUrls
              )
            )
          case Scrape(url) =>
            val updatedData = if (data.robotsCfg.isAllowed(url.toString)) {
              logger.debug(
                s"[${data.sourceUrl}] Scheduled '$url' for scraping."
              )
              data.copy(
                siteQueue = data.siteQueue :+ url
              )
            } else
              data
            idle(
              updatedData
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

    val updatedData = if (processedUrls.nonEmpty) {
      val updatedSiteQueue = data.siteQueue.diff(processedUrls)
      logger.info(
        s"[${data.sourceUrl}] Scraping ${processedUrls.size} urls. ${updatedSiteQueue.size} left in queue."
      )
      data.copy(siteQueue = updatedSiteQueue)
    } else
      data

    idle(updatedData)
  }
}

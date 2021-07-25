/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import info.coverified.spider.HostCrawler.HostCrawlerEvent
import info.coverified.spider.main.Config
import io.sentry.{Sentry, SentryLevel}

import scala.language.{existentials, postfixOps}
import java.net.URL
import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object Supervisor extends LazyLogging {

  sealed trait SupervisorEvent

  final case class Start(source: SourceView) extends SupervisorEvent

  final case class ScrapeFailure(url: URL, reason: Throwable)
      extends SupervisorEvent

  final case class IndexFinished(url: URL, newUrls: Set[URL])
      extends SupervisorEvent

  final case object IdleTimeout extends SupervisorEvent

  final case class SitemapFinished(urls: Set[URL]) extends SupervisorEvent

  final case class SupervisorData(
      config: Config,
      startDate: Long = System.currentTimeMillis(),
      host2Source: Map[String, SourceView] = Map.empty,
      host2Actor: Map[String, ActorRef[HostCrawlerEvent]] = Map.empty,
      scrapeCounts: Map[URL, Int] = Map.empty,
      currentlyScraping: Set[URL] = Set.empty
  )

  def apply(cfg: Config): Behavior[SupervisorEvent] = Behaviors.setup { ctx =>
    ctx.setReceiveTimeout(cfg.shutdownTimeout, IdleTimeout)
    idle(SupervisorData(cfg))
  }

  private def idle(
      data: SupervisorData,
      shutdownTryCounter: Int = 0
  ): Behavior[SupervisorEvent] =
    Behaviors.receive {
      case (actorContext, msg) =>
        msg match {
          case Start(source) =>
            source.url match {
              case Some(urlString) =>
                val url = new URL(urlString)
                logger.info(s"Starting indexing for '${source.url}' ...")
                idle(
                  scrape(
                    url,
                    actorContext,
                    data.copy(
                      host2Source = data.host2Source + (url.getHost -> source)
                    )
                  )
                )
              case None =>
                logger.warn(
                  s"Empty urlString in source: $source. Cannot start indexing this one!"
                )
                Behaviors.same
            }
          case ScrapeFailure(url, reason) =>
            idle(handleFailure(data, actorContext, url, reason))
          case SitemapFinished(urls) =>
            idle(data.copy(currentlyScraping = data.currentlyScraping ++ urls))
          case IndexFinished(url, newUrls) =>
            idle(handleIndexFinished(data, actorContext, url, newUrls))
          case IdleTimeout =>
            checkAndShutdown(data, actorContext.system, shutdownTryCounter)
        }
      case _ =>
        Behaviors.unhandled
    }

  private def handleFailure(
      data: SupervisorData,
      actorContext: ActorContext[SupervisorEvent],
      url: URL,
      reason: Throwable
  ): SupervisorData =
    data.scrapeCounts.get(url) match {
      case Some(scrapeCount) if scrapeCount <= data.config.maxRetries =>
        logger.warn(
          s"Scraping failed. Re-scheduling! url: $url Reason = $reason"
        )
        scrape(
          url,
          actorContext,
          data.copy(currentlyScraping = data.currentlyScraping - url)
        )
      case Some(_) =>
        val msg =
          s"Cannot re-schedule '$url' for scraping. Max retries reached! Error = $reason"
        logger.warn(msg)
        Sentry.captureMessage(msg, SentryLevel.INFO)
        data.copy(
          currentlyScraping = data.currentlyScraping - url
        )
      case None =>
        val msg =
          s"Cannot re-schedule '$url' for scraping. Unknown url! Error = $reason"
        logger.error(msg)
        Sentry.captureMessage(msg, SentryLevel.ERROR)
        data.copy(
          currentlyScraping = data.currentlyScraping - url
        )
    }

  private def handleIndexFinished(
      data: SupervisorData,
      actorContext: ActorContext[SupervisorEvent],
      url: URL,
      newUrls: Set[URL]
  ): SupervisorData = {
    val uniqueNewUrls = newUrls
      .map(clean)
      .filterNot(alreadyScraped(_, data))
      .filter(inNamespaces(_, data))

    // sanity to ensure that we have sent out this url
    if (!data.currentlyScraping.contains(url)) {
      val msg =
        s"Received indexing response for '$url' which hasn't been scheduled for indexing!"
      logger.error(msg)
      Sentry.captureMessage(msg, SentryLevel.ERROR)
    }

    if (uniqueNewUrls.nonEmpty) {
      val updatedData = uniqueNewUrls
        .foldLeft(data)(
          (updatedData, url) => scrape(url, actorContext, updatedData)
        )
      logger.info(
        s"Received ${newUrls.size} (new: ${uniqueNewUrls.size}) urls with host ${url.getHost}."
      )
      logger.debug(
        s"Source url is '$url'."
      )
      updatedData
        .copy(currentlyScraping = updatedData.currentlyScraping - url)
    } else {
      logger.debug(s"No new links from $url.")
      logger.debug(
        s"Currently waiting for ${data.currentlyScraping.size} urls scheduled for scraping."
      )
      data.copy(currentlyScraping = data.currentlyScraping - url)
    }
  }

  private def scrape(
      url: URL,
      context: ActorContext[SupervisorEvent],
      data: SupervisorData
  ): SupervisorData = {
    if (data.currentlyScraping.contains(clean(url))) {
      data
    } else {
      val host = url.getHost
      if (host.nonEmpty) {
        data.host2Source
          .get(host)
          .map { source =>
            val actor = data.host2Actor.getOrElse(
              host,
              context
                .spawn(
                  HostCrawler(
                    source,
                    data.config.scrapeParallelism,
                    data.config.scrapeInterval,
                    data.config.scrapeTimeout,
                    data.config.apiUrl,
                    data.config.authSecret,
                    context.self
                  ),
                  s"Scraper_$host"
                )
            )
            actor ! HostCrawler.Scrape(clean(url))
            data.copy(
              host2Actor = data.host2Actor + (host -> actor),
              scrapeCounts = countVisits(clean(url), data.scrapeCounts),
              currentlyScraping = data.currentlyScraping + clean(url)
            )
          }
          .getOrElse(data)
      } else {
        logger.warn(s"Cannot get host of url: '$url'!")
        data
      }
    }
  }

  private def alreadyScraped(url: URL, data: SupervisorData): Boolean =
    data.scrapeCounts.contains(url)

  private def inNamespaces(url: URL, data: SupervisorData): Boolean =
    data.host2Source.keySet.contains(url.getHost)

  private def countVisits(url: URL, scrapCounts: Map[URL, Int]): Map[URL, Int] =
    scrapCounts + (url -> (scrapCounts.getOrElse(url, 0) + 1))

  private def clean(url: URL) = new URL(url.toString.stripSuffix("/"))

  private def checkAndShutdown(
      data: SupervisorData,
      system: ActorSystem[Nothing],
      shutdownCounter: Int
  ): Behavior[SupervisorEvent] = {
    if (data.currentlyScraping.isEmpty) {
      // shutdown all
      logger.info(
        "Idle timeout in Supervisor reached and scraping data is empty. " +
          "Initiate shutdown ..."
      )
      logger.info("Stats:")
      logger.info(s"Start: ${new Date(data.startDate)}")
      logger.info(
        s"End: ${new Date(System.currentTimeMillis() - data.config.shutdownTimeout.toMillis)}"
      )
      logger.info(
        s"Duration: ${(System.currentTimeMillis() - data.config.shutdownTimeout.toMillis - data.startDate) / 1000}s"
      )
      system.terminate()
    } else {
      logger.info(
        "Shutdown timeout received. But still waiting for answers from {} url scraping requests!",
        data.currentlyScraping.size
      )
      logger.info(
        "Awaiting data: {}",
        data.currentlyScraping
          .groupBy(_.getHost)
          .map {
            case (host, urls) => host -> urls.size
          }
          .mkString("\n")
      )
      logger.info(
        "{}",
        data.currentlyScraping.groupBy(_.getHost).mkString("\n")
      )

      // force shutdown after 10 tries to softly shutdown
      if (shutdownCounter == 10) {
        val msg = "Maximum number of shutdown tries reached. Force shutdown!"
        logger.error(msg)
        Sentry.captureMessage(msg, SentryLevel.FATAL)
        system.terminate()
        Try(Await.ready(system.whenTerminated, 60 seconds)) match {
          case Failure(exception) =>
            logger.error(
              "Exception occurred during akka system shutdown!",
              exception
            )
            Sentry.captureException(exception)
          case Success(_) =>
        }
        System.exit(-1)
      }
    }
    idle(data, shutdownTryCounter = shutdownCounter + 1)
  }
}

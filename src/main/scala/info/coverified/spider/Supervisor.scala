/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.HostCrawler.HostCrawlerEvent
import info.coverified.spider.main.Config

import scala.language.{existentials, postfixOps}
import java.net.URL

object Supervisor extends LazyLogging {

  sealed trait SupervisorEvent

  final case class Start(url: URL) extends SupervisorEvent

  final case class ScrapFailure(url: URL, reason: Throwable)
      extends SupervisorEvent

  final case class IndexFinished(url: URL, newUrls: Set[URL])
      extends SupervisorEvent

  final case class IdleTimeout() extends SupervisorEvent

  final case class SupervisorData(
      config: Config,
      host2Actor: Map[String, ActorRef[HostCrawlerEvent]] = Map.empty,
      scrapCounts: Map[URL, Int] = Map.empty,
      toScrape: Set[URL] = Set.empty,
      namespaces: Vector[String] = Vector.empty
  )

  private val maxRetries = 0 // todo JH config value

  def apply(cfg: Config): Behavior[SupervisorEvent] = Behaviors.setup { ctx =>
    ctx.setReceiveTimeout(cfg.shutdownTimeout, IdleTimeout())
    idle(SupervisorData(cfg))
  }

  private def idle(data: SupervisorData): Behavior[SupervisorEvent] =
    Behaviors.receive {
      case (actorContext, msg) =>
        msg match {
          case Start(url) =>
            logger.info(s"Starting indexing for '$url' ...")
            idle(scrape(url, actorContext, data))
          case ScrapFailure(url, reason) =>
            val updatedData = data.scrapCounts.get(url) match {
              case Some(scrapeCount) if scrapeCount <= maxRetries =>
                logger.warn(
                  s"Scraping failed. Re-scheduling! url: $url Reason = $reason"
                )
                scrape(url, actorContext, data)
              case Some(_) =>
                logger.debug(
                  s"Cannot re-schedule '$url' for scraping. Max retries reached!"
                )
                data
              case None =>
                logger.error(
                  s"Cannot re-schedule '$url' for scraping. Unknown url!"
                )
                data
            }
            idle(updatedData)
          case IndexFinished(url, newUrls) =>
            logger.debug(
              s"Received new urls from '$url': ${newUrls.mkString(", ")}"
            )
            val updatedData = newUrls
              .map(clean)
              .filterNot(alreadyScraped(_, data))
              .filter(inNamespaces(_, data))
              .foldLeft(data)(
                (updatedData, url) => scrape(url, actorContext, updatedData)
              )
            idle(updatedData.copy(toScrape = updatedData.toScrape - url))
          case IdleTimeout() =>
            checkAndShutdown(data, actorContext.system)
        }
      case _ =>
        Behaviors.unhandled
    }

  private def scrape(
      url: URL,
      context: ActorContext[SupervisorEvent],
      data: SupervisorData
  ): SupervisorData = {
    val host = url.getHost
    if (host.nonEmpty) {
      val actor = data.host2Actor.getOrElse(
        host,
        context
          .spawn(
            HostCrawler(
              host,
              data.config.scrapParallelism,
              data.config.scrapeInterval,
              data.config.scrapeTimeout,
              context.self
            ),
            s"Scraper_$host"
          )
      )
      actor ! HostCrawler.Scrap(url)
      data.copy(
        host2Actor = data.host2Actor + (host -> actor),
        namespaces = data.namespaces :+ host,
        scrapCounts = countVisits(clean(url), data.scrapCounts),
        toScrape = data.toScrape + url
      )
    } else {
      logger.warn(s"Cannot get host of url: '$url'!")
      data
    }
  }

  private def alreadyScraped(url: URL, data: SupervisorData): Boolean =
    data.scrapCounts.contains(url)

  private def inNamespaces(url: URL, data: SupervisorData): Boolean =
    data.namespaces.contains(url.getHost)

  private def countVisits(url: URL, scrapCounts: Map[URL, Int]): Map[URL, Int] =
    scrapCounts + (url -> (scrapCounts.getOrElse(url, 0) + 1))

  private def clean(url: URL) = new URL(url.toString.stripSuffix("/"))

  private def checkAndShutdown(
      data: SupervisorData,
      system: ActorSystem[Nothing]
  ): Behavior[SupervisorEvent] = {
    if (data.toScrape.isEmpty) {
      // shutdown all
      logger.info(
        "Idle timeout in Supervisor reached and scraping data is empty. " +
          "Initiate shutdown ..."
      )
      system.terminate()
    }
    idle(data)
  }
}

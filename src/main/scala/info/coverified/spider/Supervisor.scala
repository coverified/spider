/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.HostCrawler.HostCrawlerEvent

import scala.concurrent.duration._
import scala.language.{existentials, postfixOps}
import java.net.URL

object Supervisor extends LazyLogging {

  sealed trait SupervisorEvent

  final case class Start(url: URL) extends SupervisorEvent

  final case class ScrapSuccessful(url: URL) extends SupervisorEvent

  final case class ScrapFailure(url: URL, reason: Throwable)
      extends SupervisorEvent

  final case class IndexFinished(url: URL, newUrls: Set[URL])
      extends SupervisorEvent

  final case class IdleTimeout() extends SupervisorEvent

  final case class SupervisorData(
      host2Actor: Map[String, ActorRef[HostCrawlerEvent]] = Map.empty,
      scrapCounts: Map[URL, Int] = Map.empty,
      toScrap: Set[URL] = Set.empty,
      namespaces: Vector[String] = Vector.empty
  )

  // TODO JH config values
  val noOfScraper = 100

  def apply(): Behavior[SupervisorEvent] = Behaviors.setup { ctx =>
    ctx.setReceiveTimeout(20 seconds, IdleTimeout())
    idle(SupervisorData())
  }

  private def idle(data: SupervisorData): Behavior[SupervisorEvent] =
    Behaviors.receive {
      case (actorContext, msg) =>
        msg match {
          case Start(url) =>
            logger.info(s"Starting indexing for '$url' ...")
            idle(scrap(url, actorContext, data))
          case ScrapSuccessful(url) =>
            logger.info(s"Successfully scraped '$url'.")
            idle(data)
          case ScrapFailure(url, reason) =>
            logger.error(s"Scraping failed for $url! Reason = $reason")
            // TODO: JH -> retry
            idle(data)
          case IndexFinished(url, newUrls) =>
            logger.debug(
              s"Received new urls from '$url': ${newUrls.mkString(", ")}"
            )
            // todo JH maybe max number of pages
            val updatedData = newUrls
              .filterNot(alreadyScraped(_, data))
              .filter(isInNamespace(_, data))
              .foldLeft(data)(
                (updatedData, url) => scrap(url, actorContext, updatedData)
              )
            idle(updatedData.copy(toScrap = updatedData.toScrap - url))
          case IdleTimeout() =>
            // todo JH
            checkAndShutdown(data, actorContext.system)
        }
      case _ =>
        Behaviors.unhandled
    }

  private def scrap(
      url: URL,
      context: ActorContext[SupervisorEvent],
      data: SupervisorData
  ): SupervisorData = {
    val host = url.getHost
    if (host.nonEmpty) {
      val actor = data.host2Actor.getOrElse(
        host,
        context
          .spawn(HostCrawler(host, noOfScraper, context.self), s"Scraper_$host")
      )
      actor ! HostCrawler.Scrap(url)
      data.copy(
        host2Actor = data.host2Actor + (host -> actor),
        namespaces = data.namespaces :+ host,
        scrapCounts = countVisits(url, data.scrapCounts),
        toScrap = data.toScrap + url
      )
    } else {
      logger.warn(s"Cannot get host of url: '$url'!")
      data
    }
  }

  private def alreadyScraped(url: URL, data: SupervisorData): Boolean =
    data.scrapCounts.contains(url)

  private def isInNamespace(url: URL, data: SupervisorData): Boolean =
    data.namespaces.contains(url.getHost)

  private def countVisits(url: URL, scrapCounts: Map[URL, Int]): Map[URL, Int] =
    scrapCounts + (url -> (scrapCounts.getOrElse(url, 0) + 1))

  private def checkAndShutdown(
      data: SupervisorData,
      system: ActorSystem[Nothing]
  ): Behavior[SupervisorEvent] = {
    if (data.toScrap.isEmpty) {
      // shutdown all
      logger.info("Idle timeout in Supervisor reached and scraping data is empty. " +
        "Initiate shutdown ...")
      system.terminate()
    }
    idle(data)
  }
}

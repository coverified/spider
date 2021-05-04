/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.SupervisorEvent

import java.net.URL
import scala.collection.mutable

object Indexer extends LazyLogging {

  sealed trait IndexerEvent

  final case class Index(url: URL, content: SiteContent) extends IndexerEvent

  var store = mutable.Set.empty[URL] // TODO: JH remove

  def apply(supervisor: ActorRef[SupervisorEvent]): Behavior[IndexerEvent] =
    idle(supervisor)

  private def idle(
      supervisor: ActorRef[SupervisorEvent]
  ): Behavior[IndexerEvent] = Behaviors.receiveMessage {
    case Index(url, content) =>
      logger.debug(s"Indexed '$url'")
      store += url
      logger.debug(s"Store: ${store.mkString(", ")}")
      supervisor ! Supervisor.IndexFinished(url, content.links)
      idle(supervisor)
  }

}

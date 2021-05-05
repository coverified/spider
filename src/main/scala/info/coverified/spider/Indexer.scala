/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.SupervisorEvent

import java.net.URL
import java.nio.file.Path
import java.nio.file.StandardOpenOption.{APPEND, CREATE, WRITE}

object Indexer extends LazyLogging {

  sealed trait IndexerEvent

  final case class Index(url: URL, content: SiteContent) extends IndexerEvent

  def apply(
      supervisor: ActorRef[SupervisorEvent],
      file: Path
  ): Behavior[IndexerEvent] =
    idle(supervisor, file)

  private def idle(
      supervisor: ActorRef[SupervisorEvent],
      file: Path
  ): Behavior[IndexerEvent] = Behaviors.receive[IndexerEvent] {
    case (ctx, msg) =>
      msg match {
        case Index(url, content) =>
          logger.debug(s"Indexed '$url'")
          implicit val system: ActorSystem[Nothing] = ctx.system
          Source
            .single(url.toString + "\n")
            .map(t => ByteString(t))
            .runWith(FileIO.toPath(file, Set(WRITE, APPEND, CREATE)))

          supervisor ! Supervisor.IndexFinished(url, content.links)
          idle(supervisor, file)
      }
  }

}

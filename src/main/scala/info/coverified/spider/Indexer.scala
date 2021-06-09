/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.AllUrlSource.AllUrlSourceView
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.SupervisorEvent
import info.coverified.spider.util.DBConnector
import sttp.model.Uri

import java.net.URL

object Indexer extends LazyLogging {

  sealed trait IndexerEvent

  final case class Index(url: URL, content: SiteContent) extends IndexerEvent

  final case class NoIndex(url: URL) extends IndexerEvent

  def apply(
      supervisor: ActorRef[SupervisorEvent],
      source: AllUrlSourceView,
      apiUri: Uri
  ): Behavior[IndexerEvent] =
    idle(supervisor, source, apiUri)

  private def idle(
      supervisor: ActorRef[SupervisorEvent],
      source: AllUrlSourceView,
      apiUri: Uri
  ): Behavior[IndexerEvent] = Behaviors.receive[IndexerEvent] {
    case (ctx, msg) =>
      msg match {
        case Index(url, content) =>
          // schedule new urls if any and write out/index the base url
          logger.debug(s"Indexed '$url'")
          implicit val system: ActorSystem[Nothing] = ctx.system

          handleUrl(source, url, apiUri)
          //Source
          //  .single(url.toString + "\n")
          //  .map(t => ByteString(t))
          //  .runWith(FileIO.toPath(file, Set(WRITE, APPEND, CREATE)))

          supervisor ! Supervisor.IndexFinished(url, content.links)
          idle(supervisor, source, apiUri)

        case NoIndex(url) =>
          // do not index this url
          supervisor ! Supervisor.IndexFinished(url, Set.empty)
          idle(supervisor, source, apiUri)

      }
  }

  /**
    * Handle the received url by storing it in database.
    *
    * @return [[Option]] onto an effect, that might be put to API
    */
  def handleUrl(
      source: AllUrlSourceView,
      url: URL,
      apiUri: Uri
  ): Unit = {
    logger.info("Handling url: {}", url.toString)
    if (!source.urls.contains(url.toString)) {
      try {
        DBConnector
          .sendRequest(
            DBConnector.storeMutation(
              DBConnector.createUrlMutation(source, url.toString),
              apiUri
            )
          )
      } catch {
        case e: Throwable =>
          e.printStackTrace()
      }
    }
  }

}

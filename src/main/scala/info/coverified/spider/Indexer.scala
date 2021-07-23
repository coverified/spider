/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.scalalogging.LazyLogging
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.SupervisorEvent
import info.coverified.spider.util.DBConnector
import sttp.model.Uri

import java.net.URL

object Indexer extends LazyLogging {

  sealed trait IndexerEvent

  final case class Index(
      url: URL,
      canonicalUrl: Option[URL],
      content: SiteContent
  ) extends IndexerEvent

  final case class NoIndex(url: URL) extends IndexerEvent

  def apply(
      supervisor: ActorRef[SupervisorEvent],
      source: SourceView,
      apiUrl: Uri,
      authSecret: String
  ): Behavior[IndexerEvent] =
    idle(supervisor, source, apiUrl, authSecret)

  private def idle(
      supervisor: ActorRef[SupervisorEvent],
      source: SourceView,
      apiUrl: Uri,
      authSecret: String
  ): Behavior[IndexerEvent] = Behaviors.receive[IndexerEvent] {
    case (ctx, msg) =>
      msg match {
        case Index(url, canonicalUrl, content) =>
          // schedule new urls if any and write out/index the base url
          implicit val system: ActorSystem[Nothing] = ctx.system

          // always handle canonical url, if available and drop the other one
          handleUrl(source, canonicalUrl.getOrElse(url), apiUrl, authSecret)

          //Source
          //  .single(url.toString + "\n")
          //  .map(t => ByteString(t))
          //  .runWith(FileIO.toPath(file, Set(WRITE, APPEND, CREATE)))

          supervisor ! Supervisor.IndexFinished(url, content.links)
          idle(supervisor, source, apiUrl, authSecret)

        case NoIndex(url) =>
          // do not index this url
          supervisor ! Supervisor.IndexFinished(url, Set.empty)
          idle(supervisor, source, apiUrl, authSecret)

      }
  }

  /**
    * Handle the received url by storing it in database.
    *
    * @return [[Option]] onto an effect, that might be put to API
    */
  def handleUrl(
      source: SourceView,
      url: URL,
      apiUrl: Uri,
      authSecret: String
  ): Unit = {
    // check if source exists in db, otherwise persist
    try {
      DBConnector.getUrls(url.toString, apiUrl, authSecret) match {
        case Some(_) =>
          logger.debug("'{}' is already indexed!", url.toString)
        case None =>
          logger.info("Indexing url: {}", url.toString)
          DBConnector
            .sendRequest(
              DBConnector.storeMutation(
                DBConnector.createUrlMutation(source, url.toString),
                apiUrl,
                authSecret
              )
            )
      }
    } catch {
      case e: Throwable =>
        e.printStackTrace()
    }
  }

}

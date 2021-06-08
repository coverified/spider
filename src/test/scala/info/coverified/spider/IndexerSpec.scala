/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import info.coverified.graphql.schema.AllUrlSource.AllUrlSourceView
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.IndexFinished
import sttp.model.Uri

import java.net.URL

/**
  * File output is not checked here, since this should be replaced by database output anyway soon
  */
class IndexerSpec extends ActorSpec {

  private val source =
    AllUrlSourceView("-1", None, None, "http://www.example.com", List.empty)

  "Index message received by Indexer" should {
    "result in appropriate IndexFinished being sent to Supervisor" ignore { // TODO fix and enable again
      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, Uri("")) // FIXME mock server
      )

      val crawledUrl = new URL("http://www.example1.com")
      val newLinks = Set(
        new URL("http://www.example1.com/page1"),
        new URL("http://www.example2.com/index.php")
      )

      indexer ! Indexer.Index(crawledUrl, SiteContent(newLinks))

      supervisor.expectMessage(
        IndexFinished(crawledUrl, newLinks)
      )
    }
  }

  "NoIndex message received by Indexer" should {
    "result in appropriate IndexFinished being sent to Supervisor" in {
      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, Uri("-- no uri --"))
      )

      val crawledUrl = new URL("http://www.example1.com")

      indexer ! Indexer.NoIndex(crawledUrl)

      supervisor.expectMessage(
        IndexFinished(crawledUrl, Set.empty)
      )
    }
  }
}

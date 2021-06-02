/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.IndexFinished

import java.io.File
import java.net.URL

/**
  * File output is not checked here, since this should be replaced by database output anyway soon
  */
class IndexerSpec extends ActorSpec {

  private val outFile = File.createTempFile("Indexer-out", ".txt").toPath

  "Index message received by Indexer" should {
    "result in appropriate IndexFinished being sent to Supervisor" in {
      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, outFile)
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
        Indexer(supervisor.ref, outFile)
      )

      val crawledUrl = new URL("http://www.example1.com")

      indexer ! Indexer.NoIndex(crawledUrl)

      supervisor.expectMessage(
        IndexFinished(crawledUrl, Set.empty)
      )
    }
  }
}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{
  equalTo,
  getRequestedFor,
  status,
  urlEqualTo
}
import info.coverified.spider.SiteScraper.SiteContent

import java.net.{URL, UnknownHostException}
import scala.concurrent.duration.DurationInt

class SiteScraperSpec extends WireMockActorSpec {

  "Scrape message received by SiteScraper" should {
    "result in indexation and extraction of URL if status code is ok" in {
      // set up test page
      val html = """|<html>
                    |  <head></head>
                    |  <body>
                    |    <a href="/page2">Link to page 2</a>
                    |  </body>
                    |</html>""".stripMargin

      wireMockServer.stubFor(
        WireMock
          .get("/page1")
          .willReturn(
            status(200)
              .withHeader("Content-Type", "text/html")
              .withBody(html)
          )
      )

      val indexer =
        testKit.createTestProbe[Indexer.IndexerEvent]("Indexer_127.0.0.1")
      val hostCrawler = testKit.createTestProbe[HostCrawler.HostCrawlerEvent](
        "Scraper_127.0.0.1"
      )

      val behaviorKit =
        BehaviorTestKit(
          SiteScraper(
            indexer.ref,
            defaultConfig.scrapeTimeout
          )
        )

      val pageUrl = new URL(s"http://127.0.0.1:$port/page1")
      behaviorKit.run(SiteScraper.Scrape(pageUrl, hostCrawler.ref))

      indexer.expectMessage(
        Indexer.Index(
          pageUrl,
          SiteContent(None, Set(new URL(s"http://127.0.0.1:$port/page2")))
        )
      )
      hostCrawler.expectNoMessage()

      // testing if proper user agent has been set
      wireMockServer.verify(
        WireMock.exactly(1),
        getRequestedFor(urlEqualTo("/page1"))
          .withHeader("User-Agent", equalTo("CoVerifiedBot-Spider"))
      )
    }

    "result in no indexation if status code is not ok" in {
      // set up test page
      wireMockServer.stubFor(
        WireMock
          .get("/page2")
          .willReturn(
            status(404)
              .withHeader("Content-Type", "text/html")
          )
      )

      val indexer =
        testKit.createTestProbe[Indexer.IndexerEvent]("Indexer_127.0.0.1")
      val hostCrawler = testKit.createTestProbe[HostCrawler.HostCrawlerEvent](
        "Scraper_127.0.0.1"
      )

      val behaviorKit =
        BehaviorTestKit(
          SiteScraper(
            indexer.ref,
            2.seconds
          )
        )

      val pageUrl = new URL(s"http://127.0.0.1:$port/page2")
      behaviorKit.run(SiteScraper.Scrape(pageUrl, hostCrawler.ref))

      indexer.expectMessage(
        Indexer.NoIndex(pageUrl)
      )
      hostCrawler.expectNoMessage()
    }

    "result in failure message sent to HostCrawler when given host is unreachable" in {
      val indexer =
        testKit.createTestProbe[Indexer.IndexerEvent]("Indexer_127.0.0.1")
      val hostCrawler = testKit.createTestProbe[HostCrawler.HostCrawlerEvent](
        "Scraper_127.0.0.1"
      )

      val behaviorKit =
        BehaviorTestKit(
          SiteScraper(
            indexer.ref,
            1.second
          )
        )

      // request impossible URL
      val pageUrl = new URL(s"http://127.0.0.0.0.0.0.0.1:$port")
      behaviorKit.run(SiteScraper.Scrape(pageUrl, hostCrawler.ref))

      indexer.expectNoMessage()
      val failureMsg =
        hostCrawler.expectMessageType[HostCrawler.SiteScrapeFailure]
      failureMsg.url shouldBe pageUrl
      // UnknownHostException doesn't implement equals(), so we check ourselves
      failureMsg.reason.getClass shouldBe classOf[UnknownHostException]
    }
  }

}

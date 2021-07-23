/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{
  containing,
  postRequestedFor,
  status,
  urlEqualTo
}
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.IndexFinished
import sttp.model.Uri

import java.net.URL
import scala.concurrent.duration.DurationInt

/**
  * File output is not checked here, since this should be replaced by database output anyway soon
  */
class IndexerSpec extends WireMockActorSpec {

  private val mockAuthSecret = ""

  "Index message received by Indexer" should {

    "result in appropriate IndexFinished being sent to Supervisor if url already exists" in {
      val source =
        SourceView(
          "2",
          None,
          None,
          Some("http://www.example1.com")
        )

      wireMockServer.stubFor(
        WireMock
          .post("/api")
          .withRequestBody(containing("query{allUrls"))
          .willReturn(
            status(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""|{
                           |  "data": {
                           |    "allUrls": [
                           |      {
                           |        "id": "0",
                           |        "name": "http://www.example1.com",
                           |        "source": null,
                           |        "lastCrawl": null
                           |      }
                           |    ]
                           |  }
                           |}
                           |""".stripMargin)
          )
      )

      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val apiUrl = Uri.unsafeParse(s"http://127.0.0.1:$port/api")

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, apiUrl, mockAuthSecret)
      )

      val crawledUrl = new URL("http://www.example1.com")
      val newLinks = Set(
        new URL("http://www.example1.com/page1"),
        new URL("http://www.example2.com/index.php")
      )

      indexer ! Indexer.Index(crawledUrl, None, SiteContent(None, newLinks))

      supervisor.expectMessage(
        10.seconds,
        IndexFinished(crawledUrl, newLinks)
      )

      wireMockServer.verify(
        WireMock.exactly(1),
        postRequestedFor(urlEqualTo("/api"))
          .withRequestBody(containing("query{allUrls"))
      )
      wireMockServer.verify(
        WireMock.exactly(0),
        postRequestedFor(urlEqualTo("/api"))
          .withRequestBody(containing("mutation{createUrl"))
      )
    }

    "result in url being inserted into db and appropriate IndexFinished being sent to Supervisor if url not existing yet" in {
      val source =
        SourceView("2", None, None, Some("http://www.example1.com"))

      wireMockServer.stubFor(
        WireMock
          .post("/api")
          .withRequestBody(containing("query{allUrls"))
          .willReturn(
            status(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""|{
                           |  "data": {
                           |    "allUrls": []
                           |  }
                           |}
                           |""".stripMargin)
          )
      )

      wireMockServer.stubFor(
        WireMock
          .post("/api")
          .withRequestBody(containing("mutation{createUrl"))
          .willReturn(
            status(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""|{
                   |  "data": {
                   |    "createUrl": {
                   |      "id": "0",
                   |      "name": "http://www.example1.com",
                   |      "source": null,
                          "lastCrawl": null
                   |    }
                   |  }
                   |}
                   |""".stripMargin)
          )
      )

      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val apiUrl = Uri.unsafeParse(s"http://127.0.0.1:$port/api")

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, apiUrl, mockAuthSecret)
      )

      val crawledUrl = new URL("http://www.example1.com")
      val newLinks = Set(
        new URL("http://www.example1.com/page1"),
        new URL("http://www.example2.com/index.php")
      )

      indexer ! Indexer.Index(crawledUrl, None, SiteContent(None, newLinks))

      supervisor.expectMessage(
        10.seconds,
        IndexFinished(crawledUrl, newLinks)
      )

      wireMockServer.verify(
        WireMock.exactly(1),
        postRequestedFor(urlEqualTo("/api"))
          .withRequestBody(containing("query{allUrls"))
      )
      wireMockServer.verify(
        WireMock.exactly(1),
        postRequestedFor(urlEqualTo("/api"))
          .withRequestBody(containing("mutation{createUrl"))
      )
    }
  }

  "NoIndex message received by Indexer" should {
    "result in appropriate IndexFinished being sent to Supervisor" in {
      val source =
        SourceView("2", None, None, Some("http://www.example1.com"))

      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val apiUrl = Uri.unsafeParse(s"http://127.0.0.1:$port/api")

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, apiUrl, mockAuthSecret)
      )

      val crawledUrl = new URL("http://www.example1.com")

      indexer ! Indexer.NoIndex(crawledUrl)

      supervisor.expectMessage(
        IndexFinished(crawledUrl, Set.empty)
      )

      // no request sent
      wireMockServer.verify(
        WireMock.exactly(0),
        postRequestedFor(urlEqualTo("/api"))
      )
    }
  }
}

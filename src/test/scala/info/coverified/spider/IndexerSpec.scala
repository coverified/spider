/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{
  postRequestedFor,
  status,
  urlEqualTo
}
import info.coverified.graphql.schema.AllUrlSource.AllUrlSourceView
import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.Supervisor.IndexFinished
import sttp.model.Uri

import java.net.URL
import scala.concurrent.duration.DurationInt

/**
  * File output is not checked here, since this should be replaced by database output anyway soon
  */
class IndexerSpec extends WireMockActorSpec {

  "Index message received by Indexer" should {
    "result in appropriate IndexFinished being sent to Supervisor if url already exists" in {
      val source =
        AllUrlSourceView(
          "2",
          None,
          None,
          "http://www.example1.com",
          List("http://www.example1.com")
        )

      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val apiUri = Uri.unsafeParse(s"http://127.0.0.1:$port/api")

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, apiUri)
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

      // no request sent
      wireMockServer.verify(
        WireMock.exactly(0),
        postRequestedFor(urlEqualTo("/api"))
      )
    }

    "result in url being inserted into db and appropriate IndexFinished being sent to Supervisor if url not existing yet" in {
      val source =
        AllUrlSourceView("2", None, None, "http://www.example1.com", List.empty)

      wireMockServer.stubFor(
        WireMock
          .post("/api")
          .willReturn(
            status(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""|{
                   |  "data": {
                   |    "createUrl": {
                   |      "id": "0",
                   |      "name": "http://www.example1.com",
                   |      "source": {
                   |        "id": "2"
                   |      }
                   |    }
                   |  }
                   |}
                   |""".stripMargin)
          )
      )

      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val apiUri = Uri.unsafeParse(s"http://127.0.0.1:$port/api")

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, apiUri)
      )

      val crawledUrl = new URL("http://www.example1.com")
      val newLinks = Set(
        new URL("http://www.example1.com/page1"),
        new URL("http://www.example2.com/index.php")
      )

      indexer ! Indexer.Index(crawledUrl, SiteContent(newLinks))

      supervisor.expectMessage(
        6.seconds,
        IndexFinished(crawledUrl, newLinks)
      )

      // we just verify that the url was called, not the contents of the request.
      // checking the content would also be possible by adding withRequestBody()
      wireMockServer.verify(
        WireMock.exactly(1),
        postRequestedFor(urlEqualTo("/api"))
      )
    }
  }

  "NoIndex message received by Indexer" should {
    "result in appropriate IndexFinished being sent to Supervisor" in {
      val source =
        AllUrlSourceView("2", None, None, "http://www.example1.com", List.empty)

      val supervisor = testKit.createTestProbe[Supervisor.SupervisorEvent](
        "Supervisor"
      )

      val apiUri = Uri.unsafeParse(s"http://127.0.0.1:$port/api")

      val indexer = testKit.spawn(
        Indexer(supervisor.ref, source, apiUri)
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

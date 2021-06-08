/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.testkit.typed.Effect.{ReceiveTimeoutSet, Spawned}
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import info.coverified.graphql.schema.AllUrlSource.AllUrlSourceView
import info.coverified.spider.HostCrawler.HostCrawlerEvent
import info.coverified.spider.Supervisor.{
  IdleTimeout,
  IndexFinished,
  ScrapeFailure,
  Start
}
import info.coverified.spider.main.Config
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import scala.concurrent.duration.DurationInt

class SupervisorSpec extends AnyWordSpec with Matchers {

  private val source1 =
    AllUrlSourceView("1", None, None, "https://www.example1.com/", List.empty)
  private val source2 =
    AllUrlSourceView("2", None, None, "https://www.example2.com/", List.empty)

  "Initialization of the Supervisor" should {
    "set up a receive timeout" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffect(ReceiveTimeoutSet(15000.millis, IdleTimeout))
    }
  }

  "Start messages received by the Supervisor" should {
    "spawn HostCrawlers for each host and trigger scraping at each host URL" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(source1))
      val hostCrawler1 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      hostCrawler1.childName shouldBe "Scraper_www.example1.com"
      val hostCrawlerInbox1 = testKit.childInbox(hostCrawler1.ref)
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com"))
      )

      testKit.run(Start(source2))
      val hostCrawler2 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      hostCrawler2.childName shouldBe "Scraper_www.example2.com"
      val hostCrawlerInbox2 = testKit.childInbox(hostCrawler2.ref)
      hostCrawlerInbox2.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example2.com"))
      )
    }

    "do nothing when indexing provides URLs to an unknown host" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(source1))
      testKit.expectEffectType[Spawned[_]]

      testKit.run(
        IndexFinished(
          new URL("https://www.example1.com"),
          Set(
            new URL("https://www.example2.com/"),
            new URL("https://www.example3.com/page1.html")
          )
        )
      )
      assert(!testKit.hasEffects())
    }

    "trigger scraping at each respective host when provided with new matching URLs" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(source1))
      val hostCrawler1 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      val hostCrawlerInbox1 = testKit.childInbox(hostCrawler1.ref)
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com"))
      )

      testKit.run(Start(source2))
      val hostCrawler2 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      val hostCrawlerInbox2 = testKit.childInbox(hostCrawler2.ref)
      hostCrawlerInbox2.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example2.com"))
      )

      testKit.run(
        IndexFinished(
          new URL("https://www.example1.com"),
          Set(
            new URL("https://www.example1.com/page0.html/"),
            new URL("https://www.example2.com/page1.html"),
            new URL("https://www.example3.com/not-used.html")
          )
        )
      )
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com/page0.html"))
      )
      hostCrawlerInbox2.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example2.com/page1.html"))
      )
    }

    "do nothing when indexer provides known URLs" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(source1))
      testKit.expectEffectType[Spawned[_]]

      testKit.run(
        IndexFinished(
          new URL("https://www.example1.com"),
          Set(
            new URL("https://www.example1.com")
          )
        )
      )
      assert(!testKit.hasEffects())
    }
  }

  "A ScrapeFailure received by the Supervisor" should {
    "result in retrying with a Start message when MAX_RETRIES is not reached yet" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get.copy(maxRetries = 1)))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(source1))
      val hostCrawler1 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      val hostCrawlerInbox1 = testKit.childInbox(hostCrawler1.ref)
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com"))
      )

      // first time we retry
      testKit.run(
        ScrapeFailure(
          new URL("https://www.example1.com"),
          new Exception("no reason tbh")
        )
      )
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com"))
      )

      // second time we give up
      testKit.run(
        ScrapeFailure(
          new URL("https://www.example1.com"),
          new Exception("no reason tbh")
        )
      )
      // sadly TestInbox.expectNoMsg is not implemented yet
      assertThrows[NoSuchElementException](hostCrawlerInbox1.receiveMessage())
    }
  }

}

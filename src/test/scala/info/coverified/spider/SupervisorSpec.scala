/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.testkit.typed.Effect.{ReceiveTimeoutSet, Spawned}
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import info.coverified.spider.HostCrawler.HostCrawlerEvent
import info.coverified.spider.Supervisor.{
  IdleTimeout,
  IndexFinished,
  ScrapeFailure,
  Start
}
import info.coverified.spider.main.Config
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import scala.concurrent.duration.DurationInt

class SupervisorSpec extends AnyWordSpec {

  "A Supervisor" should {
    "set a receive timer upon spawning" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffect(ReceiveTimeoutSet(15000.millis, IdleTimeout))
    }

    "spawn a HostCrawler for each host and trigger scraping at each host URL" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(new URL("https://www.example1.com/")))
      val hostCrawler1 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      val hostCrawlerInbox1 = testKit.childInbox(hostCrawler1.ref)
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com"))
      )

      testKit.run(Start(new URL("https://www.example2.com/")))
      val hostCrawler2 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      val hostCrawlerInbox2 = testKit.childInbox(hostCrawler2.ref)
      hostCrawlerInbox2.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example2.com"))
      )
    }

    "do nothing when indexing provides URLs to an unknown host" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(new URL("https://www.example1.com/")))
      testKit.expectEffectType[Spawned[HostCrawlerEvent]]

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

      testKit.run(Start(new URL("https://www.example1.com/")))
      val hostCrawler1 = testKit.expectEffectType[Spawned[HostCrawlerEvent]]
      val hostCrawlerInbox1 = testKit.childInbox(hostCrawler1.ref)
      hostCrawlerInbox1.expectMessage(
        HostCrawler.Scrape(new URL("https://www.example1.com"))
      )

      testKit.run(Start(new URL("https://www.example2.com/")))
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

      testKit.run(Start(new URL("https://www.example1.com/")))
      testKit.expectEffectType[Spawned[HostCrawlerEvent]]

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

    "retries scraping upon failure when MAX_RETRIES is not reached yet" in {
      val testKit: BehaviorTestKit[Supervisor.SupervisorEvent] =
        BehaviorTestKit(Supervisor(Config().get.copy(maxRetries = 1)))
      testKit.expectEffectType[ReceiveTimeoutSet[IdleTimeout.type]]

      testKit.run(Start(new URL("https://www.example1.com/")))
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

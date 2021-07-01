/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.testkit.typed.Effect.{Spawned, TimerScheduled}
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import info.coverified.graphql.schema.CoVerifiedClientSchema.Source.SourceView
import sttp.model.Uri

import java.net.URL

class HostCrawlerSpec extends ActorSpec {

  private val source = SourceView(
    "-1",
    Some("www.example.com"),
    None,
    Some("http://www.example.com")
  )

  private val mockApiUrl = Uri("www.noapi.com")
  private val mockAuthSecret = ""

  "Initialization of HostCrawler" should {
    "set a timer and spawn Indexer and SiteScrapers" in {
      val supervisor =
        testKit.createTestProbe[Supervisor.SupervisorEvent]("supervisor")

      val behaviorKit: BehaviorTestKit[HostCrawler.HostCrawlerEvent] =
        BehaviorTestKit(
          HostCrawler(
            source,
            defaultConfig.scrapeParallelism,
            defaultConfig.scrapeInterval,
            defaultConfig.scrapeTimeout,
            mockApiUrl,
            mockAuthSecret,
            supervisor.ref
          )
        )

      val timer = behaviorKit.expectEffectType[TimerScheduled[_]]
      timer.delay shouldBe defaultConfig.scrapeInterval
      timer.msg shouldBe HostCrawler.Process
      timer.mode shouldBe TimerScheduled.FixedRateMode

      behaviorKit
        .expectEffectType[Spawned[_]]
        .childName shouldBe "Indexer_www.example.com"

      behaviorKit.expectEffectType[Spawned[_]].childName should startWith(
        "SiteScraper"
      )
    }
  }

  "Scrape message(s) received by HostCrawler" should {
    "result in a Scrape message sent to a SiteScraper after timer has been triggered" in {
      val supervisor =
        testKit.createTestProbe[Supervisor.SupervisorEvent]("supervisor")

      val behaviorKit: BehaviorTestKit[HostCrawler.HostCrawlerEvent] =
        BehaviorTestKit(
          HostCrawler(
            source,
            defaultConfig.scrapeParallelism,
            defaultConfig.scrapeInterval,
            defaultConfig.scrapeTimeout,
            mockApiUrl,
            mockAuthSecret,
            supervisor.ref
          )
        )

      behaviorKit.expectEffectType[TimerScheduled[_]]
      behaviorKit
        .expectEffectType[Spawned[_]]
        .childName shouldBe "Indexer_www.example.com"

      val siteScraperPool =
        behaviorKit.expectEffectType[Spawned[SiteScraper.SiteScraperEvent]]
      siteScraperPool.childName should startWith("SiteScraper")
      val siteScraperPoolInbox = behaviorKit.childInbox(siteScraperPool.ref)

      behaviorKit.run(HostCrawler.Scrape(new URL("https://www.example.com")))
      assert(!siteScraperPoolInbox.hasMessages)

      // simulate timer tick
      behaviorKit.run(HostCrawler.Process)
      siteScraperPoolInbox.expectMessage(
        SiteScraper.Scrape(new URL("https://www.example.com"), behaviorKit.ref)
      )
      assert(!siteScraperPoolInbox.hasMessages)

      // nothing else should have happened
      assert(!behaviorKit.hasEffects())
      supervisor.expectNoMessage()
    }

    "result in multiple Scrape messages sent to SiteScrapers after timer has been triggered" in {
      val supervisor =
        testKit.createTestProbe[Supervisor.SupervisorEvent]("supervisor")

      val behaviorKit: BehaviorTestKit[HostCrawler.HostCrawlerEvent] =
        BehaviorTestKit(
          HostCrawler(
            source,
            defaultConfig.scrapeParallelism,
            defaultConfig.scrapeInterval,
            defaultConfig.scrapeTimeout,
            mockApiUrl,
            mockAuthSecret,
            supervisor.ref
          )
        )

      behaviorKit.expectEffectType[TimerScheduled[_]]
      behaviorKit
        .expectEffectType[Spawned[_]]
        .childName shouldBe "Indexer_www.example.com"

      val siteScraperPool =
        behaviorKit.expectEffectType[Spawned[SiteScraper.SiteScraperEvent]]
      siteScraperPool.childName should startWith("SiteScraper")
      val siteScraperPoolInbox = behaviorKit.childInbox(siteScraperPool.ref)

      behaviorKit.run(
        HostCrawler.Scrape(new URL("https://www.example.com/page1"))
      )
      behaviorKit.run(
        HostCrawler.Scrape(new URL("https://www.example.com/page2"))
      )
      assert(!siteScraperPoolInbox.hasMessages)

      // simulate timer tick
      behaviorKit.run(HostCrawler.Process)
      siteScraperPoolInbox.expectMessage(
        SiteScraper
          .Scrape(new URL("https://www.example.com/page1"), behaviorKit.ref)
      )
      siteScraperPoolInbox.expectMessage(
        SiteScraper
          .Scrape(new URL("https://www.example.com/page2"), behaviorKit.ref)
      )
      assert(!siteScraperPoolInbox.hasMessages)

      // nothing else should have happened
      assert(!behaviorKit.hasEffects())
      supervisor.expectNoMessage()
    }
  }

  "A SiteScrapeFailure sent to HostCrawler" should {
    "result in a ScrapeFailure sent to Supervisor" in {
      val supervisor =
        testKit.createTestProbe[Supervisor.SupervisorEvent]("supervisor")

      val behaviorKit: BehaviorTestKit[HostCrawler.HostCrawlerEvent] =
        BehaviorTestKit(
          HostCrawler(
            source,
            defaultConfig.scrapeParallelism,
            defaultConfig.scrapeInterval,
            defaultConfig.scrapeTimeout,
            mockApiUrl,
            mockAuthSecret,
            supervisor.ref
          )
        )

      // clear effects
      behaviorKit.retrieveAllEffects()

      val mockThrowable = new RuntimeException("- test -")
      behaviorKit.run(
        HostCrawler.SiteScrapeFailure(
          new URL("https://www.example.com/page1"),
          mockThrowable
        )
      )

      supervisor.expectMessage(
        Supervisor.ScrapeFailure(
          new URL("https://www.example.com/page1"),
          mockThrowable
        )
      )

      // nothing else should have happened
      assert(!behaviorKit.hasEffects())
    }
  }
}

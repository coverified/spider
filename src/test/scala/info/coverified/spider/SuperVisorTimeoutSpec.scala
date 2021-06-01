/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.DeadLetter
import info.coverified.spider.Supervisor.{IdleTimeout, Start}
import info.coverified.spider.main.Config

import java.net.URL

class SuperVisorTimeoutSpec extends ActorSpec {
  "A Supervisor" should {
    "terminate the ActorSystem when receiving IdleTimeout" in {
      val supervisor = testKit.spawn(Supervisor(Config().get))
      val deadLetters = testKit.createDeadLetterProbe()

      // tell supervisor that receive timeout has been reached.
      supervisor.tell(IdleTimeout)

      // supervisor should now terminate the ActorSystem,
      supervisor ! Start(new URL("https://dead-letter.com"))

      // thus, message sent to HostCrawler is a dead letter
      deadLetters.expectMessageType[DeadLetter]
    }
  }
}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import info.coverified.spider.Supervisor.IdleTimeout
import info.coverified.spider.main.Config

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SupervisorTimeoutSpec extends ActorSpec {
  "An IdleTimeout message received by Supervisor" should {
    "terminate the ActorSystem" in {
      val supervisor = testKit.spawn(Supervisor(Config().get))

      supervisor.tell(IdleTimeout)

      Await.ready(testKit.system.whenTerminated, 5.seconds)
    }
  }
}

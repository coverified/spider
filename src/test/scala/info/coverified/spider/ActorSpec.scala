/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import info.coverified.spider.main.Config
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait ActorSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  protected val testKit: ActorTestKit = ActorTestKit()

  protected val defaultConfig: Config = Config().get

  override def afterAll(): Unit = testKit.shutdownTestKit()
}

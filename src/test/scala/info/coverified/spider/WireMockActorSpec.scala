/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

trait WireMockActorSpec
    extends ActorSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  protected val wireMockServer = new WireMockServer(
    wireMockConfig()
      .bindAddress("127.0.0.1")
      .dynamicPort() // pick a random free port
  )

  protected def port: Int = wireMockServer.port()

  override def beforeAll(): Unit = {
    wireMockServer.start()
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    wireMockServer.resetAll()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    // this can get called too early, maybe when tests are executed in parallel?
    wireMockServer.stop()
    super.afterAll()
  }
}

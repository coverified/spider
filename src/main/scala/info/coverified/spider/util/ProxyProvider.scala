/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import java.net.{InetSocketAddress, Proxy}
import scala.util.Random

object ProxyProvider {

  private val proxies: Seq[ProxyCfg] = Vector() // todo

  private final case class ProxyCfg(
      proxyType: Proxy.Type,
      proxyUrl: String,
      proxyPort: Int
  ) {
    def asProxy: Proxy =
      new Proxy(
        proxyType,
        InetSocketAddress.createUnresolved(proxyUrl, proxyPort)
      )
  }

  private val rand = new Random(100L)

  def randomProxy: Proxy = proxies(rand.nextInt(proxies.length)).asProxy

}

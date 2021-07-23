/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import crawlercommons.robots.{SimpleRobotRules, SimpleRobotRulesParser}
import info.coverified.spider.SiteScraper

import java.net.URL
import scala.util.Try

object RobotsTxtInspector {

  private val robotsTxtParser = new SimpleRobotRulesParser()

  def inspect(hostUrl: URL): Try[SimpleRobotRules] =
    Try(new URL(robotsTxtUrl(hostUrl)).openConnection).map(urlConnection => {
      val contentType = urlConnection.getHeaderField("Content-Type")
      val content = urlConnection.getInputStream.readAllBytes()
      urlConnection.getInputStream.close()
      robotsTxtParser.parseContent(
        robotsTxtUrl(hostUrl),
        content,
        contentType,
        SiteScraper.USER_AGENT
      )
    })

  private def robotsTxtUrl(url: URL): String =
    url.getProtocol + "://" + url.getHost + "/" + "robots.txt"

}

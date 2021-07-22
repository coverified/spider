/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import crawlercommons.robots.{SimpleRobotRules, SimpleRobotRulesParser}
import info.coverified.spider.SiteScraper

import java.net.URL

object RobotsTxtInspector {

  private val robotsTxtParser = new SimpleRobotRulesParser()

  def inspect(hostUrl: URL): SimpleRobotRules = {
    val url = new URL(robotsTxtUrl(hostUrl))
    val conn = url.openConnection
    val contentType = conn.getHeaderField("Content-Type")
    val content = conn.getInputStream.readAllBytes()
    conn.getInputStream.close()
    robotsTxtParser.parseContent(
      url.toString,
      content,
      contentType,
      SiteScraper.USER_AGENT
    )
  }

  private def robotsTxtUrl(url: URL): String =
    url.getProtocol + "://" + url.getHost + "/" + "robots.txt"

}

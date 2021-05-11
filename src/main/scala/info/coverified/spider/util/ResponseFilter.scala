/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import org.jsoup.Connection.Response

import scala.jdk.CollectionConverters._

object ResponseFilter {

  def keep(response: Response): Boolean =
    !headerHasNoIndex(response)

  private def headerHasNoIndex(response: Response): Boolean = {
    val xRobotsTag = "X-Robots-Tag"
    val noIndex = "noindex"
    val none = "none"
    val isNoIndex: String => Boolean = tags =>
      tags.toLowerCase.contains(noIndex) || tags.toLowerCase.contains(none)

    val headers = response.headers.asScala
    headers.get(xRobotsTag) match {
      case Some(tags) => isNoIndex(tags)
      case None =>
        headers.get(xRobotsTag.toUpperCase) match {
          case Some(tags) => isNoIndex(tags)
          case None =>
            headers.get(xRobotsTag.toLowerCase) match {
              case Some(tags) => isNoIndex(tags)
              case None       => false
            }
        }
    }
  }

}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

object UrlFilter {

  // note: not valid url check executed!
  def wantedUrl(url: String): Boolean = !isSearchResultPage(url)

  private def isSearchResultPage(url: String): Boolean =
    url.contains("!search?")

}
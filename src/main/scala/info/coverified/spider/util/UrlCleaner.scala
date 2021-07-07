/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import io.lemonlabs.uri.Url

object UrlCleaner {

  private val unwantedQueryParams =
    Vector("nn", "gtp", "imgdownload", "download")

  def cleanUrl(url: String): String = clean(Url.parse(url)).toStringPunycode

  private def clean(url: Url): Url =
    url
      .removeParams(unwantedQueryParams)
      .withFragment(None)
      .toAbsoluteUrl

}

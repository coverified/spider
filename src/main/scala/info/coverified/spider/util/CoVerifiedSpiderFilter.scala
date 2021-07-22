/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import info.coverified.spider.SiteScraper.SiteContent
import info.coverified.spider.util.ContentFilter.contentFilter

import org.jsoup.Connection.Response

object CoVerifiedSpiderFilter {

  implicit class RichResponse(private val response: Response) extends AnyVal {

    def withCoVerifiedHeaderFilter: Option[Response] =
      Option.when(ResponseFilter.keep(response))(response)

    def asFilteredSiteContent: Option[SiteContent] =
      contentFilter(response)

  }

}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

object UrlFilter {

  // note: not valid url check executed!
  def wantedUrl(url: String): Boolean =
    !isSearchResultPage(url) && !isForm(url) && !isImagePopup(url) && !isShoppingCard(
      url
    ) && !isAddToCard(url)

  private def isSearchResultPage(url: String): Boolean =
    url.contains("!search?")

  private def isForm(url: String): Boolean =
    url.contains("/SiteGlobals/Forms/")

  private def isImagePopup(url: String): Boolean =
    url.contains("?show=image") || url.contains("&show=image") || url.contains(
      "!show=image"
    )

  private def isShoppingCard(url: String): Boolean =
    url.matches(".*/warenkorb.*")

  private def isAddToCard(url: String): Boolean =
    url.matches(".*/addToCart.*")

}

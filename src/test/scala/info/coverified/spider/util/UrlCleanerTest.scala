/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

class UrlCleanerTest extends should.Matchers with AnyWordSpecLike {

  "The UrlCleaner" should {

    "remove 'nn' query params from urls correctly" in {
      val expectedResults = Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0"
      )

      Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?nn=260752&queryResultId=null&pageNo=0"
      ).map(UrlCleaner.cleanUrl)
        .zip(expectedResults)
        .foreach { case (res, exp) => res shouldBe exp }

    }

    "remove 'gtp' query params from urls correctly" in {
      val expectedResults = Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0"
      )

      Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0&gtp=14468_liste%3D55%26212490_list%3D44"
      ).map(UrlCleaner.cleanUrl)
        .zip(expectedResults)
        .foreach { case (res, exp) => res shouldBe exp }

    }

    "remove trailing # anchors from urls correctly " in {
      val expectedResults = Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0"
      )

      Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0#servicenav"
      ).map(UrlCleaner.cleanUrl)
        .zip(expectedResults)
        .foreach { case (res, exp) => res shouldBe exp }
    }

  }
}

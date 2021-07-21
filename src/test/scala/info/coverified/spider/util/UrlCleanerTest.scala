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
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0",
        "https://www.bmvi.de/DE/Themen/Digitales/mFund/Projekte/mfund-projekte.html"
      )

      Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0&gtp=14468_liste%3D55%26212490_list%3D44",
        "https://www.bmvi.de/DE/Themen/Digitales/mFund/Projekte/mfund-projekte.html?gtp=325998_liste%253D3"
      ).map(UrlCleaner.cleanUrl)
        .zip(expectedResults)
        .foreach { case (res, exp) => res shouldBe exp }

    }

    "remove trailing # anchors from urls correctly " in {
      val expectedResults = Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0",
        "https://www.bmvi.de/DE/Home/home.html",
        "https://www.bmvi.de/SharedDocs/DE/Artikel/LF/drohnen.html"
      )

      Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?queryResultId=null&pageNo=0#servicenav",
        "https://www.bmvi.de/DE/Home/home.html#main",
        "https://www.bmvi.de/SharedDocs/DE/Artikel/LF/drohnen.html?nn=12830#servicenav"
      ).map(UrlCleaner.cleanUrl)
        .zip(expectedResults)
        .foreach { case (res, exp) => res shouldBe exp }
    }

    "remove '&imgdownload' and '&download' from urls correctly" in {
      test(
        input = Vector(
          "https://www.bmvi.de/SharedDocs/DE/Bilder/Pressefotos/Wasser/scheuer-uferpromenade.jpg?__blob=normal&imgdownload=true",
          "https://www.bmvi.de/SharedDocs/DE/Bilder/Pressefotos/Wasser/scheuer-uferpromenade.jpg?__blob=normal&download=true",
          "https://www.bundesregierung.de/resource/blob/974430/1836290/78199806b8e92fd9c3eae406a741c886/2021-01-14-bkm-neustartpdf-data.pdf?download=1"
        ),
        expectedResults = Vector(
          "https://www.bmvi.de/SharedDocs/DE/Bilder/Pressefotos/Wasser/scheuer-uferpromenade.jpg?__blob=normal",
          "https://www.bmvi.de/SharedDocs/DE/Bilder/Pressefotos/Wasser/scheuer-uferpromenade.jpg?__blob=normal",
          "https://www.bundesregierung.de/resource/blob/974430/1836290/78199806b8e92fd9c3eae406a741c886/2021-01-14-bkm-neustartpdf-data.pdf"
        )
      )

    }

    "removes ';jsessionid=B76B148565D3F794C5E8CD3E97BBB15A.delivery1-replication' from urls correctly" in {
      test(
        input = Vector(
          "https://www.bmas.de/SharedDocs/Downloads/DE/Arbeitsschutz/arbeitsschutzbehorden.pdf;jsessionid=B76B148565D3F794C5E8CD3E97BBB15A.delivery1-replication?__blob=publicationFile&v=1"
        ),
        expectedResults = Vector(
          "https://www.bmas.de/SharedDocs/Downloads/DE/Arbeitsschutz/arbeitsschutzbehorden.pdf?__blob=publicationFile&v=1"
        )
      )
    }

    "removes '&shoppingCart' from urls correctly" in {
      test(
        input = Vector(
          "https://www.auswaertiges-amt.de/blueprint/servlet/aa-publication-order/addToCart?contentId=216862&shoppingCart=216654"
        ),
        expectedResults = Vector(
          "https://www.auswaertiges-amt.de/blueprint/servlet/aa-publication-order/addToCart?contentId=216862"
        )
      )
    }
  }

  private def test(
      input: Vector[String],
      expectedResults: Vector[String]
  ): Unit =
    input
      .map(UrlCleaner.cleanUrl)
      .zip(expectedResults)
      .foreach { case (res, exp) => res shouldBe exp }

}

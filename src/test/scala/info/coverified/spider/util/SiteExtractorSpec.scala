/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import org.jsoup.Jsoup
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.URL

class SiteExtractorSpec extends should.Matchers with AnyWordSpecLike {

  "The SiteExtractor" should {

    "identify valid canonical links correctly" in {

      val canonicalHtml =
        """<html>
          |<head>
          |    <link rel="canonical" href="https://example.com/page.html">
          |</head>
          |<body
          |    <link rel="canonical" href="https://example.com/page1.html">
          |</body>
          |</html>""".stripMargin

      val doc = Jsoup.parse(canonicalHtml)

      SiteExtractor.canonicalLinkFromHead(doc) shouldBe Some(
        new URL("https://example.com/page.html")
      )

    }

    "return none if no canonical links are available" in {
      val canonicalHtml =
        """<html>
          |<head>
          |</head>
          |</html>""".stripMargin

      val doc = Jsoup.parse(canonicalHtml)

      SiteExtractor.canonicalLinkFromHead(doc) shouldBe None
    }

  }

}

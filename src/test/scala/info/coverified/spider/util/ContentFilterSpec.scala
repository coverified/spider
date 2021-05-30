/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import org.jsoup.Jsoup
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable

class ContentFilterSpec extends should.Matchers with AnyWordSpecLike {

  "The SiteExtractor" should {

    "identify valid hreflang links in page heads correctly" in {
      val html =
        """<html>
          |<head>
          |    <link rel="alternate" hreflang="en" href="https://example.com/page_en.html">
          |    <link rel="alternate" hreflang="es" href="https://example.com/page_es.html">
          |    <link rel="canonical" href="https://example.com/page.html">
          |</head>
          |<body
          |</body>
          |</html>""".stripMargin

      val doc = Jsoup.parse(html)
      ContentFilter.extractHRefLang(doc) shouldBe mutable.Buffer(
        "https://example.com/page_en.html",
        "https://example.com/page_es.html"
      )
    }


    "identify valid canonical links in page heads correctly" in {
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

      ContentFilter.canonicalLinkFromHead(doc) shouldBe Some(
        "https://example.com/page.html"
      )
    }

    "return none if no links in page heads are available" in {
      val canonicalHtml =
        """<html>
          |<head>
          |    <link rel="stylesheet" href="https://example.com/page.css">
          |</head>
          |</html>""".stripMargin

      val doc = Jsoup.parse(canonicalHtml)

      ContentFilter.canonicalLinkFromHead(doc) shouldBe None
    }

    "return none if no canonical links in page heads are available" in {
      val canonicalHtml =
        """<html>
          |<head>
          |</head>
          |</html>""".stripMargin

      val doc = Jsoup.parse(canonicalHtml)

      ContentFilter.canonicalLinkFromHead(doc) shouldBe None
    }

    "identify valid canonical links in page body correctly" in {
      val canonicalHtml =
        """<html>
          |<head>
          |    <link rel="canonical" href="https://example.com/page.html">
          |</head>
          |<body
          |    <link rel="canonical" href="https://example.com/page1.html">
          |    <link rel="canonical" href="https://example.com/page2.html">
          |</body>
          |</html>""".stripMargin

      val doc = Jsoup.parse(canonicalHtml)

      ContentFilter.extractCanonicalLinksFromBody(doc) shouldBe mutable.Buffer(
        "https://example.com/page1.html",
        "https://example.com/page2.html"
      )
    }

    "return none if no canonical links in page body are available" in {
      val canonicalHtml =
        """<html>
          |<head>
          | <link rel="canonical" href="https://example.com/page1.html">
          |</head>
          |</html>""".stripMargin

      val doc = Jsoup.parse(canonicalHtml)

      ContentFilter.extractCanonicalLinksFromBody(doc) shouldBe mutable.Buffer.empty[String]
    }

  }

}

/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import info.coverified.spider.SiteScraper.SiteContent
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.PrivateMethodTester
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.URL
import scala.collection.mutable

class ContentFilterSpec
    extends should.Matchers
    with AnyWordSpecLike
    with PrivateMethodTester {

  private val addToIndexMethod = PrivateMethod[Boolean](Symbol("addToIndex"))
  private val extractContentInformationMethod =
    PrivateMethod[Option[SiteContent]](Symbol("extractContentInformation"))

  "The SiteExtractor" should {

    "extract content information correctly if url is canonical link" in {
      val html =
        """<html>
          |<head>
          |    <!-- hreflang links -->
          |    <link rel="alternate" hreflang="en" href="https://example.com/page_en.html">
          |    <link rel="alternate" hreflang="es" href="https://example.com/page_es.html">
          |
          |    <!-- canonical link -->
          |    <link rel="canonical" href="https://example.com/cat0/index.html">
          |
          |    <!-- irrelevant link -->
          |    <link rel="canonical" href="https://example.com/canonical1">
          |</head>
          |<body
          |    <!-- regular links (-> absolute) -->
          |    <a href="https://example.com/abs.html">absolute level link</a>
          |    <a href="same_level.html">same level link</a>
          |
          |    <!-- canonical links -->
          |    <link rel="canonical" href="https://example.com/page1.html">
          |    <link rel="canonical" href="https://example.com/page2.html">
          |</body>
          |</html>""".stripMargin

      val doc: Document =
        Jsoup.parse(html, "https://example.com/cat0/index.html")
      (ContentFilter invokePrivate extractContentInformationMethod(
        doc
      )) shouldBe Some(
        SiteContent(
          Some(new URL("https://example.com/cat0/index.html")),
          Set(
            new URL("https://example.com/cat0/same_level.html"),
            new URL("https://example.com/page_es.html"),
            new URL("https://example.com/page_en.html"),
            new URL("https://example.com/abs.html"),
            new URL("https://example.com/page2.html"),
            new URL("https://example.com/page1.html")
          )
        )
      )
    }

    "extract absolute links correctly" in {
      val canonicalHtml =
        """<html>
          |<head></head>
          |<body
          |    <a href="https://example.com/abs.html">absolute level link</a>
          |    <a href="same_level.html">same level link</a>
          |    <a href="../upper_level.html">upper level link</a>
          |    <a href="sub/sub_page.html">sub level link</a>
          |    <a href="|| invalid link ||">invalid link</a>
          |</body>
          |</html>""".stripMargin

      val doc =
        Jsoup.parse(canonicalHtml, "https://example.com/cat0/index.html")

      ContentFilter.extractAbsLinks(doc) shouldBe mutable.Buffer(
        "https://example.com/abs.html",
        "https://example.com/cat0/same_level.html",
        "https://example.com/upper_level.html",
        "https://example.com/cat0/sub/sub_page.html"
      )
    }

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

      ContentFilter.extractCanonicalLinksFromBody(doc) shouldBe mutable.Buffer
        .empty[String]
    }

  }

}

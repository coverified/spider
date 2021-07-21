/**
 * © 2021. CoVerified,
 * Diehl, Fetzer, Hiry, Kilian, Mayer, Schlittenbauer, Schweikert, Vollnhals, Weise GbR
 **/

package info.coverified.spider.util

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

class UrlFilterSpec extends should.Matchers with AnyWordSpecLike {

  "The UrlFilter" should {

    "identify search page urls as unwanted correctly" in {
      Vector(
        "https://www.example.de/867116!search?formState=eNptjztvwzAMhP9h7HsFT=*",
        "https://www.example.de/asd/aktuelles/867116!search?formState=eNptj00Lgk8BI3Ba4A&tf=867052:103440"
      ).foreach(UrlFilter.wantedUrl(_) shouldBe false)
    }

    "identify form page urls as unwanted correctly" in {
      Vector(
        "https://www.bmvi.de/SiteGlobals/Forms/Listen/EN/News-From-The-Ministry/News-From-The-Ministry_Formular.html?nn=260752&queryResultId=null&pageNo=0&gtp=14468_liste%3D55%26212490_list%3D44#servicenav"
      ).foreach(UrlFilter.wantedUrl(_) shouldBe false)
    }

    "identify valid urls as wanted correctly" in {
      Vector(
        "https://www.example.de/de/themen/222/123",
        "https://coverified.info"
      ).foreach(UrlFilter.wantedUrl(_) shouldBe true)
    }

    "identify show=image urls as unwanted correctly" in {
      Vector(
        "https://www.bpb.de/geschichte/deutsche-einheit/30-jahre-deutsche-einheit/314688/30-jahre-deutsche-einheit-bildergalerie?show=image&i=314693",
        "https://www.bpb.de/geschichte/deutsche-einheit/30-jahre-deutsche-einheit/314688/30-jahre-deutsche-einheit-bildergalerie?show=image&i=314725",
        "https://www.bpb.de/geschichte/deutsche-einheit/30-jahre-deutsche-einheit/314688/30-jahre-deutsche-einheit-bildergalerie?rel=noindex&type=galerie&show=image&i=314725",
        "https://www.bpb.de/gesellschaft/migration/afrikanische-diaspora/59569/brothers-keepers-story?type=galerie&show=image&i=59576"
      ).foreach(UrlFilter.wantedUrl(_) shouldBe false)
    }

    "identify '*/warenkorb/*' urls as unwanted correctly" in {
      Vector(
        "https://www.bpb.de/shop/warenkorb/?addpub=7720",
        "https://www.bpb.de/shop/warenkorb",
        "https://www.bpb.de/warenkorb/",
        "https://www.bpb.de/shop/warenkorb/7720"
      ).foreach(UrlFilter.wantedUrl(_) shouldBe false)
    }
  }
}

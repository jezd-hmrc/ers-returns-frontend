/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

import models.{ErsMetaData, SchemeInfo}
import org.joda.time.DateTime
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec

class ErsMetaDataHelperSpec extends UnitSpec with MockitoSugar with Matchers with GuiceOneAppPerSuite {
  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  override def fakeApplication() = new GuiceApplicationBuilder().configure(Map("play.i18n.langs"->List("en-GB","en","cy-GB", "cy"))).build()

  "ErsMetaDataHelper" should {

    implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

    def metaData(schemeName: String, schemeType: String) = ErsMetaData(
      SchemeInfo("XA1100000000000", DateTime.now, "001", "2015/16", schemeName, schemeType),
      "ipRef",
      Some("aoRef"),
      "empRef",
      Some("agentRef"),
      Some("sapNumber")
    )

    "produce the correct schemeInfo String for caching" in {

      ErsMetaDataHelper.getScreenSchemeInfo(metaData("CSOP 2015/16", "CSOP")) shouldBe
        "001 - CSOP - CSOP 2015/16 - XA1100000000000 - 2015 to 2016"
    }

    "produce the correct schemeInfo String for views" when {

      "scheme is CSOP" in {

        ErsMetaDataHelper.getSchemeInfoForView(metaData("CSOP 2015/16", "CSOP")) shouldBe
        s"${messages(s"ers.scheme.CSOP")} - ${messages(s"ers.scheme.CSOP.title")} - XA1100000000000 - 2015 ${messages("ers.taxYear.text")} 2016"
      }

      "scheme is EMI" in {

        ErsMetaDataHelper.getSchemeInfoForView(metaData("EMI", "EMI")) shouldBe
        s"${messages(s"ers.scheme.EMI")} - ${messages(s"ers.scheme.EMI.title")} - XA1100000000000 - 2015 ${messages("ers.taxYear.text")} 2016"
      }

      "scheme is SIP" in {

        ErsMetaDataHelper.getSchemeInfoForView(metaData("SIP", "SIP")) shouldBe
        s"${messages(s"ers.scheme.SIP")} - ${messages(s"ers.scheme.SIP.title")} - XA1100000000000 - 2015 ${messages("ers.taxYear.text")} 2016"
      }

      "scheme is SAYE" in {

        ErsMetaDataHelper.getSchemeInfoForView(metaData("SAYE", "SAYE")) shouldBe
        s"${messages(s"ers.scheme.SAYE")} - ${messages(s"ers.scheme.SAYE.title")} - XA1100000000000 - 2015 ${messages("ers.taxYear.text")} 2016"
      }

      "scheme is OTHER" in {

        ErsMetaDataHelper.getSchemeInfoForView(metaData("OTHER", "OTHER")) shouldBe
        s"${messages(s"ers.scheme.OTHER")} - ${messages(s"ers.scheme.OTHER.title")} - XA1100000000000 - 2015 ${messages("ers.taxYear.text")} 2016"
      }
    }
  }
}

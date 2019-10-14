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

package models

import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi}
import utils.Fixtures.ersRequestObject
import utils.DateUtils

class RequestObjectSpec extends WordSpec with MustMatchers with GuiceOneAppPerSuite {

  def messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

  "RequestObject" should {

    "return a page title with the correct format" in {

      val expected = s"${messages(s"ers.scheme.${ersRequestObject.getSchemeType}")} - ${messages(s"ers.scheme.title", ersRequestObject.getSchemeName)} - ${ersRequestObject.getSchemeReference} - ${DateUtils.getFullTaxYear(ersRequestObject.getTaxYear)}"

      ersRequestObject.getPageTitle mustBe expected
    }

    "return the correct scheme id" in {

      val expected = "1"

      ersRequestObject.getSchemeId mustBe expected
    }
  }
}

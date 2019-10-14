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

package controllers

import connectors.ErsConnector
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.mvc.LegacyI18nSupport
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import utils.Fixtures.ersRequestObject
import utils._

import scala.concurrent.Future

class AltAmendsControllerTest extends UnitSpec with ERSFakeApplicationConfig with OneAppPerSuite with MockitoSugar {

  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

  "calling Alterations Activity Page" should {

    def buildFakeAltAmendsPageController(groupSchemeActivityRes: Boolean = true, altAmendsActivityRes: Boolean = true,
                                         cacheRes: Boolean = true) = new AltAmendsController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      override val ersConnector: ErsConnector = mockErsConnector
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any()))
        .thenReturn(if (groupSchemeActivityRes) Future.successful(GroupSchemeInfo(Some(PageBuilder.OPTION_NO), Some(""))) else Future.failed(new Throwable))

      when(
        mockCacheUtil.fetch[AltAmendsActivity](refEq(CacheUtil.altAmendsActivity), anyString())(any(), any(), any())
      ).thenReturn(
        altAmendsActivityRes match {
          case true => Future.successful(AltAmendsActivity(PageBuilder.OPTION_NO))
          case _ => Future.failed(new NoSuchElementException)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.altAmendsActivity), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        cacheRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Throwable)
        }
      )

    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altActivityPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altActivityPage().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "direct to ers errors page if fetching groupSchemeActivity throws exception" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(groupSchemeActivityRes = false)
      val result = await(controllerUnderTest.showAltActivityPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      contentAsString(result) should include(messages("ers.global_errors.message"))
      contentAsString(result) shouldBe contentAsString(buildFakeAltAmendsPageController().getGlobalErrorPage)
    }

    "show alterations activity page with selection if fetching altAmendsActivity successful" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(altAmendsActivityRes = true)
      val result = controllerUnderTest.showAltActivityPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=no]").hasAttr("checked") shouldEqual true
      document.select("input[id=yes]").hasAttr("checked") shouldEqual false
    }

    "show alterations activity page with nothing selected if fetching altAmendsActivity fails" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(altAmendsActivityRes = false)
      val result = controllerUnderTest.showAltActivityPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=no]").hasAttr("checked") shouldEqual false
      document.select("input[id=yes]").hasAttr("checked") shouldEqual false
    }

    "give a redirect status (to company authentication frontend) on POST if user is not authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altActivitySelected() apply FakeRequest("GET", "")
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on POST if user is authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altActivitySelected() apply Fixtures.buildFakeRequestWithSessionIdCSOP("GET")
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a Ok status and stay on the same page if form errors and display the error" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val altActivityData = Map("" -> "")
      val form = RsFormMappings.altActivityForm.bind(altActivityData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltActivitySelected(ersRequestObject)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.OK
    }

    "give a redirect to summary declaration page if no form errors and NO selected" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val altActivityData = Map("altActivity" -> PageBuilder.OPTION_NO)
      val form = RsFormMappings.altActivityForm.bind(altActivityData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltActivitySelected(ersRequestObject)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.SummaryDeclarationController.summaryDeclarationPage().toString
    }

    "give a redirect to alterations amends page if no form errors and YES selected" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val altActivityData = Map("altActivity" -> PageBuilder.OPTION_YES)
      val form = RsFormMappings.altActivityForm.bind(altActivityData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltActivitySelected(ersRequestObject)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.AltAmendsController.altAmendsPage().toString
    }

    "direct to ers errors page if no form errors but unable to save to cache" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(cacheRes = false)
      val altActivityData = Map("altActivity" -> PageBuilder.OPTION_YES)
      val form = RsFormMappings.altActivityForm.bind(altActivityData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltActivitySelected(ersRequestObject)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.OK
      contentAsString(result) shouldBe contentAsString(buildFakeAltAmendsPageController().getGlobalErrorPage)
      contentAsString(result) should include(messages("ers.global_errors.message"))
    }

    "direct to ers errors page if no form errors and no sessionId" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(cacheRes = false)
      val altActivityData = Map("altActivity" -> PageBuilder.OPTION_YES)
      val form = RsFormMappings.altActivityForm.bind(altActivityData)
      val request = Fixtures.buildFakeRequest("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = await(controllerUnderTest.showAltActivitySelected(ersRequestObject)(Fixtures.buildFakeUser, request, hc))
      contentAsString(result) shouldBe contentAsString(buildFakeAltAmendsPageController().getGlobalErrorPage)
      contentAsString(result) should include(messages("ers.global_errors.message"))
    }
  }

  "calling Alterations Amends Page" should {

    def buildFakeAltAmendsPageController(groupSchemeActivityRes: Boolean = true, altAmendsRes: Boolean = true, cacheRes: Boolean = true) = new AltAmendsController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val altAmends = AltAmends(Some("0"), Some("1"), Some("1"), Some("1"), Some("0"))
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      override val ersConnector: ErsConnector = mockErsConnector
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[AltAmends](refEq(CacheUtil.ALT_AMENDS_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        altAmendsRes match {
          case true => Future.successful(altAmends)
          case _ => Future.failed(new NoSuchElementException)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.ALT_AMENDS_CACHE_CONTROLLER), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        cacheRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Throwable)
        }
      )

    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altAmendsPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altAmendsPage().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }


    "shows alterations amends page with no selection if fetching altAmends fails" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(altAmendsRes = false)
      val result = controllerUnderTest.showAltAmendsPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=alt-terms-check-box]").hasAttr("checked") shouldEqual false
      document.select("input[id=alt-eligibility-check-box]").hasAttr("checked") shouldEqual false
      document.select("input[id=alt-exchange-check-box]").hasAttr("checked") shouldEqual false
      document.select("input[id=variations-check-box]").hasAttr("checked") shouldEqual false
      document.select("input[id=other-check-box]").hasAttr("checked") shouldEqual false
    }

    "show alterations amends page with selection if fetching altAmends successful" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(altAmendsRes = true)
      val result = controllerUnderTest.showAltAmendsPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=alt-terms-check-box]").hasAttr("checked") shouldEqual false
      document.select("input[id=alt-eligibility-check-box]").hasAttr("checked") shouldEqual true
      document.select("input[id=alt-exchange-check-box]").hasAttr("checked") shouldEqual true
      document.select("input[id=variations-check-box]").hasAttr("checked") shouldEqual true
      document.select("input[id=other-check-box]").hasAttr("checked") shouldEqual false
    }


    "give a redirect status (to company authentication frontend) on POST if user is not authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altAmendsSelected().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on POST if user is authenticated" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val result = controllerUnderTest.altAmendsSelected().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a redirect status and stay on the same page if nothing selected" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val altAmendsData = Map("altAmendsTerms" -> "", "altAmendsEligibility" -> "", "altAmendsExchange" -> "", "altAmendsVariations" -> "", "altAmendsOther" -> "")
      val form = RsFormMappings.altAmendsForm.bind(altAmendsData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltAmendsSelected()(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.AltAmendsController.altAmendsPage().toString()
    }

    "give a redirect status and stay on the same page if form errors" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val altAmendsData = Map("altAmendsTerms" -> "X", "altAmendsEligibility" -> "", "altAmendsExchange" -> "", "altAmendsVariations" -> "", "altAmendsOther" -> "")
      val form = RsFormMappings.altAmendsForm.bind(altAmendsData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltAmendsSelected()(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.AltAmendsController.altAmendsPage().toString()
    }

    "direct to ers errors page if no form errors but unable to save to cache" in {
      val controllerUnderTest = buildFakeAltAmendsPageController(cacheRes = false)
      val altAmendsData = Map("altAmendsTerms" -> "1", "altAmendsEligibility" -> "1", "altAmendsExchange" -> "1", "altAmendsVariations" -> "1", "altAmendsOther" -> "1")
      val form = RsFormMappings.altActivityForm.bind(altAmendsData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltAmendsSelected()(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.OK
      contentAsString(result) shouldBe contentAsString(buildFakeAltAmendsPageController().getGlobalErrorPage)
      contentAsString(result) should include(messages("ers.global_errors.message"))
    }

    "give a redirect to summary declaration page if no form errors and selection applied" in {
      val controllerUnderTest = buildFakeAltAmendsPageController()
      val altAmendsData = Map("altAmendsTerms" -> "1", "altAmendsEligibility" -> "1", "altAmendsExchange" -> "1", "altAmendsVariations" -> "1", "altAmendsOther" -> "1")
      val form = RsFormMappings.altActivityForm.bind(altAmendsData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showAltAmendsSelected()(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.SummaryDeclarationController.summaryDeclarationPage().toString()
    }


  }

}

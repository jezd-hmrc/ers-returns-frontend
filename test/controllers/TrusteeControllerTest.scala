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

import akka.stream.Materializer
import connectors.ErsConnector
import models._
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import utils.Fixtures.ersRequestObject
import utils._

import scala.concurrent.Future

class TrusteeControllerTest extends UnitSpec with ERSFakeApplicationConfig with MockitoSugar with OneServerPerSuite {

  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  "calling Trustee Details Page" should {

    def buildFakeTrusteePageController(groupSchemeActivityRes: Boolean = true, trusteeDetailsRes: Boolean = true, cacheRes: Boolean = true) = new TrusteeController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val trustee = TrusteeDetails("Name", "1 The Street", None, None, None, Some("UK"), None)
      val trusteeList = List(trustee)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        groupSchemeActivityRes match {
          case true => Future.successful(GroupSchemeInfo(Some(PageBuilder.OPTION_NO), Some("")))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetch[TrusteeDetailsList](refEq(CacheUtil.TRUSTEES_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        trusteeDetailsRes match {
          case true => Future.successful(TrusteeDetailsList(trusteeList))
          case _ => Future.failed(new NoSuchElementException)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.TRUSTEES_CACHE), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        cacheRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Throwable)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeTrusteePageController()
      val result = controllerUnderTest.trusteeDetailsPage(10000).apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeTrusteePageController()
      val result = controllerUnderTest.trusteeDetailsPage(10000).apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "direct to ers errors page if fetching groupSchemeActivity throws exception" in {
      val controllerUnderTest = buildFakeTrusteePageController(groupSchemeActivityRes = false)
      contentAsString(await(controllerUnderTest.showTrusteeDetailsPage(ersRequestObject, 10000)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }

    "show alterations trustee details page with no data pre-filled" in {
      val controllerUnderTest = buildFakeTrusteePageController(groupSchemeActivityRes = true)
      val result = controllerUnderTest.showTrusteeDetailsPage(ersRequestObject, 10000)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
    }

    "give a redirect status (to company authentication frontend) on POST if user is not authenticated" in {
      val controllerUnderTest = buildFakeTrusteePageController()
      val result = controllerUnderTest.trusteeDetailsSubmit(10000) apply (FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on POST if user is authenticated" in {
      val controllerUnderTest = buildFakeTrusteePageController()
      val result = controllerUnderTest.trusteeDetailsSubmit(10000) apply (Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a OK status and stay on the same page if form errors" in {
      val controllerUnderTest = buildFakeTrusteePageController(groupSchemeActivityRes = true)
      val trusteeData = Map("" -> "")
      val form = RsFormMappings.trusteeDetailsForm.bind(trusteeData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showTrusteeDetailsSubmit(ersRequestObject, 10000)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.OK
    }

    "if form errors and if fetching groupSchemeActivity fails direct to ers errors page" in {
      val controllerUnderTest = buildFakeTrusteePageController(groupSchemeActivityRes = false)
      val trusteeData = Map("" -> "")
      val form = RsFormMappings.trusteeDetailsForm.bind(trusteeData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      contentAsString(await(controllerUnderTest.showTrusteeDetailsSubmit(ersRequestObject, 10000)(Fixtures.buildFakeUser, request, hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }

    "if no form errors with new trustee (index 10000) and fetch trustee details success" in {
      val controllerUnderTest = buildFakeTrusteePageController(trusteeDetailsRes = true, cacheRes = true)
      val trusteeData = Map("name" -> "Name", "addressLine1" -> "1 The Street", "addressLine2" -> "", "addressLine3" -> "", "addressLine4" -> "", "country" -> "UK", "postcode" -> "")
      val form = RsFormMappings.trusteeDetailsForm.bind(trusteeData)
      val request = Fixtures.buildFakeRequestWithSessionIdSIP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showTrusteeDetailsSubmit(ersRequestObject, 10000)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.TrusteeController.trusteeSummaryPage.toString()
    }

    "if no form errors with new trustee (index 10000) and fetch trustee details fails" in {
      val controllerUnderTest = buildFakeTrusteePageController(trusteeDetailsRes = false, cacheRes = true)
      val trusteeData = Map("name" -> "Name", "addressLine1" -> "1 The Street", "addressLine2" -> "", "addressLine3" -> "", "addressLine4" -> "", "country" -> "UK", "postcode" -> "")
      val form = RsFormMappings.trusteeDetailsForm.bind(trusteeData)
      val request = Fixtures.buildFakeRequestWithSessionIdSIP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showTrusteeDetailsSubmit(ersRequestObject, 10000)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.TrusteeController.trusteeSummaryPage.toString()
    }

    "if no form errors and fetch trustee details success for not updating an existing trustee (index 1) " in {
      val controllerUnderTest = buildFakeTrusteePageController(trusteeDetailsRes = true, cacheRes = true)
      val trusteeData = Map("name" -> "Name", "addressLine1" -> "1 The Street", "addressLine2" -> "", "addressLine3" -> "", "addressLine4" -> "", "country" -> "UK", "postcode" -> "")
      val form = RsFormMappings.trusteeDetailsForm.bind(trusteeData)
      val request = Fixtures.buildFakeRequestWithSessionIdSIP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showTrusteeDetailsSubmit(ersRequestObject, 1)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.TrusteeController.trusteeSummaryPage.toString()
    }

    "if no form errors and fetch trustee details success for updating a trustee (index 0) " in {
      val controllerUnderTest = buildFakeTrusteePageController(trusteeDetailsRes = true, cacheRes = true)
      val trusteeData = Map("name" -> "Name", "addressLine1" -> "1 The Street", "addressLine2" -> "", "addressLine3" -> "", "addressLine4" -> "", "country" -> "UK", "postcode" -> "")
      val form = RsFormMappings.trusteeDetailsForm.bind(trusteeData)
      val request = Fixtures.buildFakeRequestWithSessionIdSIP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showTrusteeDetailsSubmit(ersRequestObject, 0)(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.TrusteeController.trusteeSummaryPage.toString()
    }

  }

  "calling Delete Trustee" should {

    def buildFakeTrusteeController(groupSchemeActivityRes: Boolean = true, trusteeDetailsRes: Boolean = true, cacheRes: Boolean = true) = new TrusteeController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val trustee = TrusteeDetails("Name", "1 The Street", None, None, None, Some("UK"), None)
      val trusteeList = List(trustee)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        groupSchemeActivityRes match {
          case true => Future.successful(GroupSchemeInfo(Some(PageBuilder.OPTION_NO), Some("")))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetch[TrusteeDetailsList](refEq(CacheUtil.TRUSTEES_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        trusteeDetailsRes match {
          case true => Future.successful(TrusteeDetailsList(trusteeList))
          case _ => Future.failed(new NoSuchElementException)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.TRUSTEES_CACHE), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        cacheRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Throwable)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.deleteTrustee(10000).apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.deleteTrustee(10000).apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "throws exception if fetching trustee details direct to ers errors page" in {
      val controllerUnderTest = buildFakeTrusteeController(trusteeDetailsRes = false)
      contentAsString(await(controllerUnderTest.showDeleteTrustee(10000)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }

    "delete trustee for given index and redirect to trustee summary page" in {
      val controllerUnderTest = buildFakeTrusteeController(trusteeDetailsRes = true)
      val result = controllerUnderTest.showDeleteTrustee(0)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.TrusteeController.trusteeSummaryPage.toString()
    }

    "reconstruct trustee list and redirect to trustee summary page" in {
      val controllerUnderTest = buildFakeTrusteeController(trusteeDetailsRes = true)
      val result = controllerUnderTest.showDeleteTrustee(10)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.TrusteeController.trusteeSummaryPage.toString()
    }

  }

  "calling Edit Trustee" should {

    def buildFakeTrusteeController(groupSchemeActivityRes: Boolean = true, trusteeDetailsRes: Boolean = true, cacheRes: Boolean = true) = new TrusteeController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val trustee = TrusteeDetails("Name", "1 The Street", None, None, None, Some("UK"), None)
      val trusteeList = List(trustee)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        groupSchemeActivityRes match {
          case true => Future.successful(GroupSchemeInfo(Some(PageBuilder.OPTION_NO), Some("")))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetch[TrusteeDetailsList](refEq(CacheUtil.TRUSTEES_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        trusteeDetailsRes match {
          case true => Future.successful(TrusteeDetailsList(trusteeList))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.TRUSTEES_CACHE), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        cacheRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Throwable)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.editTrustee(10000).apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.editTrustee(10000).apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "direct to ers errors page if fetching group scheme activity fails" in {
      val controllerUnderTest = buildFakeTrusteeController(groupSchemeActivityRes = false, trusteeDetailsRes = false)
      contentAsString(await(controllerUnderTest.showEditTrustee(ersRequestObject, 10000)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }

    "direct to ers errors page if fetching trustee details list fails" in {
      val controllerUnderTest = buildFakeTrusteeController(groupSchemeActivityRes = true, trusteeDetailsRes = false)
      contentAsString(await(controllerUnderTest.showEditTrustee(ersRequestObject, 10000)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }

    "edit trustee for given index and display trustee summary page pre-filled" in {
      val controllerUnderTest = buildFakeTrusteeController(groupSchemeActivityRes = true, trusteeDetailsRes = true)
      val result = controllerUnderTest.showEditTrustee(ersRequestObject, 0)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
    }

    "traverse the trustee list and display trustee summary page" in {
      val controllerUnderTest = buildFakeTrusteeController(groupSchemeActivityRes = true, trusteeDetailsRes = true)
      val result = controllerUnderTest.showEditTrustee(ersRequestObject, 10)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
    }

  }

  "calling trustee summary page" should {

    def buildFakeTrusteeController(groupSchemeActivityRes: Boolean = true, trusteeDetailsRes: Boolean = true, cacheRes: Boolean = true) = new TrusteeController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val trustee = TrusteeDetails("Name", "1 The Street", None, None, None, Some("UK"), None)
      val trusteeList = List(trustee)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        groupSchemeActivityRes match {
          case true => Future.successful(GroupSchemeInfo(Some(PageBuilder.OPTION_NO), Some("")))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetch[TrusteeDetailsList](refEq(CacheUtil.TRUSTEES_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        trusteeDetailsRes match {
          case true => Future.successful(TrusteeDetailsList(trusteeList))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.TRUSTEES_CACHE), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        cacheRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Throwable)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.trusteeSummaryPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.trusteeSummaryPage().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "direct to ers errors page if fetching trustee details list fails" in {
      val controllerUnderTest = buildFakeTrusteeController(groupSchemeActivityRes = true, trusteeDetailsRes = false)
      contentAsString(await(controllerUnderTest.showTrusteeSummaryPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }

    "display trustee summary page pre-filled" in {
      val controllerUnderTest = buildFakeTrusteeController(groupSchemeActivityRes = true, trusteeDetailsRes = true)
      val result = controllerUnderTest.showTrusteeSummaryPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
    }


    "continue button gives a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.trusteeSummaryContinue().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "continue button give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.trusteeSummaryContinue().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "redirect to alterations activity page" in {
      val controllerUnderTest = buildFakeTrusteeController()
      val result = controllerUnderTest.continueFromTrusteeSummaryPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.SEE_OTHER
    }
  }
}

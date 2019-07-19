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
import config.ErsContextImpl
import connectors.ErsConnector
import metrics.Metrics
import models.{ErsMetaData, ErsSummary, SchemeInfo}
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.mvc.{Request, Result, Session}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.UnitSpec
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import views.html.confirmation

class ConfirmationPageControllerTest extends UnitSpec with ERSFakeApplicationConfig with MockitoSugar with BeforeAndAfterEach with OneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer
  implicit lazy val messages: Messages = Messages(Lang("en"), app.injector.instanceOf[MessagesApi])

  lazy val mockMetrics: Metrics = mock[Metrics]

  override def beforeEach() = {
    super.beforeEach()
    reset(mockMetrics)
  }

  "calling ConformationPage" should {

    def buildFakeConfirmationPageController(isNilReturn: String = "1", bundleRes: Boolean = true, allDataRes: Boolean = true, ersMetaRes: Boolean = true, presubmission: Int = 200, presubmissionSuccess: Boolean = true) = new ConfirmationPageController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", isNilReturn, None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      override val jsonParser: JsonParser = mock[JsonParser]
      override val metrics = mockMetrics
      val mockErsConnector: ErsConnector = mock[ErsConnector]

      when(
        mockErsConnector.connectToEtmpSummarySubmit(anyString(), any[JsValue]())(any(), any())
      ).thenReturn(
        bundleRes match {
          case true => Future.successful("Bundle12345")
          case _ => Future.failed(new RuntimeException)
        }

      )
      when(
        mockErsConnector.checkForPresubmission(any[SchemeInfo](), anyString())(any(), any())
      ).thenReturn(
        presubmissionSuccess match {
          case true => Future.successful(HttpResponse(presubmission))
          case _ => Future.failed(new RuntimeException)
        }
      )

      override val ersConnector: ErsConnector = mockErsConnector
      val mockCacheUtil: CacheUtil = mock[CacheUtil]

      when(
        mockCacheUtil.fetch[ErsMetaData](refEq(CacheUtil.ersMetaData), anyString())(any(), any(), any())
      ).thenReturn(
        ersMetaRes match {
          case true => Future.successful(rsc)
          case _ => Future.failed(new RuntimeException)
        }
      )
      when(
        mockCacheUtil.getAllData(anyString(), any[ErsMetaData]())(any(), any(), any())
      ).thenReturn(
        allDataRes match {
          case true => Future.successful(ersSummary)
          case _ => Future.failed(new RuntimeException)
        }
      )
      when(
        mockCacheUtil.fetch[String](refEq(CacheUtil.VALIDATED_SHEEETS), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful("")
      )

      override val cacheUtil: CacheUtil = mockCacheUtil

      override def saveAndSubmit(alldata: ErsSummary, all: ErsMetaData, bundle: String)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
        Future(Ok)
      }

    }

    "give a redirect status (to company authentication frontend) if user is not authenticated" in {
      val controllerUnderTest = buildFakeConfirmationPageController()
      val result = controllerUnderTest.confirmationPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK if user is authenticated" in {
      val controllerUnderTest = buildFakeConfirmationPageController()
      val result = controllerUnderTest.confirmationPage().apply(Fixtures.buildFakeRequestWithSessionId("GET"))
      status(result) shouldBe Status.SEE_OTHER

    }

    "show user panel for confirmation page" in {
      val controllerUnderTest = buildFakeConfirmationPageController()
      val result = confirmation("8 April 2016, 4:50pm", "", "", "")(Fixtures.buildFakeRequestWithSessionId("GET"), ErsContextImpl, messages)
      contentAsString(result)  should include("Help improve digital services by joining the HMRC user panel (opens in new window)")
      contentAsString(result)  should include("No thanks")

    }

    "direct to ers errors page if bundle request throws exception" in {
      val controllerUnderTest = buildFakeConfirmationPageController(bundleRes = false)
      val result = await(controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

    "direct to ers errors page if fetching all data throws exception" in {
      val controllerUnderTest = buildFakeConfirmationPageController(allDataRes = false)
      val result = await(controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

    "returns OK if there are no exceptions thrown and confirmaton date time already exists" in {
      val mockedSession = mock[Session]
      val mockedRequest = mock[Request[AnyRef]]
      when(
        mockedSession.get(anyString())
      ).thenReturn(
        Some("")
      )
      when(
        mockedRequest.session
      ).thenReturn(
        mockedSession
      )
      val controllerUnderTest = buildFakeConfirmationPageController(isNilReturn = "2")
      val result = controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, mockedRequest, hc)
      status(result) shouldBe Status.OK
    }

    "direct to ers errors page if fetching metadata throws exception" in {
      val controllerUnderTest = buildFakeConfirmationPageController(ersMetaRes = false)
      val result = await(controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

    "returns OK for NilReturn if there are no exceptions thrown" in {
      val request = FakeRequest().withSession("screenSchemeInfo" -> "10 MAR 2016")
      val controllerUnderTest = buildFakeConfirmationPageController(isNilReturn = "2")
      val result = controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.OK
    }

    "returns OK for submission if there are no exceptions thrown" in {
      val controllerUnderTest = buildFakeConfirmationPageController()
      val result = controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }

    "direct to ers errors page if check for presubmission returns status != OK" in {
      val controllerUnderTest = buildFakeConfirmationPageController(presubmission = 500)
      val result = await(controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

    "direct to ers errors page if check for presubmission fails" in {
      val controllerUnderTest = buildFakeConfirmationPageController(presubmissionSuccess = false)
      val result = await(controllerUnderTest.showConfirmationPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

  }

  "calling saveAndSubmit" should {
    val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")
    val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
    val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)

    def buildFakeConfirmationPageController(saveMetadataRes: Boolean = true, saveMetadataResponse: Int = 200, submitReturnToBackendResponse: Int = 200) = new ConfirmationPageController {
      override val jsonParser: JsonParser = mock[JsonParser]
      override val metrics = mockMetrics
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      when(
        mockErsConnector.saveMetadata(any[ErsSummary]())(any(), any())
      ).thenReturn(
        saveMetadataRes match {
          case true => Future.successful(HttpResponse(saveMetadataResponse))
          case _ => Future.failed(new RuntimeException)
        }
      )
      when(
        mockErsConnector.submitReturnToBackend(any[ErsSummary]())(any(), any())
      ).thenReturn(
        HttpResponse(submitReturnToBackendResponse)
      )
      override val ersConnector: ErsConnector = mockErsConnector
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "returns OK for Submission if there are no exceptions thrown - submit to backend successful" in {
      val controllerUnderTest = buildFakeConfirmationPageController()
      val result = controllerUnderTest.saveAndSubmit(ersSummary, ersSummary.metaData, ersSummary.bundleRef)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }

    "returns OK for Submission if there are no exceptions thrown - submit to backend fails" in {
      val controllerUnderTest = buildFakeConfirmationPageController(submitReturnToBackendResponse = 500)
      val result = controllerUnderTest.saveAndSubmit(ersSummary, ersSummary.metaData, ersSummary.bundleRef)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }

    "returns OK for Submission if there are no exceptions thrown - save meta data to backend fails" in {
      val controllerUnderTest = buildFakeConfirmationPageController(saveMetadataResponse = 500)
      val result = await(controllerUnderTest.saveAndSubmit(ersSummary, ersSummary.metaData, ersSummary.bundleRef)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

    "displays the global error page for Submission if save meta data to backend throws an exception" in {
      val controllerUnderTest = buildFakeConfirmationPageController(saveMetadataRes = false)
      val result = await(controllerUnderTest.saveAndSubmit(ersSummary, ersSummary.metaData, ersSummary.bundleRef)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include("Service unavailable")
    }

  }


}

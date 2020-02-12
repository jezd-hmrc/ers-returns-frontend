/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.NoSuchElementException

import akka.stream.Materializer
import connectors.ErsConnector
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import utils.Fixtures.ersRequestObject
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}

import scala.concurrent.Future

class ReportableEventsControllerTest extends UnitSpec with ERSFakeApplicationConfig with GuiceOneAppPerSuite with MockitoSugar {

  val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))
  implicit val requests: Request[_] = FakeRequest()
  implicit lazy val mat: Materializer = app.materializer

  "calling Reportable Events Page" should {

    def buildFakeReportableEventsController(ersMetaDataRes: Boolean = true,
																						ersMetaDataCachedOk: Boolean = true,
																						sapRequestRes: Boolean = true,
																						schemeOrganiserDetailsRes: Boolean = true,
																						schemeOrganiserDataCached: Boolean = false,
																						reportableEventsRes: Boolean = true,
																						fileTypeRes: Boolean = true,
																						altAmendsActivityRes: Boolean = true,
																						cacheRes: Boolean = true
																					 ): ReportableEventsController = new ReportableEventsController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val ersMetaData = ErsMetaData(schemeInfo, "300.300.300.300", None, "", None, None)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil
      override val ersConnector: ErsConnector = mockErsConnector

      when(
        mockErsConnector.connectToEtmpSapRequest(anyString())(any(), any())
      ).thenReturn(
        sapRequestRes match {
          case true => Future.successful("1234567890")
          case _ => Future.failed(new RuntimeException)
        }
      )
      when(
        mockCacheUtil.fetch[ReportableEvents](refEq(CacheUtil.reportableEvents), anyString())(any(), any(), any())
      ).thenReturn(
        reportableEventsRes match {
          case true => Future.successful(ReportableEvents(Some(PageBuilder.OPTION_NIL_RETURN)))
          case _ => Future.failed(new NoSuchElementException)
        }
      )
      when(
        mockCacheUtil.fetch[ErsMetaData](refEq(CacheUtil.ersMetaData), anyString())(any(), any(), any())
      ).thenReturn(
        ersMetaDataRes match {
          case true => Future.successful(ersMetaData)
          case _ => Future.failed(new NoSuchElementException)
        }
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.ersMetaData), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        ersMetaDataCachedOk match {
          case true => Future.successful(null)
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetch[SchemeOrganiserDetails](refEq(CacheUtil.SCHEME_ORGANISER_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        schemeOrganiserDetailsRes match {
          case true => {
            schemeOrganiserDataCached match {
              case true => Future.successful(SchemeOrganiserDetails("Name", Fixtures.companyName, None, None, None, None, None, None, None))
              case _ => Future.successful(SchemeOrganiserDetails("", "", None, None, None, None, None, None, None))
            }
          }
          case _ => Future.failed(new NoSuchElementException)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val result = controllerUnderTest.reportableEventsPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val result = controllerUnderTest.reportableEventsPage().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "direct to ers errors page if fetching ersMetaData throws exception" in {
      val controllerUnderTest = buildFakeReportableEventsController(ersMetaDataRes = false)
      val result = controllerUnderTest.updateErsMetaData(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
    }

    "direct to ers errors page if saving ersMetaData throws exception" in {
      val controllerUnderTest = buildFakeReportableEventsController(ersMetaDataCachedOk = false)
      val result = controllerUnderTest.updateErsMetaData(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
    }

    "show blank reportable events page if fetching reportableEvents throws exception" in {
      val controllerUnderTest = buildFakeReportableEventsController(reportableEventsRes = false)
      val result = await(controllerUnderTest.showReportableEventsPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=upload-spreadsheet-radio-button]").hasAttr("checked") shouldEqual false
      document.select("input[id=nil-return-radio-button]").hasAttr("checked") shouldEqual false
    }

    "show reportable events page with NO selected" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val result = await(controllerUnderTest.showReportableEventsPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=upload-spreadsheet-radio-button]").hasAttr("checked") shouldEqual false
      document.select("input[id=nil-return-radio-button]").hasAttr("checked") shouldEqual true
    }

  }


  "calling Reportable Events Selected Page" should {

    def buildFakeReportableEventsController(ersMetaDataRes: Boolean = true,
																						ersMetaDataCachedOk: Boolean = true,
																						sapRequestRes: Boolean = true,
																						schemeOrganiserDetailsRes: Boolean = true,
																						schemeOrganiserDataCached: Boolean = false,
																						reportableEventsRes: Boolean = true,
																						fileTypeRes: Boolean = true,
																						altAmendsActivityRes: Boolean = true,
																						cacheRes: Boolean = true
																					 ): ReportableEventsController = new ReportableEventsController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val ersMetaData = ErsMetaData(schemeInfo, "300.300.300.300", None, "", None, None)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil
      override val ersConnector: ErsConnector = mockErsConnector

      when(mockErsConnector.connectToEtmpSapRequest(anyString())(any(), any())
      ).thenReturn(
        if (sapRequestRes) {
					Future.successful("1234567890")
				} else {
					Future.failed(new RuntimeException)
				}
      )
      when(
        mockCacheUtil.fetch[ReportableEvents](refEq(CacheUtil.reportableEvents), any())(any(), any(), any())
      ).thenReturn(
        if (reportableEventsRes) {
					Future.successful(ReportableEvents(Some(PageBuilder.OPTION_NIL_RETURN)))
				} else {
					Future.failed(new NoSuchElementException)
				}
      )
      when(
        mockCacheUtil.fetch[ErsMetaData](refEq(CacheUtil.ersMetaData), any())(any(), any(), any())
      ).thenReturn(
        if (ersMetaDataRes) {
					Future.successful(ersMetaData)
				} else {
					Future.failed(new NoSuchElementException)
				}
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.reportableEvents), any(), any())(any(), any(), any())
      ).thenReturn(
        if (ersMetaDataCachedOk) {
					Future.successful(null)
				} else {
					Future.failed(new Exception)
				}
      )
      when(
        mockCacheUtil.fetch[SchemeOrganiserDetails](refEq(CacheUtil.SCHEME_ORGANISER_CACHE), any())(any(), any(), any())
      ).thenReturn(
        if (schemeOrganiserDetailsRes) {
					if (schemeOrganiserDataCached) {
						Future.successful(SchemeOrganiserDetails("Name", Fixtures.companyName, None, None, None, None, None, None, None))
					} else {
						Future.successful(SchemeOrganiserDetails("", "", None, None, None, None, None, None, None))
					}
				} else {
					Future.failed(new NoSuchElementException)
				}
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest: ReportableEventsController = buildFakeReportableEventsController()
      val result = controllerUnderTest.reportableEventsSelected().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest: ReportableEventsController = buildFakeReportableEventsController()
      val result = controllerUnderTest.reportableEventsSelected().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "if nothing selected give a status of OK and show the reportable events page displaying form errors" in {
      val controllerUnderTest: ReportableEventsController = buildFakeReportableEventsController()
      val reportableEventsData = Map("" -> "")
      val form = _root_.models.RsFormMappings.chooseForm.bind(reportableEventsData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showReportableEventsSelected(ersRequestObject)(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.OK
    }

    "give a redirect status to the Check File Type Page if YES selected for Reportable events" in {
      val controllerUnderTest: ReportableEventsController = buildFakeReportableEventsController()
      val form = "isNilReturn" -> PageBuilder.OPTION_UPLOAD_SPREEDSHEET
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form)
      val result = controllerUnderTest.showReportableEventsSelected(ersRequestObject)(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers("Location") shouldBe routes.CheckFileTypeController.checkFileTypePage().toString
    }

    "give a redirect status to the Scheme Organiser Page if NO selected for Reportable events" in {
      val controllerUnderTest: ReportableEventsController = buildFakeReportableEventsController()
      val form = "isNilReturn" -> PageBuilder.OPTION_NIL_RETURN
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form)
      val result = controllerUnderTest.showReportableEventsSelected(ersRequestObject)(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers("Location") shouldBe routes.SchemeOrganiserController.schemeOrganiserPage().toString
    }

    "direct to ers errors page if fetching reportableEvents throws exception" in {
      val controllerUnderTest: ReportableEventsController = buildFakeReportableEventsController(ersMetaDataCachedOk = false)
      val form = "isNilReturn" -> PageBuilder.OPTION_NIL_RETURN
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form)
      val result = controllerUnderTest.showReportableEventsSelected(ersRequestObject)(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.OK
      contentAsString(result) should include(messages("ers.global_errors.message"))
      contentAsString(result) shouldBe contentAsString(buildFakeReportableEventsController().getGlobalErrorPage)
    }

  }

}

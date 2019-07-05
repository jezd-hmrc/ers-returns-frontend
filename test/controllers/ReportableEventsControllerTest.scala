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

import java.util.NoSuchElementException

import akka.stream.Materializer
import connectors.ErsConnector
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}

import scala.concurrent.Future

class ReportableEventsControllerTest extends UnitSpec with ERSFakeApplicationConfig with OneAppPerSuite with MockitoSugar {

  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  "calling Reportable Events Page" should {

    def buildFakeReportableEventsController(ersMetaDataRes: Boolean = true, ersMetaDataCachedOk: Boolean = true, sapRequestRes: Boolean = true, schemeOrganiserDetailsRes: Boolean = true, schemeOrganiserDataCached: Boolean = false, reportableEventsRes: Boolean = true, fileTypeRes: Boolean = true, altAmendsActivityRes: Boolean = true, cacheRes: Boolean = true) = new ReportableEventsController {

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
      val result = controllerUnderTest.updateErsMetaData()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
    }

    "direct to ers errors page if saving ersMetaData throws exception" in {
      val controllerUnderTest = buildFakeReportableEventsController(ersMetaDataCachedOk = false)
      val result = controllerUnderTest.updateErsMetaData()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
    }

    "show blank reportable events page if fetching reportableEvents throws exception" in {
      val controllerUnderTest = buildFakeReportableEventsController(reportableEventsRes = false)
      val result = await(controllerUnderTest.showReportableEventsPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=upload-spreadsheet-radio-button]").hasAttr("checked") shouldEqual false
      document.select("input[id=nil-return-radio-button]").hasAttr("checked") shouldEqual false
    }

    "show reportable events page with NO selected" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val result = await(controllerUnderTest.showReportableEventsPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=upload-spreadsheet-radio-button]").hasAttr("checked") shouldEqual false
      document.select("input[id=nil-return-radio-button]").hasAttr("checked") shouldEqual true
    }

  }


  "calling Reportable Events Selected Page" should {

    def buildFakeReportableEventsController(ersMetaDataRes: Boolean = true, ersMetaDataCachedOk: Boolean = true, sapRequestRes: Boolean = true, schemeOrganiserDetailsRes: Boolean = true, schemeOrganiserDataCached: Boolean = false, reportableEventsRes: Boolean = true, fileTypeRes: Boolean = true, altAmendsActivityRes: Boolean = true, cacheRes: Boolean = true) = new ReportableEventsController {

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
        mockCacheUtil.cache(refEq(CacheUtil.reportableEvents), anyString(), anyString())(any(), any(), any())
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
      val result = controllerUnderTest.reportableEventsSelected().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val result = controllerUnderTest.reportableEventsSelected().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "if nothing selected give a status of OK and show the reportable events page displaying form errors" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val reportableEventsData = Map("" -> "")
      val form = _root_.models.RsFormMappings.chooseForm.bind(reportableEventsData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showReportableEventsSelected()(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.OK
    }

    "give a redirect status to the Check File Type Page if YES selected for Reportable events" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val form = "isNilReturn" -> PageBuilder.OPTION_UPLOAD_SPREEDSHEET
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form)
      val result = controllerUnderTest.showReportableEventsSelected()(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.CheckFileTypeController.checkFileTypePage().toString()
    }

    "give a redirect status to the Scheme Organiser Page if NO selected for Reportable events" in {
      val controllerUnderTest = buildFakeReportableEventsController()
      val form = "isNilReturn" -> PageBuilder.OPTION_NIL_RETURN
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form)
      val result = controllerUnderTest.showReportableEventsSelected()(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.SchemeOrganiserController.schemeOrganiserPage().toString()
    }

    "direct to ers errors page if fetching reportableEvents throws exception" in {
      val controllerUnderTest = buildFakeReportableEventsController(ersMetaDataCachedOk = false)
      val form = "isNilReturn" -> PageBuilder.OPTION_NIL_RETURN
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form)
      val result = controllerUnderTest.showReportableEventsSelected()(Fixtures.buildFakeUser, request)
      status(result) shouldBe Status.OK
      contentAsString(result) should include("Service unavailable")
      contentAsString(result) shouldBe contentAsString(buildFakeReportableEventsController().getGlobalErrorPage)
    }

  }

}

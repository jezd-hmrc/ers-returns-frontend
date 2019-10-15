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
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}
import utils.Fixtures.fakeRequestToRequestWithSchemeInfo

import scala.concurrent.Future

class SchemeOrganiserControllerTest extends UnitSpec with OneAppPerSuite with ERSFakeApplicationConfig with MockitoSugar {

  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit val mat: Materializer = app.materializer

  "calling Scheme Organiser Page" should {

    def buildFakeSchemeOrganiserController(groupSchemeActivityRes: Boolean = true, schemeOrganiserDetailsRes: Boolean = true, schemeOrganiserDataCached: Boolean = false, reportableEventsRes: Boolean = true, fileTypeRes: Boolean = true, altAmendsActivityRes: Boolean = true, cacheRes: Boolean = true) = new SchemeOrganiserController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[ReportableEvents](refEq(CacheUtil.reportableEvents), anyString())(any(), any(), any())
      ).thenReturn(
        reportableEventsRes match {
          case true => Future.successful(ReportableEvents(Some(PageBuilder.OPTION_NO)))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetchOption[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        fileTypeRes match {
          case true => Future.successful(Some(CheckFileType(Some(PageBuilder.OPTION_CSV))))
          case _ => Future.failed(new NoSuchElementException)
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
      val controllerUnderTest = buildFakeSchemeOrganiserController()
      val result: Future[Result] = controllerUnderTest.schemeOrganiserPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController()
      val result: Future[Result] = controllerUnderTest.schemeOrganiserPage().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "direct to ers errors page if fetching reportableEvents throws exception" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController(reportableEventsRes = false)
      val result: Future[Result] = await(controllerUnderTest.showSchemeOrganiserPage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      contentAsString(result) should include(messages("ers.global_errors.message"))
      contentAsString(result) shouldBe contentAsString(buildFakeSchemeOrganiserController().getGlobalErrorPage)
    }

    "show blank scheme organiser page if fetching file type from cache fails" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController(fileTypeRes = false)
      val result: Future[Result] = controllerUnderTest.showSchemeOrganiserPage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=company-name]").hasText shouldEqual false
      document.select("input[id=address-line-1]").hasText shouldEqual false
    }

    "show blank scheme organiser page if fetching scheme organiser details from cache fails" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController(schemeOrganiserDetailsRes = false)
      val result: Future[Result] = controllerUnderTest.showSchemeOrganiserPage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=company-name]").hasText shouldEqual false
      document.select("input[id=address-line-1]").hasText shouldEqual false
    }

    "show filled out scheme organiser page if fetching scheme organiser details from cache is successful" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController(schemeOrganiserDataCached = true)
      val result: Future[Result] = controllerUnderTest.showSchemeOrganiserPage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=companyName]").`val`() shouldEqual "Name"
      document.select("input[id=addressLine1]").`val`() shouldEqual Fixtures.companyName
    }

  }


  "calling Scheme Organiser Submit Page" should {

    def buildFakeSchemeOrganiserController(schemeOrganiserDetailsRes: Boolean = true, schemeOrganiserDataCached: Boolean = false, reportableEventsRes: Boolean = true, fileTypeRes: Boolean = true, altAmendsActivityRes: Boolean = true, schemeOrganiserDataCachedOk: Boolean = true) = new SchemeOrganiserController {

      val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "CSOP 2015/16", "CSOP")
      val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
      val ersSummary = ErsSummary("testbundle", "1", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
      val mockErsConnector: ErsConnector = mock[ErsConnector]
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil

      when(
        mockCacheUtil.fetch[ReportableEvents](refEq(CacheUtil.reportableEvents), anyString())(any(), any(), any())
      ).thenReturn(
        reportableEventsRes match {
          case true => Future.successful(ReportableEvents(Some(PageBuilder.OPTION_NO)))
          case _ => Future.failed(new Exception)
        }
      )
      when(
        mockCacheUtil.fetchOption[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        fileTypeRes match {
          case true => Future.successful(Some(CheckFileType(Some(PageBuilder.OPTION_CSV))))
          case _ => Future.failed(new NoSuchElementException)
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
      when(
        mockCacheUtil.cache(refEq(CacheUtil.SCHEME_ORGANISER_CACHE), anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        schemeOrganiserDataCachedOk match {
          case true => Future.successful(null)
          case _ => Future.failed(new Exception)
        }
      )

    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController()
      val result: Future[Result] = controllerUnderTest.schemeOrganiserSubmit().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController()
      val result: Future[Result] = controllerUnderTest.schemeOrganiserSubmit().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a Ok status and stay on the same page if form errors and display the error" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController()
      val schemeOrganiserData = Map("" -> "")
      val form = RsFormMappings.schemeOrganiserForm.bind(schemeOrganiserData)
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result: Future[Result] = controllerUnderTest.showSchemeOrganiserSubmit()(Fixtures.buildFakeAuthContext, request, hc)
      status(result) shouldBe Status.OK
    }

    "give a redirect status on POST if no form errors" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController()
      val schemeOrganiserData = Map("companyName" -> Fixtures.companyName, "addressLine1" -> "Add1", "addressLine" -> "Add2", "addressLine3" -> "Add3", "addressLine1" -> "Add4", "postcode" -> "AA11 1AA", "country" -> "United Kingdom", "companyReg" -> "AB123456", "corporationRef" -> "1234567890")
      val form = _root_.models.RsFormMappings.schemeOrganiserForm.bind(schemeOrganiserData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showSchemeOrganiserSubmit()(Fixtures.buildFakeAuthContext, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers("Location") shouldBe routes.GroupSchemeController.groupSchemePage().url
    }

    "direct to ers errors page if saving scheme organiser data throws exception" in {
      val controllerUnderTest = buildFakeSchemeOrganiserController(schemeOrganiserDataCachedOk = false)
      val schemeOrganiserData = Map("companyName" -> Fixtures.companyName, "addressLine1" -> "Add1", "addressLine" -> "Add2", "addressLine3" -> "Add3", "addressLine1" -> "Add4", "postcode" -> "AA11 1AA", "country" -> "United Kingdom", "companyReg" -> "AB123456", "corporationRef" -> "1234567890")
      val form = _root_.models.RsFormMappings.schemeOrganiserForm.bind(schemeOrganiserData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showSchemeOrganiserSubmit()(Fixtures.buildFakeAuthContext, request, hc)
      contentAsString(result) should include(messages("ers.global_errors.message"))
      contentAsString(result) shouldBe contentAsString(buildFakeSchemeOrganiserController().getGlobalErrorPage)
    }

  }

}

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
import models.{CheckFileType, RsFormMappings}
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
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}

import scala.concurrent.Future

class CheckFileTypeControllerTest extends UnitSpec with OneAppPerSuite with ERSFakeApplicationConfig with MockitoSugar {

  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))
  implicit val requests: Request[_] = FakeRequest()

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  "Check File Type Page GET" should {

    def buildFakeCheckingServiceController(fileTypeRes: Boolean = true) = new CheckFileTypeController {
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil
      when(
        mockCacheUtil.fetch[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE), any())(any(), any(), any())
      ).thenReturn(
        fileTypeRes match {
          case true => Future.successful(CheckFileType(Some(PageBuilder.OPTION_CSV)))
          case _ => Future.failed(new NoSuchElementException)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeCheckingServiceController()
      val result = controllerUnderTest.checkFileTypePage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "gives a call to showCheckFileTypePage if user is authenticated" in {
      val controllerUnderTest = buildFakeCheckingServiceController()
      val result = controllerUnderTest.checkFileTypePage().apply(Fixtures.buildFakeRequestWithSessionId("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK if fetch successful and shows check file type page with file type selected" in {
      val controllerUnderTest = buildFakeCheckingServiceController()
      val result = controllerUnderTest.showCheckFileTypePage(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=csv]").hasAttr("checked") shouldEqual true
      document.select("input[id=ods]").hasAttr("checked") shouldEqual false
    }

    "give a status OK if fetch fails then show check file type page with nothing selected" in {
      val controllerUnderTest = buildFakeCheckingServiceController(fileTypeRes = false)
      val result = controllerUnderTest.showCheckFileTypePage(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=csv]").hasAttr("checked") shouldEqual false
      document.select("input[id=ods]").hasAttr("checked") shouldEqual false
    }

  }

  "Check File Type Page POST" should {

    def buildFakeCheckingServiceController(fileTypeRes: Boolean = true) = new CheckFileTypeController {
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil
      when(
        mockCacheUtil.cache(refEq(CacheUtil.FILE_TYPE_CACHE), anyString(), any())(any(), any(), any())
      ).thenReturn(
        fileTypeRes match {
          case true => Future.successful(null)
          case _ => Future.failed(new Exception)
        }
      )
    }

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
      val controllerUnderTest = buildFakeCheckingServiceController()
      val result = controllerUnderTest.checkFileTypeSelected().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "gives a call to showCheckFileTypeSelected if user is authenticated" in {
      val controllerUnderTest = buildFakeCheckingServiceController()
      val result = controllerUnderTest.checkFileTypeSelected().apply(Fixtures.buildFakeRequestWithSessionId("GET"))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a bad request status and stay on the same page if form errors" in {
      val controllerUnderTest = buildFakeCheckingServiceController()
      val fileTypeData = Map("" -> "")
      val form = RsFormMappings.checkFileTypeForm.bind(fileTypeData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showCheckFileTypeSelected(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.OK
    }

    "if no form errors with file type = csv and save success" in {
      val controllerUnderTest = buildFakeCheckingServiceController(fileTypeRes = true)
      val checkFileTypeData = Map("checkFileType" -> "csv")
      val form = RsFormMappings.schemeTypeForm.bind(checkFileTypeData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showCheckFileTypeSelected(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.CheckCsvFilesController.checkCsvFilesPage().toString()
    }

    "if no form errors with file type = ods and save success" in {
      val controllerUnderTest = buildFakeCheckingServiceController(fileTypeRes = true)
      val checkFileTypeData = Map("checkFileType" -> "ods")
      val form = RsFormMappings.schemeTypeForm.bind(checkFileTypeData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = controllerUnderTest.showCheckFileTypeSelected(Fixtures.buildFakeUser, request, hc)
      status(result) shouldBe Status.SEE_OTHER
      result.header.headers.get("Location").get shouldBe routes.FileUploadController.uploadFilePage.toString()
    }

    "if no form errors with scheme type and save fails" in {
      val controllerUnderTest = buildFakeCheckingServiceController(fileTypeRes = false)
      val schemeTypeData = Map("checkFileType" -> "csv")
      val form = RsFormMappings.schemeTypeForm.bind(schemeTypeData)
      val request = Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = await(controllerUnderTest.showCheckFileTypeSelected(Fixtures.buildFakeUser, request, hc))
      contentAsString(result) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
      contentAsString(result) should include(messages("ers.global_errors.message"))
    }

  }

}

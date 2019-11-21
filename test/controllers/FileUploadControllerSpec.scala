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

import akka.stream.Materializer
import connectors.ErsConnector
import models._
import models.upscan.Failed
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{SessionService, UpscanService}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import utils.{CacheUtil, ERSFakeApplicationConfig, UpscanData}

import scala.concurrent.Future

class FileUploadControllerSpec extends PlaySpec with OneAppPerSuite
  with MockitoSugar with ERSUsers with ErsConstants with LegacyI18nSupport
  with ERSFakeApplicationConfig with UpscanData {

  "uploadFilePage" must {
    when(mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any()))
      .thenReturn(Future.successful(ersRequestObject))
    "return OK" when {
      "Upscan form data is successfully returned and callback record is created in session cache" in {
        when(mockUpscanService.getUpscanFormDataOds()(any[HeaderCarrier], any[Request[_]]))
          .thenReturn(Future.successful(upscanInitiateResponse))
        when(mockSessionService.createCallbackRecord(any[Request[_]], any[HeaderCarrier]))
          .thenReturn(Future.successful(()))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.uploadFilePage()(request)
          status(result) mustBe OK
        }
      }
    }

    "return global error page" when {
      "Upscan service returns an exception retrieving form data" in {
        reset(mockSessionService)
        when(mockUpscanService.getUpscanFormDataOds()(any[HeaderCarrier], any[Request[_]]))
          .thenReturn(Future.failed(new Exception("Expected exception")))
        withAuthorisedUser { request =>
          val result = TestFileUploadController.uploadFilePage()(request)
          checkGlobalErrorPage(result)
        }

        verify(mockSessionService, never()).createCallbackRecord(any(), any())
      }


      "Session service returns an exception creating callback record" in {
        when(mockUpscanService.getUpscanFormDataOds()(any[HeaderCarrier], any[Request[_]]))
          .thenReturn(Future.successful(upscanInitiateResponse))
        when(mockSessionService.createCallbackRecord(any[Request[_]], any[HeaderCarrier]))
            .thenReturn(Future.failed(new Exception("Expected exception")))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.uploadFilePage()(request)
          checkGlobalErrorPage(result)
        }
      }
    }
  }
//TODO rest of testing of success after impl
  "success" must {
    "return OK" when {
      "Callback record is returned with a successful upload and file name is cached" in {
        when(mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any()))
          .thenReturn(Future.successful(ersRequestObject))
        when(mockSessionService.getCallbackRecord(any[Request[_]], any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(uploadedSuccessfully)))
        when(mockCacheUtil.cache(meq(CacheUtil.FILE_NAME_CACHE), meq(uploadedSuccessfully.name), any[String])(any[HeaderCarrier], any(), any[Request[AnyRef]]))
          .thenReturn(Future.successful(mock[CacheMap]))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.success()(request)
          status(result) mustBe OK
          contentAsString(result) must include (messages("ers.if_there_are_no_errors.page_title"))
        }

      }
    }

    "return global error page" when {
      "caching file name fails" in {
        when(mockSessionService.getCallbackRecord(any[Request[_]], any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(uploadedSuccessfully)))
        when(mockCacheUtil.cache(meq(CacheUtil.FILE_NAME_CACHE), meq(uploadedSuccessfully.name), any[String])(any[HeaderCarrier], any(), any[Request[AnyRef]]))
          .thenReturn(Future.failed(new Exception))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.success()(request)
          checkGlobalErrorPage(result)
        }
      }
    }
  }

  "validationResults" must {
    when(mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any()))
      .thenReturn(Future.successful(ersRequestObject))
    when(mockSessionService.getCallbackRecord(any[Request[_]], any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(uploadedSuccessfully)))
    when(mockErsConnector.removePresubmissionData(any())(any[AuthContext], any[HeaderCarrier]))
      .thenReturn(Future.successful(HttpResponse(OK)))

    "redirect the user" when {
      "Ers Meta Data is returned, callback record is uploaded successfully, remove presubmission data returns OK and validate file data returns OK" in {
        when(mockCacheUtil.fetch[ErsMetaData](any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(validErsMetaData))
        when(mockErsConnector.validateFileData(meq(uploadedSuccessfully), any[SchemeInfo])(any[AuthContext], any[Request[AnyRef]], any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(OK)))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.SchemeOrganiserController.schemeOrganiserPage().url)
        }
      }

      "Ers Meta Data is returned, callback record is uploaded successfully, remove presubmission data returns OK and validate file data returns Accepted" in {
        when(mockCacheUtil.fetch[ErsMetaData](any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(validErsMetaData))
        when(mockErsConnector.validateFileData(meq(uploadedSuccessfully), any[SchemeInfo])(any[AuthContext], any[Request[AnyRef]], any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(ACCEPTED)))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.FileUploadController.validationFailure().url)
        }
      }
    }
    "return global error page" when {
      "validate file returns status code other than 200 OR 202" in {
        when(mockErsConnector.validateFileData(meq(uploadedSuccessfully), any[SchemeInfo])(any[AuthContext], any[Request[AnyRef]], any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          checkGlobalErrorPage(result)
        }
      }

      "remove presubmission data returns status code other than 200" in {
        when(mockErsConnector.removePresubmissionData(meq(validErsMetaData.schemeInfo))(any[AuthContext], any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(SERVICE_UNAVAILABLE)))
        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          checkGlobalErrorPage(result)
        }
      }

      "session service throws an exception retrieving callback data" in {
        when(mockSessionService.getCallbackRecord(any[Request[_]], any[HeaderCarrier]))
          .thenReturn(Future.failed(new Exception))
        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          checkGlobalErrorPage(result)
        }
      }

      "session service returns a file which has not been successfully uploaded" in {
        when(mockSessionService.getCallbackRecord(any[Request[_]], any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(Failed)))
        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          checkGlobalErrorPage(result)
        }
      }

      "cacheUtil fails to fetch metadata and returns an exception" in {
        when(mockCacheUtil.fetch[ErsMetaData](meq(CacheUtil.ersMetaData), meq(ersRequestObject.getSchemeReference))(any(), any(), any()))
          .thenReturn(Future.failed(new Exception("Expected exception")))
        withAuthorisedUser { request =>
          val result = TestFileUploadController.validationResults()(request)
          checkGlobalErrorPage(result)
        }
      }
    }
  }

  "failure" must {
    "be authorised" in {
      failure() { result =>
        status(result) must equal(SEE_OTHER)
        redirectLocation(result).get must include("/gg/sign-in")
      }
    }

    "authorised users" must {
      "throw an Exception" in {
        withAuthorisedUser { user =>
          failure(user) { result =>
            status(result) must equal(OK)
            contentAsString(result) must include(messages("ers.global_errors.message"))
          }
        }
      }
    }
  }

  "Validation failure" must {
    "be authorised" in {
      validationFailure() { result =>
        status(result) must equal(SEE_OTHER)
        redirectLocation(result).get must include("/gg/sign-in")
      }
    }

    "authorised users" must {
      "respond with a status of OK" in {
        when(
          mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
        ).thenReturn(
          Future.successful(ersRequestObject)
        )
        when(mockCacheUtil.fetch[CheckFileType](Matchers.refEq(CacheUtil.FILE_TYPE_CACHE), Matchers.any[String]())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(CheckFileType(Some("csv"))))

        withAuthorisedUser { user =>
          validationFailure(user) { result =>
            status(result) must be(OK)
            contentAsString(result) must include(messages("file_upload_errors.title"))
          }
        }
      }
    }
  }

  def failure(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
    handler(TestFileUploadController.failure().apply(request))
  }

  def validationFailure(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
    handler(TestFileUploadController.validationFailure().apply(request))
  }
  val testOptString = Some("test")
  val ersRequestObject = RequestObject(testOptString, testOptString, testOptString, Some("CSOP"), Some("CSOP"), testOptString, testOptString, testOptString, testOptString)
  val schemeInfo: SchemeInfo = SchemeInfo(testOptString.get, DateTime.now, testOptString.get, testOptString.get, testOptString.get, testOptString.get)
  val validErsMetaData: ErsMetaData = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockSessionService = mock[SessionService]
  lazy val mockCacheUtil = mock[CacheUtil]
  lazy val mockErsConnector = mock[ErsConnector]
  lazy val mockUpscanService = mock[UpscanService]

  when(mockCacheUtil.fetch[CheckFileType](Matchers.refEq(CacheUtil.FILE_TYPE_CACHE), Matchers.any[String]())(Matchers.any(),
    Matchers.any(), Matchers.any())).thenReturn(Future.successful(CheckFileType(Some("csv"))))

  object TestFileUploadController extends FileUploadController {
    val authConnector = mockAuthConnector
    val sessionService = mockSessionService
    val cacheUtil = mockCacheUtil
    val ersConnector = mockErsConnector
    override val upscanService = mockUpscanService
  }

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val materializer: Materializer = app.materializer
  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))


  def checkGlobalErrorPage(result: Future[Result]) = {
    status(result) mustBe OK
    contentAsString(result) must include (messages("ers.global_errors.title"))
  }
}

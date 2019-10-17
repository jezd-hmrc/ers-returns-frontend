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
import connectors.{AttachmentsConnector, ErsConnector}
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import services.SessionService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.Fixtures.ersRequestObject

import scala.util.Success

class CsvFileUploadControllerSpec extends UnitSpec with OneAppPerSuite with ERSFakeApplicationConfig with ERSUsers with MockitoSugar with ScalaFutures {


  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer
  implicit lazy val messages: Messages = Messages(Lang("en"), app.injector.instanceOf[MessagesApi])

  val mockAuthConnector = mock[AuthConnector]

  "calling uploadFilePage" should {

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def showUploadFilePage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Ok
    }

    "redirect for unauthorised users to login page" in {
      val result = csvFileUploadController.uploadFilePage().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }

    "show the result of showUploadFilePage() for authorised users" in {
      withAuthorisedUser { user =>
        csvFileUploadController.uploadFilePage().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET")).map { result =>
          status(result) shouldBe OK
        }
      }
    }

  }

  "calling showUploadFilePage" should {

    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil

    }

    "direct to ers errors page if fetching csv callback list fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )
      when(
        mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )
      when(
        csvFileUploadController.attachmentsConnector.getCsvFileUploadPartial()(any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(OK, Some(JsString("partialResponse"))))
      )

      contentAsString(await(csvFileUploadController.showUploadFilePage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

    "direct to ers errors page if fetching request object fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CsvFilesCallbackList(List()))
      )
      when(
        mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )
      when(
        csvFileUploadController.attachmentsConnector.getCsvFileUploadPartial()(any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(OK, Some(JsString("partialResponse"))))
      )

      contentAsString(await(csvFileUploadController.showUploadFilePage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

    "direct to ers errors page if fetching attachments partial fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CsvFilesCallbackList(List()))
      )
      when(
        mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )
      when(
        csvFileUploadController.attachmentsConnector.getCsvFileUploadPartial()(any(), any())
      ).thenReturn(
        Future.failed(new Exception)
      )

      contentAsString(await(csvFileUploadController.showUploadFilePage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

    "return the page with the partial if fetching from cache is successful" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CsvFilesCallbackList(List()))
      )
      when(
        mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )
      when(
        csvFileUploadController.attachmentsConnector.getCsvFileUploadPartial()(any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(OK, Some(JsString("partialResponse"))))
      )

      val result = await(csvFileUploadController.showUploadFilePage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK

      contentAsString(result) should include(messages("ers.upload_first_csv_file.page_title"))
      contentAsString(result) should include("partialResponse")
    }

  }

  "calling success" should {

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def showSuccess()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "redirect for unauthorised users to login page" in {
      val result = csvFileUploadController.success().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }

    "show the result of showUploadFilePage() for authorised users" in {
      withAuthorisedUser { user =>
        csvFileUploadController.success().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET")).map { result =>
          status(result) shouldBe OK
        }
      }
    }

  }

  "calling showSuccess" should {

    val mockSessionService = mock[SessionService]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mockSessionService
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def proceedCallbackData(callbackData: Option[CallbackData])(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "return the result of proceedCallbackData" in {
      reset(mockSessionService)
      when(
        mockSessionService.retrieveCallbackData()(any(), any())
      ).thenReturn(
        Future.successful(Some(mock[CallbackData]))
      )

      val result = await(csvFileUploadController.showSuccess()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

  }

  "calling proceedCallbackData" should {
    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil

      override def updateCallbackData(callbackData: Option[CallbackData], csvFilesCallbackList: List[CsvFilesCallback])(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): List[CsvFilesCallback] = List()
      override def modifyCachedCallbackData(newCsvFilesCallbackList: List[CsvFilesCallback])(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "direct to ers errors page if fetching CsvFilesCallbackList fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      contentAsString(await(csvFileUploadController.proceedCallbackData(Some(mock[CallbackData]))(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

    "return the result of modifyCallbackData if fetching CsvFilesCallbackList is successful" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(mock[CsvFilesCallbackList])
      )

      val result = await(csvFileUploadController.proceedCallbackData(Some(mock[CallbackData]))(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

  }

  "calling modifyCachedCallbackData" should {
    val callbackData = CallbackData(collection = "collection", id = "someid", length = 1000L, name = Some("CSOP_OptionsExercised_V3.csv"), contentType = Some("content-type"), customMetadata = None, sessionId = Some("testId"), noOfRows = None)

    val mockCacheUtil: CacheUtil = mock[CacheUtil]
    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "direct to ers errors page if caching newCsvFilesCallbackList fails" in {

      reset(mockCacheUtil)
      when(
        mockCacheUtil.cache[CsvFilesCallbackList](anyString(), any[CsvFilesCallbackList](), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      contentAsBytes(await(csvFileUploadController.modifyCachedCallbackData(List())(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsBytes(csvFileUploadController.getGlobalErrorPage)

    }

    "redirects to upload page if caching is successful and there are still files to be uploaded" in {

      reset(mockCacheUtil)
      when(
        mockCacheUtil.cache[CsvFilesCallbackList](anyString(), any[CsvFilesCallbackList](), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CacheMap("", Map()))
      )

      val csvFilesCallbackList: List[CsvFilesCallback] = List(
        CsvFilesCallback("file0", None),
        CsvFilesCallback("file1", Some(callbackData))
      )

      val result = await(csvFileUploadController.modifyCachedCallbackData(csvFilesCallbackList)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe routes.CsvFileUploadController.uploadFilePage().toString()
    }

    "shows success page if caching is successful and there are no files to be uploaded" in {

      reset(mockCacheUtil)
      when(
        mockCacheUtil.cache[CsvFilesCallbackList](anyString(), any[CsvFilesCallbackList](), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CacheMap("", Map()))
      )

      val csvFilesCallbackList: List[CsvFilesCallback] = List(
        CsvFilesCallback("file0", Some(callbackData)),
        CsvFilesCallback("file1", Some(callbackData))
      )

      val result = await(csvFileUploadController.modifyCachedCallbackData(csvFilesCallbackList)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

  }

  "calling updateCallbackData" should {

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]
    }

    val csvFilesCallbackList: List[CsvFilesCallback] = List(
      CsvFilesCallback("file0", None),
      CsvFilesCallback("file1", None),
      CsvFilesCallback("file2", None)
    )

    val callbackData = CallbackData(collection = "collection", id = "someid", length = 1000L, name = Some("CSOP_OptionsExercised_V3.csv"), contentType = Some("content-type"), customMetadata = None, sessionId = Some("testId"), noOfRows = None)

    "should add callbackData to relevent element in CsvFilesCallbackList" in {
      val result = await(csvFileUploadController.updateCallbackData(Some(callbackData), csvFilesCallbackList)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      result shouldBe List(
        CsvFilesCallback("file0", None),
        CsvFilesCallback("file1", None),
        CsvFilesCallback("file2", Some(callbackData))
      )
    }

  }

  "calling failure" should {

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]
     }

    "redirect for unauthorised users to login page" in {
      val result = csvFileUploadController.failure().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }

  }

  "calling validationFailure" should {

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]
    }

    "redirect for unauthorised users to login page" in {
      val result = csvFileUploadController.validationFailure().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }

    "show the result of processValidationFailure() for authorised users" in {
      withAuthorisedUser { user =>
        csvFileUploadController.validationFailure().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET")).map { result =>
          status(result) shouldBe OK
        }
      }
    }

  }

  "calling processValidationFailure" should {

    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "return Ok if fetching CheckFileType from cache is successful" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CheckFileType(Some("csv")))
      )
      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )

      val result = await(csvFileUploadController.processValidationFailure()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK

    }

    "return the globalErrorPage if fetching CheckFileType from cache fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )
      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )

      val result = csvFileUploadController.processValidationFailure()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc).futureValue

      result.value shouldBe Some(Success(csvFileUploadController.getGlobalErrorPage))
    }

    "return the globalErrorPage if fetching requestObject from cache fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CheckFileType(Some("csv")))
      )
      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.failed(new Exception)
      )

      val result = csvFileUploadController.processValidationFailure()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc).futureValue

      result.value shouldBe Some(Success(csvFileUploadController.getGlobalErrorPage))
    }

  }

  "calling validationResults" should {

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def processValidationResults()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "redirect for unauthorised users to login page" in {
      val result = csvFileUploadController.validationResults().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }

    "show the result of processValidationFailure() for authorised users" in {
      withAuthorisedUser { user =>
        csvFileUploadController.validationResults().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET")).map { result =>
          status(result) shouldBe OK
        }
      }
    }

  }

  "calling processValidationResults" should {

    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil

      override def removePresubmissionData(schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "return result of removePresubmissionData if fetching from the cache is successful" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[ErsMetaData](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(ErsMetaData(SchemeInfo("", DateTime.now, "", "", "", ""), "", None, "", None, None))
      )
      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )

      val result = await(csvFileUploadController.processValidationResults()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK

    }

    "direct to ers errors page if fetching metaData from cache fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[ErsMetaData](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )

      status(await(csvFileUploadController.processValidationResults()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)))

    }

    "direct to ers errors page if fetching requestObject from cache fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[ErsMetaData](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(ErsMetaData(SchemeInfo("", DateTime.now, "", "", "", ""), "", None, "", None, None))
      )

      when(
        mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any())
      ).thenReturn(
        Future.failed(new Exception)
      )

      status(await(csvFileUploadController.processValidationResults()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)))

    }

  }

  "calling removePresubmissionData" should {

    val mockErsConnector: ErsConnector = mock[ErsConnector]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mockErsConnector
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def extractCsvCallbackData(schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Redirect(""))
    }

    "return the result of extractCsvCallbackData if deleting presubmission data is successful" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.removePresubmissionData(any[SchemeInfo]())(any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(OK))
      )

      val result = await(csvFileUploadController.removePresubmissionData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").equals("") shouldBe true
    }

    "return Ok and show error page if deleting presubmission data fails" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.removePresubmissionData(any[SchemeInfo]())(any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))
      )

      val result = await(csvFileUploadController.removePresubmissionData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

    "direct to ers errors page if deleting presubmission data throws exception" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.removePresubmissionData(any[SchemeInfo]())(any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      contentAsBytes(await(csvFileUploadController.removePresubmissionData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsBytes(csvFileUploadController.getGlobalErrorPage)

    }

  }

  "calling extractCsvCallbackData" should {

    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil

      override def validateCsv(csvCallbackValidatorData: List[CallbackData], schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "throws exception if fetching from cache fails" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      contentAsString(await(csvFileUploadController.extractCsvCallbackData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

    "return the result of validateCsv if fetching from cache is successful" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CsvFilesCallbackList(List(CsvFilesCallback("", Some(mock[CallbackData])))))
      )

      val result = await(csvFileUploadController.extractCsvCallbackData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

    "direct to ers errors if fetching from cache is successful but there is no callbackData" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[CsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CsvFilesCallbackList(List(CsvFilesCallback("", None))))
      )

      contentAsString(await(csvFileUploadController.extractCsvCallbackData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

  }

  "calling validateCsv" should {

    val mockErsConnector: ErsConnector = mock[ErsConnector]

    val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector

      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mockErsConnector
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      when(
        mockCacheUtil.cache[CsvFilesCallbackList](anyString(), any[CsvFilesCallbackList](), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(CacheMap("", Map()))
      )
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "redirect to schemeOrganiserPage if validating is successful" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[CallbackData]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(OK))
      )

      val result = await(csvFileUploadController.validateCsv(mock[List[CallbackData]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe routes.SchemeOrganiserController.schemeOrganiserPage.toString()
    }

    "redirect to validationFailure if validating fails" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[CallbackData]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(ACCEPTED))
      )

      val result = await(csvFileUploadController.validateCsv(mock[List[CallbackData]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe routes.CsvFileUploadController.validationFailure.toString()
    }

    "show error page if validating returns result other than OK and ACCEPTED" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[CallbackData]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))
      )

      val result = await(csvFileUploadController.validateCsv(mock[List[CallbackData]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

    "direct to ers errors page if connecting with validator is not successful" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[CallbackData]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      contentAsString(await(csvFileUploadController.validateCsv(mock[List[CallbackData]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }

  }

}

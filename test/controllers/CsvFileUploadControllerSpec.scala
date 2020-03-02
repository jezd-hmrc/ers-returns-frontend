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
import config.{ApplicationConfig, ApplicationConfigImpl}
import connectors.ErsConnector
import models._
import models.upscan._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.{Application, Configuration}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{SessionService, UpscanService}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import utils.Fixtures.ersRequestObject
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, UpscanData}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

class CsvFileUploadControllerSpec extends UnitSpec with OneAppPerSuite with ERSFakeApplicationConfig with ERSUsers with MockitoSugar with BeforeAndAfterEach with UpscanData with ScalaFutures {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockAuthConnector,
      mockSessionService,
      mockErsConnector,
      mockCacheUtil,
      mockUpscanService
    )
    when(
      mockCacheUtil.fetch[RequestObject](anyString())(any(), any(), any(), any())
    ).thenReturn(
      Future.successful(ersRequestObject)
    )
  }


  "uploadFilePage" should {
    "display file upload page" when {
      "form data is successfully retrieved from upscan" in {
        when(
          mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), anyString())(any(), any(), any())
        ).thenReturn(Future.successful(notStartedUpscanCsvFilesList))
        when(mockUpscanService.getUpscanFormDataCsv(UploadId(anyString()), any())(any(), any()))
          .thenReturn(UpscanInitiateResponse(Reference("Reference"), "postTarget", formFields = Map()))

        withAuthorisedUser { req =>
          val result = csvFileUploadController.uploadFilePage()(req)

          status(result) shouldBe OK

          contentAsString(result) should include(messages("csv_file_upload.upload_your_file", ""))
        }
      }

      "display global error page" when {
        "upscanService throws an exception" in {
          val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
            List(
              UpscanCsvFilesCallback(testUploadId, "file1", NotStarted),
              UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
            )
          )
          when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), any[String])(any(), any(), any()))
            .thenReturn(upscanCsvFilesCallbackList)
          when(mockUpscanService.getUpscanFormDataCsv(UploadId(anyString()), any())(any(), any()))
            .thenReturn(Future.failed(new Exception("Expected exception")))

          withAuthorisedUser { req =>
            val result = csvFileUploadController.uploadFilePage()(req)

            status(result) shouldBe OK
            contentAsString(result) should include(messages("ers.global_errors.title"))
          }
        }

        "fetching cache data throws an exception" in {
          when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), any[String])(any(), any(), any()))
            .thenReturn(Future.failed(new Exception("Expected exception")))

          withAuthorisedUser { req =>
            val result = csvFileUploadController.uploadFilePage()(req)

            status(result) shouldBe OK
            contentAsString(result) should include(messages("ers.global_errors.title"))
          }
        }

        "there is no files to upload" in {
          val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
            List(
              UpscanCsvFilesCallback(testUploadId, "file1", InProgress)
            )
          )
          when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), any[String])(any(), any(), any()))
            .thenReturn(upscanCsvFilesCallbackList)

          withAuthorisedUser { req =>
            val result = csvFileUploadController.uploadFilePage()(req)

            status(result) shouldBe OK
            contentAsString(result) should include(messages("ers.global_errors.title"))
          }
        }
      }
    }
  }

  "success" should {
    "update the cache for the relevant uploadId to InProgress" in {
      withAuthorisedUser { req =>
        val updatedCallbackCaptor: ArgumentCaptor[UpscanCsvFilesCallbackList] = ArgumentCaptor.forClass(classOf[UpscanCsvFilesCallbackList])

        when(mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), any[String])(any[HeaderCarrier], any(), any()))
          .thenReturn(Future.successful(notStartedUpscanCsvFilesList))
        when(mockCacheUtil.cache(meq(CacheUtil.CSV_FILES_UPLOAD), updatedCallbackCaptor.capture(), any[String])(any[HeaderCarrier], any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))

        await(csvFileUploadController.success(testUploadId)(req))
        updatedCallbackCaptor.getValue shouldBe inProgressUpscanCsvFilesList
      }
    }

    "redirect the user to validation results" when {
      "no file in the cache has UploadStatus of NotStarted after update" in {
        withAuthorisedUser { req =>
          when(mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.successful(notStartedUpscanCsvFilesList))
          when(mockCacheUtil.cache(meq(CacheUtil.CSV_FILES_UPLOAD), any[UpscanCsvFilesList], any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.successful(mock[CacheMap]))

          val result = csvFileUploadController.success(testUploadId)(req)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.CsvFileUploadController.validationResults().url)
        }
      }
    }

    "redirect the user to upload a file" when {
      "a file in the cache has an UploadStatus of NotStarted after update" in {
        withAuthorisedUser { req =>
          when(mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.successful(multipleNotStartedUpscanCsvFilesList))
          when(mockCacheUtil.cache(meq(CacheUtil.CSV_FILES_UPLOAD), any[UpscanCsvFilesList], any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.successful(mock[CacheMap]))
          val result = csvFileUploadController.success(testUploadId)(req)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.CsvFileUploadController.uploadFilePage().url)
        }
      }
    }

    "display global error page" when {
      "Fetching the cache fails" in {
        withAuthorisedUser { req: Request[AnyContent] =>
          when(mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.failed(new Exception("Expected Exception")))

          val result = csvFileUploadController.success(testUploadId)(req)
          status(result) shouldBe OK
          contentAsString(result) should include(messages("ers.global_errors.title"))
        }
      }

      "saving the cache fails" in {
        withAuthorisedUser { req =>
          when(mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.successful(multipleNotStartedUpscanCsvFilesList))
          when(mockCacheUtil.cache(meq(CacheUtil.CSV_FILES_UPLOAD), any[UpscanCsvFilesCallbackList], any[String])(any[HeaderCarrier], any(), any()))
            .thenReturn(Future.failed(new Exception("Expected Exception")))

          val result = csvFileUploadController.success(testUploadId)(req)
          status(result) shouldBe OK
          contentAsString(result) should include(messages("ers.global_errors.title"))
        }
      }
    }
  }

  "calling failure" should {

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig

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

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
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

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "return Ok if fetching CheckFileType from cache is successful" in {
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

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
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

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil

      override def removePresubmissionData(schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "return result of removePresubmissionData if fetching from the cache is successful" in {
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

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
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

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil

      override def validateCsv(csvCallbackData: List[UploadedSuccessfully], schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future.successful(Ok("Validated"))
    }

    "return global error page" when {
      "fetching data from cache util fails" in {
        reset(mockCacheUtil)
        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](anyString(), anyString())(any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException))

        contentAsString(await(csvFileUploadController.extractCsvCallbackData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
      }
    }

    "return the result of validateCsv if fetching from cache is successful" in {
      when(
        mockCacheUtil.fetch[UpscanCsvFilesList](meq(CacheUtil.CSV_FILES_UPLOAD), anyString())(any(), any(), any())
      ).thenReturn(Future.successful(inProgressUpscanCsvFilesList))
      when(
        mockCacheUtil.fetch[UploadStatus](meq(s"${CacheUtil.CHECK_CSV_FILES}-${testUploadId.value}"), anyString())(any(), any(), any())
      ).thenReturn(uploadedSuccessfully)
      when(
        mockCacheUtil.cache(any(), any(), any())(any(), any(), any())
      ).thenReturn(Future.successful(CacheMap("", Map())))
      val result = await(csvFileUploadController.extractCsvCallbackData(Fixtures.EMISchemeInfo)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
      contentAsString(result) shouldBe "Validated"
    }

    "direct to ers errors" when {
      "fetching from cache is successful but there is no callbackData" in {
        when(
          mockCacheUtil.fetch[UpscanCsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
        ).thenReturn(
          Future.successful(UpscanCsvFilesCallbackList(List())))

        contentAsString(await(csvFileUploadController.extractCsvCallbackData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
      }

      "one of the files are not complete" in {
        when(
          mockCacheUtil.fetch[UpscanCsvFilesCallbackList](anyString(), anyString())(any(), any(), any())
        ).thenReturn(
          Future.successful(UpscanCsvFilesCallbackList(List())))

        contentAsString(await(csvFileUploadController.extractCsvCallbackData(mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
      }
    }

  }

  "calling validateCsv" should {

    val mockErsConnector: ErsConnector = mock[ErsConnector]

    lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
      val authConnector = mockAuthConnector
      val appConfig: ApplicationConfig = ApplicationConfig
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mockErsConnector
      val mockCacheUtil: CacheUtil = mock[CacheUtil]
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "redirect to schemeOrganiserPage if validating is successful" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[UploadedSuccessfully]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(OK))
      )

      val result = await(csvFileUploadController.validateCsv(mock[List[UploadedSuccessfully]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe routes.SchemeOrganiserController.schemeOrganiserPage.toString()
    }

    "redirect to validationFailure if validating fails" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[UploadedSuccessfully]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(ACCEPTED))
      )

      val result = await(csvFileUploadController.validateCsv(mock[List[UploadedSuccessfully]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe routes.CsvFileUploadController.validationFailure.toString()
    }

    "show error page if validating returns result other than OK and ACCEPTED" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[UploadedSuccessfully]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))
      )

      val result = await(csvFileUploadController.validateCsv(mock[List[UploadedSuccessfully]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }

    "direct to ers errors page if connecting with validator is not successful" in {
      reset(mockErsConnector)
      when(
        mockErsConnector.validateCsvFileData(any[List[UploadedSuccessfully]](), any[SchemeInfo]())(any(), any(), any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )
      contentAsString(await(csvFileUploadController.validateCsv(mock[List[UploadedSuccessfully]], mock[SchemeInfo])(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))) shouldBe contentAsString(csvFileUploadController.getGlobalErrorPage)
    }
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer
  implicit lazy val messages: Messages = Messages(Lang("en"), app.injector.instanceOf[MessagesApi])
  implicit val requests: Request[_] = FakeRequest()

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockSessionService: SessionService = mock[SessionService]
  val mockErsConnector: ErsConnector = mock[ErsConnector]
  val mockCacheUtil: CacheUtil = mock[CacheUtil]
  val mockUpscanService: UpscanService = mock[UpscanService]

  lazy val csvFileUploadController: CsvFileUploadController = new CsvFileUploadController {
    val authConnector: AuthConnector = mockAuthConnector
    override val sessionService: SessionService = mockSessionService
    override val ersConnector: ErsConnector = mockErsConnector
    override val cacheUtil: CacheUtil = mockCacheUtil
    override val upscanService: UpscanService = mockUpscanService
    override val appConfig: ApplicationConfig = ApplicationConfig
  }

}

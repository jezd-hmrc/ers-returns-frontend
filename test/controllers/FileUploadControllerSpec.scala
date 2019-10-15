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
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, LegacyI18nSupport, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SessionService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.Fixtures.fakeRequestToRequestWithSchemeRef

class FileUploadControllerSpec extends PlaySpec with OneAppPerSuite
  with MockitoSugar with ERSUsers with ErsConstants with LegacyI18nSupport
  with ERSFakeApplicationConfig {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val materializer: Materializer = app.materializer
  def injector: Injector = app.injector
  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))

  "FileUploadController" must {

    "return with a successful HTTP response on validationResults call" in {
      val mockCallbackData = mock[CallbackData]
      when(mockSessionService.retrieveCallbackData()(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(mockCallbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any[String](), Matchers.any[String]())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(Matchers.any[CallbackData](), Matchers.any())(Matchers.any[AuthContext](), Matchers.any(), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(200)))
      when(mockErsConnector.removePresubmissionData(Matchers.any[SchemeInfo]())(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))

      withAuthorisedUser { user =>
        validationResults(Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
          status(result) must equal(SEE_OTHER)
        }
      }
    }

    "redirect to Scheme Organizer page if validation is successful" in {

      when(mockSessionService.retrieveCallbackData()(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(Matchers.any[CallbackData](), Matchers.any())(Matchers.any[AuthContext](), Matchers.any(), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))
      when(mockErsConnector.removePresubmissionData(Matchers.any[SchemeInfo]())(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))

      withAuthorisedUser { user =>
        validationResults(user) { result =>
          status(result) must equal(SEE_OTHER)
          redirectLocation(result).get must include("/company-details")
        }
      }
    }

    "redirect to Validation Failure page if validation fails with status 202" in {

      when(mockSessionService.retrieveCallbackData()(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(Matchers.any[CallbackData](), Matchers.any())(Matchers.any[AuthContext](), Matchers.any(), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(ACCEPTED)))
      when(mockErsConnector.removePresubmissionData(Matchers.any[SchemeInfo]())(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))
      withAuthorisedUser { user =>
        validationResults(user) { result =>
          status(result) must equal(SEE_OTHER)
          redirectLocation(result).get must include("/errors-have-been-found-ods")
        }
      }
    }

    "return with a error page if validation fails" in {
      when(mockSessionService.retrieveCallbackData()(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(Matchers.any[CallbackData](), Matchers.any())(Matchers.any[AuthContext](), Matchers.any(), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(500)))
      when(mockErsConnector.removePresubmissionData(Matchers.any[SchemeInfo]())(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))
      withAuthorisedUser { user =>
        validationResults(user) { result =>
          status(result) must equal(OK)
        }
      }
    }

    "return with a error page if deletiong old presubmission data fails" in {
      val mockCallbackData = mock[CallbackData]
      when(mockSessionService.retrieveCallbackData()(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(Matchers.any[CallbackData](), Matchers.any())(Matchers.any[AuthContext](), Matchers.any(), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(200)))
      when(mockErsConnector.removePresubmissionData(Matchers.any[SchemeInfo]())(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))
      withAuthorisedUser { user =>
        validationResults(user) { result =>
          status(result) must equal(OK)
        }
      }
    }

    "return with a error page if callback data is not found" in {
      when(mockSessionService.retrieveCallbackData()(Matchers.any(), Matchers.any())).thenReturn(Future.successful(None))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(Matchers.any[CallbackData](), Matchers.any())(Matchers.any[AuthContext](), Matchers.any(), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(200)))
      when(mockErsConnector.removePresubmissionData(Matchers.any[SchemeInfo]())(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))
      withAuthorisedUser { user =>
        validationResults(user) { result =>
          status(result) must equal(OK)
        }
      }
    }

    "respond to /submit-your-ers-annual-return/file-upload/callback/" in {
      val result = route(app, FakeRequest(POST, s"/submit-your-ers-annual-return/file-upload/callback"))
      status(result.get) must not equal NOT_FOUND
    }
  }

  "get" must {
    "be authorised" in {
      getFileUploadPartial() { result =>
        status(result) must equal(SEE_OTHER)
        redirectLocation(result).get must include("/gg/sign-in")
      }
    }

    "authorised users" must {
      "respond with a status of OK" in {
        withAuthorisedUser { user =>
          getFileUploadPartial(user) { result =>
            status(result) must be(OK)
          }
        }
      }

      "display the attachments partial" in {
        withAuthorisedUser { user =>
          getFileUploadPartial(user) { result =>
            contentAsString(result) must include("id=\"file-uploader\"")
          }
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

  "calling success" should {
    val fileUploadController: FileUploadController = new FileUploadController {
      val authConnector = mockAuthConnector
      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def showSuccess()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "redirect for unauthorised users to login page" in {
      val result = fileUploadController.success().apply(FakeRequest("GET", ""))
      status(result) must be(SEE_OTHER)
      redirectLocation(result).get must include("/gg/sign-in")
    }

    "show the result of showUploadFilePage() for authorised users" in {
      withAuthorisedUser { user =>
        fileUploadController.success().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET")).map { result =>
          status(Future(result)) must be(OK)
        }
      }
    }
  }

  "calling showSuccess" should {

    val mockSessionService = mock[SessionService]
    val mockCacheUtil: CacheUtil = mock[CacheUtil]
    val fileUploadController: FileUploadController = new FileUploadController {
      val authConnector = mockAuthConnector
      override val attachmentsConnector = mock[AttachmentsConnector]
      override val sessionService = mockSessionService
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mockCacheUtil
    }

    "return the result of retrieveCallbackData" in {
      reset(mockSessionService)
      when(mockSessionService.retrieveCallbackData()(any(), any()))
        .thenReturn(Future.successful(Some(CallbackData("", "", 0, Some(""), None, None, None, None))))

      reset(mockCacheUtil)
      when(mockCacheUtil.cache[String](anyString(), anyString(), anyString())(any(), any(), any()))
        .thenReturn(Future.successful(mock[CacheMap]))

      val result = fileUploadController.showSuccess()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      contentAsString(result).contains(messages("ers.bulk.success.csop.info", "this file")) must be(true)
    }

    "direct to ers errors page if fetching fileName fails" in {

      reset(mockSessionService)
      when(mockSessionService.retrieveCallbackData()(any(), any()))
        .thenReturn(Future.successful(Some(CallbackData("", "", 5, Some("file_name"), None, None, None, None))))

      reset(mockCacheUtil)
      when(mockCacheUtil.cache[String](anyString(), anyString(), anyString())(any(), any(), any()))
        .thenReturn(Future.failed(new Exception))

      val result = fileUploadController.showSuccess()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      contentAsString(result) must be(contentAsString(Future(fileUploadController.getGlobalErrorPage)))
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

        when(mockCacheUtil.fetch[ErsMetaData](Matchers.refEq(CacheUtil.ersMetaData), Matchers.any[String]())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(validErsMetaData))
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

  def validationResults(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
    handler(TestFileUploadController.validationResults().apply(request))
  }

  def success(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
    handler(TestFileUploadController.success().apply(request))
  }

  def failure(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
    handler(TestFileUploadController.failure().apply(request))
  }

  def validationFailure(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
    handler(TestFileUploadController.validationFailure().apply(request))
  }

  def getFileUploadPartial(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any) {
    val html =
      """
    <form id="file-uploader" method="post" action="/attachments/attach/ers" enctype="multipart/form-data">
      <input name="fileToUpload" id="fileToUpload" type="file" accept=".csv"  />
      <input name="metadata" id="metadata" value="zTCFC5oK2j+ooVABIkaEoRzjcTt3FyyoCExq6tsYdYbGNjjq8zxM2n0si07PdWXiUGhG+4SZBK7CyNE4aLw8D+1pHDE4xzwDWxc70rELSKsgjPi9" type="hidden"/>

      <input name="onSuccessCallbackUrl" id="onSuccessCallbackUrl" value="qDjKUEySXZaT4hDttcSPiCRU1PH0CWu9tqe3sWPjlE8SQoyeJ/Wg0Sj+A88ALs3Yww+/ZIB3c3ZCGEjF3AGXeFHXDUqoCLKpBrArlOM8XjuZ7vAp42BfRpZGexsg334G" type="hidden"/>

      <input name="onSuccessRedirectUrl" id="onSuccessRedirectUrl" value="KB3jnZY9ia8OUhw+ThqM8pmLoX+/Dh5rtEl1ftdBZEUL34um86CVQFf4HSs/bmyC/qBW5rM52zNhKKbBIRLpMnOszo3ryexIumgPibw+LSjnrQ/zAOWFc7te94Ncyeg=" type="hidden"/>
      <input name="onFailureRedirectUrl" id="onFailureRedirectUrl" value="TZPWygBwtCWyJQRBF0UzfQqa5VKAKBNEBYKX+elCT5P0YZFkiEX0ESnOC/fDK2YgMoPHhhUVpvy7y75lhluFNycZDNjRqmoAOoZucl/zCwf8Jqzm4pFfvjblLpzGIAM=" type="hidden"/>
      <button type="submit">Upload</button>
    </form>""""""

    when(mockAttachmentsConnector.getFileUploadPartial()(Matchers.any(), Matchers.any()))
      .thenReturn(Future.successful(HttpResponse(OK, responseString = Some(html))))
    handler(TestFileUploadController.uploadFilePage().apply(request))
  }


  def errorCountIs(result: Future[Result], count: Int): Unit = {
    val errors = contentAsString(result).split("class=\"error\"")
    errors.size must be(count + 1)
  }

  lazy val metaData: JsObject = Json.obj(
    "surname" -> Fixtures.surname,
    "firstForename" -> Fixtures.firstName
  )

  lazy val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")
  lazy val validErsMetaData: ErsMetaData = new ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
  lazy val callbackData = CallbackData(
    collection = "collection",
    id = "someid",
    length = 1000L,
    name = Some(Fixtures.firstName),
    contentType = Some("content-type"),
    customMetadata = Some(metaData),
    sessionId = Some("testId"),
    noOfRows = None)

  lazy val schemeInfoInvalidTimeStamp = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")
  lazy val invalidErsMetaData: ErsMetaData = new ErsMetaData(schemeInfoInvalidTimeStamp, "ipRef", Some("aoRef"),
    "empRef", Some("agentRef"), Some("sapNumber"))

  lazy val mockAttachmentsConnector = mock[AttachmentsConnector]
  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockSessionService = mock[SessionService]
  lazy val mockCacheUtil = mock[CacheUtil]
  lazy val mockErsConnector = mock[ErsConnector]

  /** Csv Callback List creation */
  lazy val jv: JsValue = Json.parse("""{}""")
  lazy val s: Map[String, JsValue] = Map("" -> jv)
  lazy val js = new JsObject(s)
  lazy val cb = new CallbackData("File", "File", 100.toLong, Some("File"), Some("File"), Some("File"), Some(js), noOfRows = None)
  lazy val csvFileData = new CsvFilesCallback("file0", Some(cb))
  lazy val csvCallBackList = new CsvFilesCallbackList(List(csvFileData, csvFileData, csvFileData))

  when(mockCacheUtil.fetch[ErsMetaData](Matchers.refEq(CacheUtil.ersMetaData), Matchers.any[String]())(Matchers.any(),
    Matchers.any(), Matchers.any())).thenReturn(Future.successful(validErsMetaData))
  when(mockCacheUtil.fetch[CheckFileType](Matchers.refEq(CacheUtil.FILE_TYPE_CACHE), Matchers.any[String]())(Matchers.any(),
    Matchers.any(), Matchers.any())).thenReturn(Future.successful(CheckFileType(Some("csv"))))
  when(mockCacheUtil.fetchOption[CsvFilesCallbackList](Matchers.any[String](), Matchers.any[String]())(Matchers.any(),
    Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(csvCallBackList)))
  when(mockCacheUtil.getSchemeRefFromScreenSchemeInfo(Some(anyString()))).thenReturn("")

  object TestFileUploadController extends FileUploadController {
    val attachmentsConnector = mockAttachmentsConnector
    val authConnector = mockAuthConnector
    val sessionService = mockSessionService
    val cacheUtil = mockCacheUtil
    val ersConnector = mockErsConnector
  }

  val sr = "AA0000000000000"
}

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
import connectors.{AttachmentsConnector, ErsConnector}
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SessionService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import utils.{AuthHelper, CacheUtil, ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FileUploadControllerSpec extends PlaySpec with OneAppPerSuite
  with MockitoSugar with ErsConstants with LegacyI18nSupport
  with ERSFakeApplicationConfig with AuthHelper {

  lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))
  implicit val request: Request[_] = FakeRequest()

  "FileUploadController" must {

    "return with a successful HTTP response on validationResults call" in {
      val mockCallbackData = mock[CallbackData]

      when(
        mockSessionService.retrieveCallbackData()(ArgumentMatchers.any(), ArgumentMatchers.any())
      ) thenReturn Future.successful(Some(mockCallbackData))
      when(
        mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.any[String](), ArgumentMatchers.any[String]())
					(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ) thenReturn Future.successful(validErsMetaData)

      when(
        mockErsConnector.validateFileData(ArgumentMatchers.any[CallbackData](), ArgumentMatchers.any())
				(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier]())
      ) thenReturn Future.successful(HttpResponse(OK))

      when(
        mockErsConnector.removePresubmissionData(ArgumentMatchers.any[SchemeInfo]())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any[HeaderCarrier]())
      ) thenReturn Future.successful(HttpResponse(OK))

			when(
				mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
			) thenReturn Future.successful(ersRequestObject)

			setAuthMocks()
			validationResults(Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
				status(result) must equal(SEE_OTHER)
			}
    }

    "redirect to Scheme Organizer page if validation is successful" in {

      when(
        mockSessionService.retrieveCallbackData()(ArgumentMatchers.any(), ArgumentMatchers.any())
      ) thenReturn Future.successful(Some(callbackData))

      when(
        mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ) thenReturn Future.successful(validErsMetaData)

      when(
        mockErsConnector.validateFileData(ArgumentMatchers.any[CallbackData](), ArgumentMatchers.any())
				(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier]())
      ) thenReturn Future.successful(HttpResponse(OK))

      when(
        mockErsConnector.removePresubmissionData(ArgumentMatchers.any[SchemeInfo]())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any[HeaderCarrier]())
      ) thenReturn Future.successful(HttpResponse(OK))

      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ) thenReturn Future.successful(ersRequestObject)

			setAuthMocks()
			validationResults() { result =>
				status(result) must equal(SEE_OTHER)
				redirectLocation(result).get must include("/company-details")
			}
    }

    "redirect to Validation Failure page if validation fails with status 202" in {

      when(
        mockSessionService.retrieveCallbackData()(ArgumentMatchers.any(), ArgumentMatchers.any())
      ) thenReturn Future.successful(Some(callbackData))

      when(
        mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ) thenReturn Future.successful(validErsMetaData)

      when(
        mockErsConnector.validateFileData(ArgumentMatchers.any[CallbackData](), ArgumentMatchers.any())
				(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier]())
      ) thenReturn Future.successful(HttpResponse(ACCEPTED))

      when(
        mockErsConnector.removePresubmissionData(ArgumentMatchers.any[SchemeInfo]())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any[HeaderCarrier]())
      ) thenReturn Future.successful(HttpResponse(OK))

      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ) thenReturn Future.successful(ersRequestObject)

			setAuthMocks()
        validationResults() { result =>
          status(result) must equal(SEE_OTHER)
          redirectLocation(result).get must include("/errors-have-been-found-ods")
        }
    }

    "return with a error page if validation fails" in {
      when(mockSessionService.retrieveCallbackData()(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(ArgumentMatchers.any[CallbackData](), ArgumentMatchers.any())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(500)))
      when(mockErsConnector.removePresubmissionData(ArgumentMatchers.any[SchemeInfo]())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))
			setAuthMocks()
        validationResults() { result =>
          status(result) must equal(OK)
        }
    }

    "return with a error page if deleting old presubmission data fails" in {
      when(mockSessionService.retrieveCallbackData()(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(ArgumentMatchers.any[CallbackData](), ArgumentMatchers.any())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(OK)))
      when(mockErsConnector.removePresubmissionData(ArgumentMatchers.any[SchemeInfo]())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))
			setAuthMocks()
        validationResults() { result =>
          status(result) must equal(OK)
        }
    }

    "return with a error page if callback data is not found" in {
      when(mockSessionService.retrieveCallbackData()(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))
      when(mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(validErsMetaData))
      when(mockErsConnector.validateFileData(ArgumentMatchers.any[CallbackData](), ArgumentMatchers.any())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(HttpResponse(OK)))
      when(mockErsConnector.removePresubmissionData(ArgumentMatchers.any[SchemeInfo]())(ArgumentMatchers.any[ERSAuthData](), ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(HttpResponse(OK)))
			setAuthMocks()
        validationResults() { result =>
          status(result) must equal(OK)
        }
    }

    "respond to /submit-your-ers-annual-return/file-upload/callback/" in {
      val result = route(app, FakeRequest(POST, s"/submit-your-ers-annual-return/file-upload/callback"))
      status(result.get) must not equal NOT_FOUND
    }
  }

  "get" must {
    "be authorised" in {
			setUnauthorisedMocks()
      getFileUploadPartial() { result =>
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

				setAuthMocks()
          getFileUploadPartial() { result =>
            status(result) must be(OK)
          }
        }

      "display the attachments partial" in {

        when(
          mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
        ).thenReturn(
          Future.successful(ersRequestObject)
        )

				setAuthMocks()
          getFileUploadPartial() { result =>
            contentAsString(result) must include("id=\"file-uploader\"")
          }
      }
    }
  }

  "failure" must {
    "be authorised" in {
			setUnauthorisedMocks()
      failure() { result =>
        status(result) must equal(SEE_OTHER)
        redirectLocation(result).get must include("/gg/sign-in")
      }
    }

    "authorised users" must {
      "throw an Exception" in {
				setAuthMocks()
          failure() { result =>
            status(result) must equal(OK)
            contentAsString(result) must include(messages("ers.global_errors.message"))
          }
      }
    }
  }

  "calling success" should {
    val fileUploadController: FileUploadController = new FileUploadController {
			val authConnector: PlayAuthConnector = mockAuthConnector
      override val attachmentsConnector: AttachmentsConnector = mock[AttachmentsConnector]
      override val sessionService: SessionService = mock[SessionService]
      override val ersConnector: ErsConnector = mock[ErsConnector]
      override val cacheUtil: CacheUtil = mock[CacheUtil]

      override def showSuccess()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future(Ok)
    }

    "redirect for unauthorised users to login page" in {
			setUnauthorisedMocks()
      val result = fileUploadController.success().apply(FakeRequest("GET", ""))
      status(result) must be(SEE_OTHER)
      redirectLocation(result).get must include("/gg/sign-in")
    }

    "show the result of showUploadFilePage() for authorised users" in {
			setAuthMocks()
        fileUploadController.success().apply(Fixtures.buildFakeRequestWithSessionIdCSOP("GET")).map { result =>
          status(Future(result)) must be(OK)
        }
    }
  }

  "calling showSuccess" should {

    val mockSessionService = mock[SessionService]
    val mockCacheUtil: CacheUtil = mock[CacheUtil]
    val fileUploadController: FileUploadController = new FileUploadController {
    val authConnector: PlayAuthConnector = mockAuthConnector
    override val attachmentsConnector: AttachmentsConnector = mock[AttachmentsConnector]
    override val sessionService: SessionService = mockSessionService
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

      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ) thenReturn Future.successful(ersRequestObject)


      val result = fileUploadController.showSuccess()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      contentAsString(result) must include(messages("ers.bulk.success.csop.info", "this file"))
    }

    "direct to ers errors page if fetching requestObject fails" in {

      reset(mockSessionService)
      when(mockSessionService.retrieveCallbackData()(any(), any()))
        .thenReturn(Future.successful(Some(CallbackData("", "", 5, Some("file_name"), None, None, None, None))))

      reset(mockCacheUtil)
      when(mockCacheUtil.cache[String](anyString(), anyString(), anyString())(any(), any(), any()))
        .thenReturn(Future.successful(mock[CacheMap]))

      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.failed(new Exception)
      )


      val result = fileUploadController.showSuccess()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      contentAsString(result) must be(contentAsString(Future(fileUploadController.getGlobalErrorPage)))
    }

    "direct to ers errors page if fetching fileName fails" in {

      reset(mockSessionService)
      when(mockSessionService.retrieveCallbackData()(any(), any()))
        .thenReturn(Future.successful(Some(CallbackData("", "", 5, Some("file_name"), None, None, None, None))))

      reset(mockCacheUtil)
      when(mockCacheUtil.cache[String](anyString(), anyString(), anyString())(any(), any(), any()))
        .thenReturn(Future.failed(new Exception))

      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(
        Future.successful(ersRequestObject)
      )

      val result = fileUploadController.showSuccess()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc)
      contentAsString(result) must be(contentAsString(Future(fileUploadController.getGlobalErrorPage)))
    }
  }


  "Validation failure" must {

    "be authorised" in {
			setUnauthorisedMocks()
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

        when(mockCacheUtil.fetch[CheckFileType](ArgumentMatchers.refEq(CacheUtil.FILE_TYPE_CACHE), ArgumentMatchers.any[String]())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(CheckFileType(Some("csv"))))
				setAuthMocks()
          validationFailure() { result =>
            status(result) must be(OK)
            contentAsString(result) must include(messages("file_upload_errors.title"))
          }
      }
    }
  }

  "validationResults" must {

    "redirect user" when {

      "all calls are successful" in {

        when(
          mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.eq(mockCacheUtil.ersMetaData), any())(any(), any(), any())
        ) thenReturn Future.successful(validErsMetaData)

        when(
          mockSessionService.retrieveCallbackData()(any(), any())
        ) thenReturn Future.successful(Some(callbackData))

        when(
          mockErsConnector.removePresubmissionData(any())(any(), any())
        ) thenReturn Future.successful(HttpResponse(OK))

        when(
          mockErsConnector.validateFileData(any(), any())(any(), any(), any())
        ) thenReturn Future.successful(HttpResponse(OK))

        when(
          mockCacheUtil.cache(ArgumentMatchers.eq(mockCacheUtil.VALIDATED_SHEEETS), any(), anyString())(any(), any(), any())
        ) thenReturn Future.successful(mock[CacheMap])

				setAuthMocks()
          validationResults() { result =>
            status(result) mustBe SEE_OTHER
            redirectLocation(result).get must include("/company-details")

          }
      }

      "ers connector returns Accepted to show a validation failure" in {

        when(
          mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.eq(mockCacheUtil.ersMetaData), any())(any(), any(), any())
        ) thenReturn Future.successful(validErsMetaData)

        when(
          mockSessionService.retrieveCallbackData()(any(), any())
        ) thenReturn Future.successful(Some(callbackData))

        when(
          mockErsConnector.removePresubmissionData(any())(any(), any())
        ) thenReturn Future.successful(HttpResponse(OK))

        when(
          mockErsConnector.validateFileData(any(), any())(any(), any(), any())
        ) thenReturn Future.successful(HttpResponse(ACCEPTED))

        when(
          mockCacheUtil.cache(ArgumentMatchers.eq(mockCacheUtil.VALIDATED_SHEEETS), any(), anyString())(any(), any(), any())
        ) thenReturn Future.successful(CacheMap("1", Map("something" -> JsString("that"))))

				setAuthMocks()
          validationResults() { result =>
            status(result) mustBe SEE_OTHER
            redirectLocation(result).get must include("/errors-have-been-found-ods")
          }
      }
    }

    "show global error page" when {

      "ers connector returns a non 2xx status" in {

        when(
          mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.eq(mockCacheUtil.ersMetaData), any())(any(), any(), any())
        ) thenReturn Future.successful(validErsMetaData)

        when(
          mockSessionService.retrieveCallbackData()(any(), any())
        ) thenReturn Future.successful(Some(callbackData))

        when(
          mockErsConnector.removePresubmissionData(any())(any(), any())
        ) thenReturn Future.successful(HttpResponse(OK))

        when(
          mockErsConnector.validateFileData(any(), any())(any(), any(), any())
        ) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))

				setAuthMocks()
          validationResults() { result =>
            status(result) mustBe OK
            contentAsString(result) must include(messages("ers.global_errors.title"))

          }
      }

      "fetching ersMetaData fails" in {

        when(
          mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.eq(mockCacheUtil.ersMetaData), any())(any(), any(), any())
        ) thenReturn Future.failed(new Exception)

        when(
          mockSessionService.retrieveCallbackData()(any(), any())
        ) thenReturn Future.successful(Some(callbackData))

        when(
          mockErsConnector.removePresubmissionData(any())(any(), any())
        ) thenReturn Future.successful(HttpResponse(OK))

				setAuthMocks()
          validationResults() { result =>
            status(result) mustBe OK
            contentAsString(result) must include(messages("ers.global_errors.title"))

          }
      }

      "fetching callbackData fails" in {

        when(
          mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.eq(mockCacheUtil.ersMetaData), any())(any(), any(), any())
        ) thenReturn Future.successful(validErsMetaData)

        when(
          mockSessionService.retrieveCallbackData()(any(), any())
        ) thenReturn Future.failed(new Exception)

        when(
          mockErsConnector.removePresubmissionData(any())(any(), any())
        ) thenReturn Future.successful(HttpResponse(OK))

				setAuthMocks()
          validationResults() { result =>
            status(result) mustBe OK
            contentAsString(result) must include(messages("ers.global_errors.title"))
          }
      }

      "removing pre-submission data fails" in {

        when(
          mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.eq(mockCacheUtil.ersMetaData), any())(any(), any(), any())
        ) thenReturn Future.successful(validErsMetaData)

        when(
          mockSessionService.retrieveCallbackData()(any(), any())
        ) thenReturn Future.successful(Some(callbackData))

        when(
          mockErsConnector.removePresubmissionData(any())(any(), any())
        ) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))

				setAuthMocks()
          validationResults() { result =>
            status(result) mustBe OK
            contentAsString(result) must include(messages("ers.global_errors.title"))

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

    when(mockAttachmentsConnector.getFileUploadPartial()(ArgumentMatchers.any(), ArgumentMatchers.any()))
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

  val testOptString = Some("test")
  lazy val ersRequestObject = RequestObject(testOptString, testOptString, testOptString, Some("CSOP"), Some("CSOP"), testOptString, testOptString, testOptString, testOptString)

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

  lazy val mockAttachmentsConnector: AttachmentsConnector = mock[AttachmentsConnector]
  lazy val mockSessionService: SessionService = mock[SessionService]
  lazy val mockCacheUtil: CacheUtil = mock[CacheUtil]
  lazy val mockErsConnector: ErsConnector = mock[ErsConnector]

  /** Csv Callback List creation */
  lazy val jv: JsValue = Json.parse("""{}""")
  lazy val s: Map[String, JsValue] = Map("" -> jv)
  lazy val js = new JsObject(s)
  lazy val cb = new CallbackData("File", "File", 100.toLong, Some("File"), Some("File"), Some("File"), Some(js), noOfRows = None)
  lazy val csvFileData = new CsvFilesCallback("file0", Some(cb))
  lazy val csvCallBackList = new CsvFilesCallbackList(List(csvFileData, csvFileData, csvFileData))

  when(mockCacheUtil.fetch[ErsMetaData](ArgumentMatchers.refEq(CacheUtil.ersMetaData), ArgumentMatchers.any[String]())(ArgumentMatchers.any(),
    ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(validErsMetaData))
  when(mockCacheUtil.fetch[CheckFileType](ArgumentMatchers.refEq(CacheUtil.FILE_TYPE_CACHE), ArgumentMatchers.any[String]())(ArgumentMatchers.any(),
    ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(CheckFileType(Some("csv"))))
  when(mockCacheUtil.fetchOption[CsvFilesCallbackList](ArgumentMatchers.any[String](), ArgumentMatchers.any[String]())(ArgumentMatchers.any(),
    ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(csvCallBackList)))

  object TestFileUploadController extends FileUploadController {
    val attachmentsConnector: AttachmentsConnector = mockAttachmentsConnector
    val authConnector: PlayAuthConnector = mockAuthConnector
    val sessionService: SessionService = mockSessionService
    val cacheUtil: CacheUtil = mockCacheUtil
    val ersConnector: ErsConnector = mockErsConnector
  }
}

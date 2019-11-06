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
import models.CallbackData
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import services.SessionService
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import utils.{ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.Future

class CsvFileUploadCallbackControllerSpec extends UnitSpec with ERSFakeApplicationConfig with MockitoSugar with OneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val materializer: Materializer = app.materializer
  implicit val request: Request[_] = FakeRequest()

  lazy val metaData: JsObject = Json.obj("surname" -> Fixtures.surname, "firstForename" -> Fixtures.firstName)

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  lazy val mockSessionService: SessionService = mock[SessionService]

  lazy val fakeHeaders: FakeHeaders = FakeHeaders(Seq("Content-type" -> "application/json"))

  lazy val csvFileUploadCallbackController: CsvFileUploadCallbackController = new CsvFileUploadCallbackController {
    lazy val authConnector: AuthConnector = mockAuthConnector
    override lazy val sessionService: SessionService = mockSessionService
  }

  lazy val callbackData1 = CallbackData(collection = "collection",
    id = "someid",
    length = 1000L,
    name = Some("John"),
    contentType = Some("content-type"),
    customMetadata = Some(metaData),
    sessionId = Some("testId"),
    noOfRows = None)

  "calling callback" should {

    "return OK if attachments return valid callback data and storing callback data is successful" in {
      reset(mockSessionService)
      when(mockSessionService.storeCallbackData(any[CallbackData]())(any(), any()))
        .thenReturn(Future.successful(Some(callbackData1)))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData1))
      val result = await(csvFileUploadCallbackController.callback()(fakeRequest))
      status(result) shouldBe OK
    }

    "return INTERNAL_SERVER_ERROR if attachments return invalid callback data and storing callback data is successful" in {
      reset(mockSessionService)
      when(mockSessionService.storeCallbackData(any[CallbackData]())(any(), any()))
        .thenReturn(Future.successful(None))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData1))
      val result = await(csvFileUploadCallbackController.callback()(fakeRequest))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      bodyOf(result) shouldBe "Exception"
    }

    "return INTERNAL_SERVER_ERROR if storing callback data fails" in {
      val callbackData2 = callbackData1.copy(name = Some(Fixtures.firstName))
      reset(mockSessionService)
      when(mockSessionService.storeCallbackData(any[CallbackData]())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData2))
      val result = await(csvFileUploadCallbackController.callback()(fakeRequest))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      bodyOf(result) shouldBe "Exception occurred when attempting to store data"
    }
  }
}

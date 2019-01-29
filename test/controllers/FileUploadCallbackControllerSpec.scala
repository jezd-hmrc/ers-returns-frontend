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
import models.{CallbackData, ErsMetaData, SchemeInfo}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Request
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Application, Configuration}
import services.SessionService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class FileUploadCallbackControllerSpec extends PlaySpec with MockitoSugar with ERSFakeApplicationConfig with OneAppPerSuite {

  override implicit lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockCurrentConfig = mock[Configuration]
  lazy val mockSessionService = mock[SessionService]
  lazy val mockCacheUtil = mock[CacheUtil]

  object TestFileUploadCallbackController extends FileUploadCallbackController {
    lazy val authConnector = mockAuthConnector
    lazy val currentConfig = mockCurrentConfig
    lazy val sessionService = mockSessionService
    lazy val cacheUtil = mockCacheUtil
  }

  lazy val metaData: JsObject = Json.obj(
    "surname" -> Fixtures.surname,
    "firstForename" -> Fixtures.firstName
  )

  lazy val callbackData = CallbackData(collection = "collection", id = "someid", length = 1000L, name = Some(Fixtures.firstName), contentType = Some("content-type"), customMetadata = Some(metaData), sessionId = Some("testId"), noOfRows = None)

  lazy val fakeHeaders: FakeHeaders = FakeHeaders(Seq("Content-type" -> "application/json"))
  lazy val fakeRequest: FakeRequest[JsValue] = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData))

  lazy val sr = "XA1100000000000"
  lazy val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")
  lazy val rsc: ErsMetaData = new ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))

  "callback" must {
    "successfully store and validates callback data" in {

      when(mockSessionService.storeCallbackData(Matchers.any[CallbackData]())(Matchers.any[Request[_]](), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any[String](), Matchers.any[String]())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(rsc))
      when(mockCacheUtil.cache(Matchers.anyString(), Matchers.any(), Matchers.anyString())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(mock[CacheMap]))
      val result = TestFileUploadCallbackController.callback().apply(fakeRequest)

      status(result) must be(OK)
    }

    "successfully store and validates callback data when callbackData doesn't have session id" in {
      val callbackData = CallbackData(collection = "collection", id = "someid", length = 1000L, name = Some(Fixtures.firstName), contentType = Some("content-type"),
        customMetadata = None, sessionId = None, noOfRows = None)

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData))
      when(mockSessionService.storeCallbackData(Matchers.any[CallbackData]())(Matchers.any[Request[_]](), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(Some(callbackData)))
      when(mockCacheUtil.fetch[ErsMetaData](Matchers.any[String](), Matchers.any[String]())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(rsc))
      when(mockCacheUtil.cache(Matchers.anyString(), Matchers.any(), Matchers.anyString())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(mock[CacheMap]))
      val result = TestFileUploadCallbackController.callback().apply(fakeRequest)

      status(result) must be(OK)

    }

    "fail storing data with missing session id" in {
      val callbackData = CallbackData(collection = "collection", id = "someid", length = 1000L, name = Some(Fixtures.firstName), contentType = Some("content-type"),
        customMetadata = None, sessionId = Some("testId"), noOfRows = None)

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData))
      when(mockSessionService.storeCallbackData(Matchers.any[CallbackData]())(Matchers.any[Request[_]](), Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(None))
      val result = TestFileUploadCallbackController.callback().apply(fakeRequest)
      status(result) must be(INTERNAL_SERVER_ERROR)

    }

    "fail storing data when an exception occurs" in {
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = fakeHeaders, body = Json.toJson(callbackData))
      when(mockSessionService.storeCallbackData(Matchers.any[CallbackData]())(Matchers.any[Request[_]](), Matchers.any[HeaderCarrier]())).thenReturn(Future.failed(new RuntimeException))
      val result = TestFileUploadCallbackController.callback().apply(fakeRequest)
      status(result) must be(INTERNAL_SERVER_ERROR)

    }
  }

}

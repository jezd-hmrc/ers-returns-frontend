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

import java.net.URL
import java.time.Instant

import akka.stream.Materializer
import config.{ApplicationConfig, ApplicationConfigImpl}
import models.upscan._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, UpscanData}

import scala.concurrent.Future

class CsvFileUploadCallbackControllerSpec extends UnitSpec with ERSFakeApplicationConfig with MockitoSugar with OneAppPerSuite with BeforeAndAfterEach with UpscanData {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val materializer: Materializer = app.materializer
  implicit val request: Request[_] = FakeRequest()

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockCacheUtil: CacheUtil = mock[CacheUtil]

  def request(body: JsValue): FakeRequest[JsValue] = FakeRequest().withBody(body)
  val scRef = "scRef"
  val url: URL = new URL("http://localhost:9000/myUrl")

  lazy val csvFileUploadCallbackController: CsvFileUploadCallbackController = new CsvFileUploadCallbackController {
    lazy val authConnector: AuthConnector = mockAuthConnector
    override val cacheUtil: CacheUtil = mockCacheUtil
    override val appConfig: ApplicationConfig = new ApplicationConfigImpl(app.injector.instanceOf[Configuration]){
      import scala.concurrent.duration._
      override val retryDelay: FiniteDuration = 1 millisecond
    }
  }

  override def beforeEach(): Unit = {
    reset(mockCacheUtil)
    super.beforeEach()
  }

  "callback" should {
    val uploadId: UploadId = UploadId("ID")
    "update upload status to Uploaded Successfully" when {
      "callback is UpscanReadyCallback" in {
        val callbackCaptor = ArgumentCaptor.forClass(classOf[UploadStatus])
        val body = UpscanReadyCallback(Reference("Reference"), url, UploadDetails(Instant.now(), "checkSum", "fileMimeType", "fileName"))
        val jsonBody = Json.toJson(body)
        when(mockCacheUtil.cache(meq(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}"), callbackCaptor.capture, meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))
        val result = csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody))
        status(result) shouldBe OK
        callbackCaptor.getValue.isInstanceOf[UploadedSuccessfully] shouldBe true
        verify(mockCacheUtil, times(1))
          .cache(meq(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}"), meq(callbackCaptor.getValue), meq(scRef))(any(), any(), any())
      }
    }

    "update upload status to Failed" when {
      "callback is UpscanFailedCallback and upload is InProgress" in {
        val callbackCaptor = ArgumentCaptor.forClass(classOf[UploadStatus])
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)
        when(mockCacheUtil.cache(meq(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}"), callbackCaptor.capture, meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))
        val result = csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody))
        status(result) shouldBe OK
        callbackCaptor.getValue shouldBe Failed
        verify(mockCacheUtil, times(1))
          .cache(meq(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}"), meq(callbackCaptor.getValue), meq(scRef))(any(), any(), any())
      }
    }

    "return InternalServerError" when {
      "updating the cache fails" in {
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)
        when(mockCacheUtil.cache(meq(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}"), any[UploadStatus], meq(scRef))(any(), any(), any()))
          .thenReturn(Future.failed(new Exception("Test exception")))

        val result = await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR

        verify(mockCacheUtil, times(1))
          .cache(meq(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}"), any[UploadStatus], meq(scRef))(any(), any(), any())
      }

      "callback data is not in the correct format" in {
        val jsonBody = Json.parse("""{"key":"value"}""")
        val result = await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody)))
        status(result) shouldBe BAD_REQUEST
        verify(mockCacheUtil, never())
          .cache(any(), any(), meq(scRef))(any(), any(), any())
      }
    }
  }
}

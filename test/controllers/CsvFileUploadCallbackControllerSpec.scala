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

import java.net.URL
import java.time.Instant

import akka.stream.Materializer
import config.ApplicationConfig
import models.upscan._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json, _}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, UpscanData}

import scala.concurrent.Future
import scala.util.Try

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
    override val appConfig: ApplicationConfig = ApplicationConfig
  }

  override def beforeEach(): Unit = {
    reset(mockCacheUtil)
    super.beforeEach()
  }

  "callback" should {
    val uploadId: UploadId = UploadId("ID")
    "update upload status to Uploaded Successfully" when {
      "callback is UpscanReadyCallback and upload is InProgress" in {
        val callbackCaptor = ArgumentCaptor.forClass(classOf[UpscanCsvFilesCallbackList])
        val body = UpscanReadyCallback(Reference("Reference"), url, UploadDetails(Instant.now(), "checkSum", "fileMimeType", "fileName"))
        val jsonBody = Json.toJson(body)
        val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
          List(
            UpscanCsvFilesCallback(uploadId, "file1", InProgress),
            UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
          )
        )
        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any()))
          .thenReturn(upscanCsvFilesCallbackList)
        when(mockCacheUtil.cache(meq(CacheUtil.CHECK_CSV_FILES), callbackCaptor.capture, meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))
        val result = csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody))
        status(result) shouldBe OK
        callbackCaptor.getValue.files.find(_.uploadId == uploadId).get should matchPattern {
          case UpscanCsvFilesCallback(`uploadId`, "file1", _: UploadedSuccessfully) =>
        }
        verify(mockCacheUtil, times(1))
          .cache(meq(CacheUtil.CHECK_CSV_FILES), callbackCaptor.capture, meq(scRef))(any(), any(), any())
        verify(mockCacheUtil, times(1))
          .fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any())
      }
    }

    "update upload status to Failed" when {
      "callback is UpscanFailedCallback and upload is InProgress" in {
        val callbackCaptor = ArgumentCaptor.forClass(classOf[UpscanCsvFilesCallbackList])
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)
        val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
          List(
            UpscanCsvFilesCallback(uploadId, "file1", InProgress),
            UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
          )
        )
        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any()))
          .thenReturn(upscanCsvFilesCallbackList)
        when(mockCacheUtil.cache(meq(CacheUtil.CHECK_CSV_FILES), callbackCaptor.capture, meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))
        val result = csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody))
        status(result) shouldBe OK
        callbackCaptor.getValue.files.find(_.uploadId == uploadId).get should matchPattern {
          case UpscanCsvFilesCallback(`uploadId`, "file1", Failed) =>
        }
        verify(mockCacheUtil, times(1))
          .cache(meq(CacheUtil.CHECK_CSV_FILES), callbackCaptor.capture, meq(scRef))(any(), any(), any())
        verify(mockCacheUtil, times(1))
          .fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any())

      }
    }

    "call cache upto 5 times" when {
      "the file upload in cache is not InProgress" in {
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)
        val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
          List(
            UpscanCsvFilesCallback(uploadId, "file1", NotStarted),
            UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
          )
        )
        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any()))
          .thenReturn(upscanCsvFilesCallbackList)
        when(mockCacheUtil.cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))

        Try(await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody))))

        verify(mockCacheUtil, never())
          .cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any())
        verify(mockCacheUtil, times(5))
          .fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any())
      }
    }

    "return InternalServerError" when {
      "fetching the cache fails" in {
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)

        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any()))
          .thenReturn(Future.failed(new Exception("Expected Exception")))
        when(mockCacheUtil.cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))

        val result = await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR

        verify(mockCacheUtil, never())
          .cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any())
        verify(mockCacheUtil, times(1))
          .fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any())
      }

      "updating the cache fails" in {
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)
        val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
          List(
            UpscanCsvFilesCallback(uploadId, "file1", InProgress),
            UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
          )
        )

        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any()))
          .thenReturn(upscanCsvFilesCallbackList)
        when(mockCacheUtil.cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any()))
          .thenReturn(Future.failed(new Exception("Test exception")))

        val result = await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR

        verify(mockCacheUtil, times(1))
          .cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any())
        verify(mockCacheUtil, times(1))
          .fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any())
      }

      "callback data is not in the correct format" ignore {
        val jsonBody = Json.parse("""{"key":"value"}""")
        val result = await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody)))
        status(result) shouldBe BAD_REQUEST
        verify(mockCacheUtil, never())
          .cache(meq(CacheUtil.CHECK_CSV_FILES), any(), meq(scRef))(any(), any(), any())
        verify(mockCacheUtil, never())
          .fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any())
      }

      "the file upload in cache is not InProgress after 5 calls" in {
        val callbackCaptor = ArgumentCaptor.forClass(classOf[UpscanCsvFilesCallbackList])
        val body = UpscanFailedCallback(Reference("ref"), ErrorDetails("failed", "message"))
        val jsonBody = Json.toJson(body)
        val upscanCsvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(
          List(
            UpscanCsvFilesCallback(uploadId, "file1", NotStarted),
            UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
          )
        )
        when(mockCacheUtil.fetch[UpscanCsvFilesCallbackList](meq(CacheUtil.CHECK_CSV_FILES), meq(scRef))(any(),any(), any()))
          .thenReturn(upscanCsvFilesCallbackList)
        when(mockCacheUtil.cache(meq(CacheUtil.CHECK_CSV_FILES), callbackCaptor.capture, meq(scRef))(any(), any(), any()))
          .thenReturn(Future.successful(mock[CacheMap]))

        val result = await(csvFileUploadCallbackController.callback(uploadId, scRef)(request(jsonBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}

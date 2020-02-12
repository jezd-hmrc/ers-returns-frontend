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

package connectors

import akka.stream.Materializer
import metrics.Metrics
import models.{CallbackData, ERSAuthData, SchemeInfo, ValidatorData}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.EmpRef
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.UnitSpec
import utils.{AuthHelper, ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.Future
import uk.gov.hmrc.http.{HttpGet, HttpPost, HttpResponse}

class ERSConnectorSpec extends UnitSpec with MockitoSugar with OneAppPerSuite with ERSFakeApplicationConfig with AuthHelper {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  implicit lazy val authContext: ERSAuthData = defaultErsAuthData
  implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()


  lazy val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")

  "calling sendData" should {

    lazy val mockHttp = mock[HttpPost]
    lazy val mockMetrics: Metrics = mock[Metrics]

    lazy val schemeInfo1 = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "QQQ", "QQQ")

    lazy val callbackData = CallbackData(
      collection = "collection",
      id = "someid",
      length = 1000L,
      name = Some(Fixtures.firstName),
      contentType = Some("content-type"),
      sessionId = Some("testId"),
      customMetadata = Some(Json.obj("sessionId" -> "testId")), noOfRows = None)

    lazy val ersConnectorUnderTest: ErsConnector = new ErsConnector {

      override lazy val metrics: Metrics = mockMetrics

      override def httpPost: HttpPost = mockHttp

      override def httpGet: HttpGet = mock[HttpGet]

      override def ersUrl = "ers-returns"

      override def validatorUrl = "ers-file-validator"
		}

    "successful validation" in {
      reset(mockHttp)
      when(
        mockHttp.POST[ValidatorData, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(
        Future.successful(HttpResponse(OK))
      )

      val result = await(ersConnectorUnderTest.validateFileData(callbackData, schemeInfo))
      result.status shouldBe OK
    }

    "validation fails" in {
      reset(mockHttp)
      when(
        mockHttp.POST[ValidatorData, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(
        Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))
      )

      val result = await(ersConnectorUnderTest.validateFileData(callbackData, schemeInfo))
      result.status shouldBe INTERNAL_SERVER_ERROR
    }

    "validator throw Exception" in {
      reset(mockHttp)
      doThrow(
        new RuntimeException
      ).when(mockHttp).POST[ValidatorData, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())

      intercept[Exception] {
        ersConnectorUnderTest.validateFileData(callbackData, schemeInfo1)
      }
    }
  }

  "calling retrieveSubmissionData" should {

    lazy val mockHttp = mock[HttpPost]
    lazy val mockMetrics: Metrics = mock[Metrics]

    lazy val ersConnector: ErsConnector = new ErsConnector {
      override lazy val metrics: Metrics = mockMetrics

      override def httpPost: HttpPost = mockHttp

      override def httpGet: HttpGet = mock[HttpGet]

      override def ersUrl = "ers-returns"

      override def validatorUrl = "ers-file-validator"
    }

    lazy val data: JsObject = Json.obj(
      "schemeRef" -> "XA1100000000000",
      "confTime" -> "2016-08-05T11:14:43"
    )

    "successful retrieving" in {
      reset(mockHttp)
      when(
        mockHttp.POST[SchemeInfo, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(
        Future.successful(HttpResponse(OK))
      )

      val result = await(ersConnector.retrieveSubmissionData(data))
      result.status shouldBe OK
    }

    "failed retrieving" in {
      reset(mockHttp)
      when(
        mockHttp.POST[SchemeInfo, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(
        Future.successful(HttpResponse(INTERNAL_SERVER_ERROR))
      )

      val result = await(ersConnector.retrieveSubmissionData(data))
      result.status shouldBe INTERNAL_SERVER_ERROR
    }

    "throws exception" in {
      reset(mockHttp)
      when(
        mockHttp.POST[SchemeInfo, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(
        Future.failed(new RuntimeException)
      )

      intercept[Exception] {
        await(ersConnector.retrieveSubmissionData(data))
      }
    }

  }
}

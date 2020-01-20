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

import java.util.UUID

import akka.stream.Materializer
import com.typesafe.config.Config
import config.{ERSFileValidatorAuditConnector, WSHttp}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.{Application, Configuration, Play}
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}
import uk.gov.hmrc.play.partials.HeaderCarrierForPartials
import utils.ERSFakeApplicationConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpPut, HttpResponse}
import uk.gov.hmrc.http.logging.{RequestId, SessionId}
import uk.gov.hmrc.play.config.AppName

class AttachmentsConnectorSpec extends PlaySpec with OneServerPerSuite with ERSFakeApplicationConfig with MockitoSugar with BeforeAndAfterEach {

  override implicit lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  class MockHttp extends WSHttp with WSGet with WSPost with HttpAuditing {

    override def appName: String = AppName(Play.current.configuration).appName
    override protected def appNameConfiguration: Configuration = Play.current.configuration

    protected def configuration: Option[Config] = Some(Play.current.configuration.underlying)
  }

  lazy implicit val hcfp = HeaderCarrierForPartials(HeaderCarrier(), "encodedCookies")

  lazy val mockHttpPut = mock[HttpPut]

  lazy val mockHttp = mock[MockHttp]

  lazy val sr = "AA0000000000000"

  object TestAttachmentsConnector extends AttachmentsConnector {
    override val http = mockHttp
  }

  "AttachmentsConnector" must {

    "getFileUploadPartial" must {

      "returns the partial from the attachment service" in {
        implicit val request = FakeRequest()

        val html = "<h1>helloworld</h1>"
        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, responseString = Some
        (html))))
        TestAttachmentsConnector.getFileUploadPartial().map {
          response => response.body must equal(html)
        }
      }

    }

  }

  "UploadConfig with session id" must {
    implicit val hc = HeaderCarrier(requestId = Some(RequestId(UUID.randomUUID().toString)), sessionId = Some(SessionId("test")))
    Play.start(new FakeApplication)
    val request = FakeRequest().withHeaders(HeaderNames.HOST -> "test.com")
    val config = UploadConfig()(request, hcfp)
  }

  "UploadConfig" must {
    implicit val hc = HeaderCarrier(requestId = Some(RequestId(UUID.randomUUID().toString)))
    val request = FakeRequest().withHeaders(HeaderNames.HOST -> "localhost:9290")
    val config = UploadConfig()(request, hcfp)

    "have the collection" in {
      config must include("collection=ers-files")
    }

    "have a the attachments service url" in {
      config must include("http://localhost:8895/attachments-internal/uploader")
    }

    "have a success url parameter" in {
      config must include("onSuccess=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fif-there-are-no-errors-ods")
    }

    "have a failure url parameter" in {
      config must include("onFailure=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Ffile-upload%2Ffailure")
    }

    "have a callback url parameter" in {
      config must include("callbackUrl=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Ffile-upload%2Fcallback")
    }

    "accept .ods" in {
      config must include("accepts=.ods")
    }


    "return the correct url with parameters" in {
      config must be(
        "http://localhost:8895/attachments-internal/uploader?" +
          "callbackUrl=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Ffile-upload%2Fcallback" +
          "&onSuccess=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fif-there-are-no-errors-ods" +
          "&onFailure=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Ffile-upload%2Ffailure" +
          "&accepts=.ods" +
          "&collection=ers-files" +
          "&decompressedTargetFile=content.xml"
      )
    }
  }

  "UploadCsvConfig" must {
    implicit val hc = HeaderCarrier(requestId = Some(RequestId(UUID.randomUUID().toString)))
    val request = FakeRequest().withHeaders(HeaderNames.HOST -> "localhost:9290")
    val config = UploadCsvConfig()(request, hcfp)

    "have the collection" in {
      config must include("collection=ers-files")
    }

    "have a the attachments service url" in {
      config must include("http://localhost:8895/attachments-internal/uploader")
    }

    "have a success url parameter" in {
      config must include("onSuccess=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fif-there-are-no-errors-csv")
    }

    "have a failure url parameter" in {
      config must include("onFailure=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fcsv-file-upload%2Ffailure")
    }

    "have a callback url parameter" in {
      config must include("callbackUrl=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fcsv-file-upload%2Fcallback")
    }

    "accept .csv" in {
      config must include("accepts=.csv")
    }


    "return the correct url with parameters" in {
      config must be(
        "http://localhost:8895/attachments-internal/uploader?" +
          "callbackUrl=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fcsv-file-upload%2Fcallback" +
          "&onSuccess=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fif-there-are-no-errors-csv" +
          "&onFailure=http%3A%2F%2Flocalhost%3A9290%2Fsubmit-your-ers-annual-return%2Fcsv-file-upload%2Ffailure" +
          "&accepts=.csv" +
          "&collection=ers-files"
      )
    }
  }
}

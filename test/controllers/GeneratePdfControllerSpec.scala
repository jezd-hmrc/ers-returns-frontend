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

import java.io.ByteArrayOutputStream

import akka.stream.Materializer
import models._
import models.upscan.{UploadId, UploadedSuccessfully, UpscanCsvFilesCallback, UpscanCsvFilesCallbackList}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.pdf.{ErsContentsStreamer, ErsReceiptPdfBuilderService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.test.UnitSpec
import utils.{AuthHelper, CacheUtil, ERSFakeApplicationConfig, PageBuilder}
import utils.Fixtures._

import scala.concurrent.Future

class GeneratePdfControllerSpec extends UnitSpec with ERSFakeApplicationConfig with AuthHelper with OneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer
  implicit lazy val messages: Messages = Messages(Lang("en"), app.injector.instanceOf[MessagesApi])
  implicit val requests: Request[_] = FakeRequest()

  lazy val pdfBuilderMock: ErsReceiptPdfBuilderService = mock[ErsReceiptPdfBuilderService]
  lazy val cache: CacheUtil = mock[CacheUtil]
  lazy val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "1", "2016", "EMI", "EMI")
  lazy val rsc = ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
  lazy val ersSummary = ErsSummary("testbundle", "2", None, DateTime.now, rsc, None, None, None, None, None, None, None, None)
  lazy val cacheMap: CacheMap = mock[CacheMap]

  "pdf generation conroller" should {

    "give a redirect status (to company authentication frontend) on GET if user is not authenticated" in {
			setUnauthorisedMocks()
      val controller = createController()
      val result = controller.buildPdfForBundle("", "").apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }

    "give a status OK on GET if user is authenticated" in {
			setAuthMocks()
      val controller = createController()
      val result = controller.buildPdfForBundle("", "").apply(buildFakeRequestWithSessionIdCSOP("GET"))
      status(result) shouldBe Status.OK
    }

    "direct to errors page if fetch all res pdf throws exception" in {
      val controller = createController(fetchAllRes = false)
      val result = await(controller.generatePdf(ersRequestObject, "", "")(buildFakeUser, buildFakeRequestWithSessionIdCSOP("GET"), hc))
      contentAsString(result) should include(messages("ers.global_errors.message"))
      contentAsString(result) shouldBe contentAsString(createController().getGlobalErrorPage)
    }

    "direct to errors page if get all data res pdf throws exception" in {
      val controller = createController(getAllDataRes = false)
      val result = await(controller.generatePdf(ersRequestObject, "", "")(buildFakeUser, buildFakeRequestWithSessionIdCSOP("GET"), hc))
      contentAsString(result) should include(messages("ers.global_errors.message"))
      contentAsString(result) shouldBe contentAsString(createController().getGlobalErrorPage)
    }

    "use bundle ref to generate the confirmation pdf filename (NilReturn)" in {
      val controller = createController()
      val res = await(controller.generatePdf(ersRequestObject, "123456", "8 August 2016, 4:28pm")(buildFakeUser, buildFakeRequestWithSessionId("GET"), hc))
      res.header.headers("Content-Disposition") should include("123456-confirmation.pdf")
    }

    "use bundle ref to generate the confirmation pdf filename (CSV File submission)" in {
      val controller = createController(isNilReturn = false)
      val res = await(controller.generatePdf(ersRequestObject, "123456", "8 August 2016, 4:28pm")(buildFakeUser, buildFakeRequestWithSessionId("GET"), hc))
      res.header.headers("Content-Disposition") should include("123456-confirmation.pdf")
    }

    "use bundle ref to generate the confirmation pdf filename (ODS File submission)" in {
      val controller = createController(isNilReturn = false, fileTypeCSV = false)
      val res = await(controller.generatePdf(ersRequestObject, "123456", "8 August 2016, 4:28pm")(buildFakeUser, buildFakeRequestWithSessionId("GET"), hc))
      res.header.headers("Content-Disposition") should include("123456-confirmation.pdf")
    }

  }

  def createController(fetchAllRes: Boolean = true, getAllDataRes: Boolean = true, isNilReturn: Boolean = true,
                       fileTypeCSV: Boolean = true): PdfGenerationController = new PdfGenerationController {

    override val cacheUtil: CacheUtil = cache
    override val pdfBuilderService: ErsReceiptPdfBuilderService = pdfBuilderMock
    override val authConnector: PlayAuthConnector = mockAuthConnector
    val byteArrayOutputStream = mock[ByteArrayOutputStream]

    val callbackData = UploadedSuccessfully("name", "downloadUrl")
    val csvFilesCallBack = UpscanCsvFilesCallback(UploadId("uploadId"), "file0", callbackData)
    val csvFilesCallbackList = UpscanCsvFilesCallbackList(List(csvFilesCallBack))

    when(pdfBuilderMock.createPdf(any[ErsContentsStreamer], any[ErsSummary], any(), any())(any())).thenReturn(byteArrayOutputStream)
    when(cache.fetch[RequestObject](any())(any(), any(), any(), any())).thenReturn(Future.successful(ersRequestObject))
    when(cache.fetch[ErsMetaData](refEq(CacheUtil.ersMetaData), anyString())(any(), any(), any())).thenReturn(Future.successful(rsc))
    when(cacheMap.getEntry[UpscanCsvFilesCallbackList](refEq(CacheUtil.CHECK_CSV_FILES))(any()))
      .thenReturn(Future.successful(Some(csvFilesCallbackList)))
    when(cacheMap.getEntry[String](refEq(CacheUtil.FILE_NAME_CACHE))(any())).thenReturn(Future.successful(Some("test.ods")))
    when(byteArrayOutputStream.toByteArray).thenReturn(Array[Byte]())

    if (fileTypeCSV) {
      when(cacheMap.getEntry[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE))(any()))
        .thenReturn(Future.successful(Some(new CheckFileType(Some(PageBuilder.OPTION_CSV)))))
    }
    else {
      when(cacheMap.getEntry[CheckFileType](refEq(CacheUtil.FILE_TYPE_CACHE))(any()))
        .thenReturn(Future.successful(Some(new CheckFileType(Some(PageBuilder.OPTION_ODS)))))
    }

    if (isNilReturn) {
      when(cacheMap.getEntry[ReportableEvents](refEq(CacheUtil.reportableEvents))(any()))
        .thenReturn(Future.successful(Some(new ReportableEvents(Some(PageBuilder.OPTION_NIL_RETURN)))))
    }
    else {
      when(cacheMap.getEntry[ReportableEvents](refEq(CacheUtil.reportableEvents))(any()))
        .thenReturn(Future.successful(Some(new ReportableEvents(Some(PageBuilder.OPTION_UPLOAD_SPREEDSHEET)))))
    }

    if (fetchAllRes) {
      when(cache.fetchAll(anyString())(any(), any()))
        .thenReturn(Future.successful(cacheMap))
    }
    else {
      when(cache.fetchAll(anyString())(any(), any()))
        .thenReturn(Future.failed(new Exception))
    }

    if (getAllDataRes) {
      when(cache.getAllData(anyString(), any[ErsMetaData]())(any(), any(), any()))
        .thenReturn(Future.successful(ersSummary))
    }
    else {
      when(cache.getAllData(anyString(), any[ErsMetaData]())(any(), any(), any()))
        .thenReturn(Future.failed(new Exception))
    }
  }

}

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
import connectors.AuditServiceConnector
import models._
import models.upscan._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Matchers, Mockito}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.audit.AuditServiceConnector
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.UnitSpec
import utils.Fixtures.ersRequestObject
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}
import utils.{AuthHelper, CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}

import scala.concurrent.Future

class CheckCsvFilesControllerSpec extends UnitSpec with ERSFakeApplicationConfig with OneAppPerSuite with AuthHelper {

	override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
	implicit lazy val mat: Materializer = app.materializer

	lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
	implicit lazy val messages: Messages = messagesApi.preferred(Seq(Lang.get("en").get))
  implicit val request: Request[_] = FakeRequest()

  "calling checkCsvFilesPage" should {

    val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
      override val cacheUtil: CacheUtil = mock[CacheUtil]
      override val pageBuilder: PageBuilder = mock[PageBuilder]
			override val authConnector: PlayAuthConnector = mockAuthConnector

      override def showCheckCsvFilesPage()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future.successful(Ok)
    }

    "redirect to company authentication frontend if user is not authenticated" in {
			setUnauthorisedMocks()
      val result = checkCsvFilesController.checkCsvFilesPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }
  }

  "calling showCheckCsvFilesPage" should {

    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
      override val cacheUtil: CacheUtil = mockCacheUtil
      override val pageBuilder: PageBuilder = mock[PageBuilder]
      when(mockCacheUtil.remove(ArgumentMatchers.eq(CacheUtil.CHECK_CSV_FILES))(any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK)))
    }

    "show CheckCsvFilesPage" in {
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[RequestObject](any())(any(), any(), any(), any())
      ).thenReturn(ersRequestObject)

      val result = await(checkCsvFilesController.showCheckCsvFilesPage()(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
    }
  }

  "calling checkCsvFilesPage" should {

    val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
      override val cacheUtil: CacheUtil = mock[CacheUtil]
      override val pageBuilder: PageBuilder = mock[PageBuilder]
			override val authConnector: PlayAuthConnector = mockAuthConnector

      override def validateCsvFilesPageSelected()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future.successful(Ok)
    }

    "redirect to company authentication frontend if user is not authenticated to access checkCsvFilesPage" in {
			setUnauthorisedMocks()
      val result = checkCsvFilesController.checkCsvFilesPageSelected().apply(FakeRequest("GET", ""))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location").contains("/gg/sign-in") shouldBe true
    }
  }

  "calling validateCsvFilesPageSelected" should {

    val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
      override val cacheUtil: CacheUtil = mock[CacheUtil]
      override val pageBuilder: PageBuilder = mock[PageBuilder]
      override def performCsvFilesPageSelected(formData: CsvFilesList)(implicit request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = Future.successful(Ok)
    }

    "return the result of performCsvFilesPageSelected if data is valid" in {

      val csvFilesListData: Map[String, String] = Map(
        ("files[0].fileId", "file0"),
        ("files[0].isSelected", "1"),
        ("files[1].fileId", "file1"),
        ("files[1].isSelected", "2"),
        ("files[2].fileId", "file2"),
        ("files[2].isSelected", "")
      )
      val form = RsFormMappings.csvFileCheckForm.bind(csvFilesListData)

      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = await(checkCsvFilesController.validateCsvFilesPageSelected()(Fixtures.buildFakeUser, request, hc))
      status(result) shouldBe OK
    }

    "return the result of reloadWithError if data is invalid" in {

      val csvFilesListData: Map[String, String] = Map(
        ("files[0].fileId", ""),
        ("files[0].isSelected", "5")
      )
      val form = RsFormMappings.csvFileCheckForm.bind(csvFilesListData)

      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
      val result = await(checkCsvFilesController.validateCsvFilesPageSelected()(Fixtures.buildFakeUser, request, hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe "/submit-your-ers-annual-return/choose-csv-files"
    }

  }

  "calling performCsvFilesPageSelected" should {

    val mockListCsvFilesCallback: UpscanCsvFilesList = mock[UpscanCsvFilesList](Mockito.RETURNS_DEEP_STUBS)
    val mockCacheUtil: CacheUtil = mock[CacheUtil]

    lazy val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
      override val cacheUtil: CacheUtil = mockCacheUtil
      override val pageBuilder: PageBuilder = mock[PageBuilder]
			override val authConnector: PlayAuthConnector = mockAuthConnector

      override def createCacheData(csvFilesList: List[CsvFiles]): UpscanCsvFilesList = mockListCsvFilesCallback
    }

    val formData: CsvFilesList = CsvFilesList(
      List(
        CsvFiles("file0", None),
        CsvFiles("file1", Some("1")),
        CsvFiles("file2", None),
        CsvFiles("file3", None),
        CsvFiles("file4", Some("1"))
      )
    )

    "return the result of reloadWithError if createCacheData returns empty list" in {
      reset(mockListCsvFilesCallback)
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[RequestObject](refEq(mockCacheUtil.ersRequestObject))(any(), any(), any(), any())
      ) thenReturn Future.successful(ersRequestObject)
      when(
        mockListCsvFilesCallback.ids.isEmpty
      ).thenReturn(true)

      val result = await(checkCsvFilesController.performCsvFilesPageSelected(formData)(Fixtures.buildFakeRequestWithSessionIdCSOP("POST"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe "/submit-your-ers-annual-return/choose-csv-files"
    }

    "redirect to next page if createCacheData returns list with data and caching is successful" in {
      reset(mockListCsvFilesCallback)
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[RequestObject](refEq(mockCacheUtil.ersRequestObject))(any(), any(), any(), any())
      ) thenReturn Future.successful(ersRequestObject)

      when(
        mockCacheUtil.cache(anyString(), any[UpscanCsvFilesCallbackList](), anyString())(any(), any(), any())
      ) thenReturn Future.successful(mock[CacheMap])

      val result = await(checkCsvFilesController.performCsvFilesPageSelected(formData)(Fixtures.buildFakeRequestWithSessionIdCSOP("POST"), hc))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should include ("/upload-")
    }

    "direct to ers errors page if createCacheData returns list with data and caching fails" in {
      reset(mockListCsvFilesCallback)
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[RequestObject](refEq(mockCacheUtil.ersRequestObject))(any(), any(), any(), any())
      ) thenReturn Future.successful(ersRequestObject)

      when(
        mockCacheUtil.cache(anyString(), any[UpscanCsvFilesCallbackList](), anyString())(any(), any(), any())
      ) thenReturn Future.failed(new RuntimeException)


      val result = await(checkCsvFilesController.performCsvFilesPageSelected(formData)(Fixtures.buildFakeRequestWithSessionIdCSOP("POST"), hc))
      contentAsString(result) shouldBe contentAsString(checkCsvFilesController.getGlobalErrorPage)
      contentAsString(result) should include(messages("ers.global_errors.message"))
    }

    "direct to ers errors page if fetching request object fails fails" in {
      reset(mockListCsvFilesCallback)
      reset(mockCacheUtil)
      when(
        mockCacheUtil.fetch[RequestObject](refEq(mockCacheUtil.ersRequestObject))(any(), any(), any(), any())
      ) thenReturn Future.failed(new Exception)

      when(
        mockCacheUtil.cache(anyString(), any[UpscanCsvFilesCallbackList](), anyString())(any(), any(), any())
      ) thenReturn Future.successful(mock[CacheMap])

      val result = await(checkCsvFilesController.performCsvFilesPageSelected(formData)(Fixtures.buildFakeRequestWithSessionIdCSOP("POST"), hc))
      contentAsString(result) shouldBe contentAsString(checkCsvFilesController.getGlobalErrorPage)
      contentAsString(result) should include(messages("ers.global_errors.message"))
    }

  }

  "calling createCacheData" should {

    lazy val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
      override val cacheUtil: CacheUtil = mock[CacheUtil]
      override val pageBuilder: PageBuilder = mock[PageBuilder]
			override val authConnector: PlayAuthConnector = mockAuthConnector

		}

    val formData: List[CsvFiles] = List(
      CsvFiles("file0", None),
      CsvFiles("file1", Some("1")),
      CsvFiles("file2", None),
      CsvFiles("file3", None),
      CsvFiles("file4", Some("1"))
    )

    "return only selected files" in {
      val result = checkCsvFilesController.createCacheData(formData)
      result.ids.size shouldBe 2
      result.ids.foreach {
        _ should matchPattern {
          case UpscanIds(UploadId(_), _: String, NotStarted) =>
        }
      }

      result.noOfUploads shouldBe 0
      result.noOfFilesToUpload shouldBe 2
    }

  }

  "calling reloadWithError" should {

    lazy val checkCsvFilesController: CheckCsvFilesController = new CheckCsvFilesController {
			override val authConnector: PlayAuthConnector = mockAuthConnector
      override val cacheUtil: CacheUtil = mock[CacheUtil]
      override val pageBuilder: PageBuilder = mock[PageBuilder]
    }

    "reload same page, showing error" in {
      val result = await(checkCsvFilesController.reloadWithError())
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe routes.CheckCsvFilesController.checkCsvFilesPage().toString
    }
  }

}

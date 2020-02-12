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
import connectors.ErsConnector
import metrics.Metrics
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json
import play.api.libs.json._
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SessionService
import uk.gov.hmrc.domain.EmpRef
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedCache}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}
import utils.Fixtures.ersRequestObject

class SummaryDeclarationControllerTest extends UnitSpec with ERSFakeApplicationConfig with MockitoSugar with OneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer
  implicit lazy val messages: Messages = Messages(Lang("en"), app.injector.instanceOf[MessagesApi])
  implicit val request: Request[_] = FakeRequest()

  lazy val mockHttp = mock[HttpPost]
  lazy val mockHttpGet = mock[HttpGet]
  lazy val mockSessionCache = mock[SessionService]

  def buildFakeSummaryDeclarationController() = new SummaryDeclarationController {
    var fetchAllMapVal = "e"
    var fetchMapVal = "e"
    val schemeInfo = SchemeInfo("XA1100000000000", DateTime.now, "2", "2016", "EMI", "EMI")
    val rsc: ErsMetaData = new ErsMetaData(schemeInfo, "ipRef", Some("aoRef"), "empRef", Some("agentRef"), Some("sapNumber"))
    override val ersConnector: ErsConnector =
      new ErsConnector {
        override lazy val metrics: Metrics = mock[Metrics]

        override def httpPost: HttpPost = mockHttp

        override def httpGet: HttpGet = mockHttpGet

        override def ersUrl = "ers-returns"

        override def validatorUrl = "ers-file-validator"

        override def connectToEtmpSapRequest(schemeRef: String)(implicit authContext: ERSAuthData, hc: HeaderCarrier): Future[String] = Future("1234567890")

      }
    when(mockHttp.POST[ValidatorData, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK)))

    override val cacheUtil: CacheUtil = new CacheUtil {
      override val sessionService: SessionService = mockSessionCache

      override def cache[T](key: String, body: T, cacheId: String)(implicit hc: HeaderCarrier, formats: json.Format[T], request: Request[AnyRef]) = {
        Future.successful(null)
      }

      override def fetch[T](key: String, cacheId: String)(implicit hc: HeaderCarrier, formats: json.Format[T], request: Request[AnyRef]): Future[T] = {
        fetchMapVal match {
          case "e" => Future(throw new NoSuchElementException)
          case "withSchemeTypeSchemeRef" => {
            key match {
              case CacheUtil.ersMetaData => {
                Future.successful(rsc.asInstanceOf[T])
              }
              case CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER => {
                val gsc: GroupSchemeInfo = new GroupSchemeInfo(Option("1"), None)
                Future.successful(gsc.asInstanceOf[T])
              }
              case CacheUtil.SCHEME_ORGANISER_CACHE => {
                val soc: SchemeOrganiserDetails = new SchemeOrganiserDetails(Fixtures.companyName,
                  "Add1", Option("Add2"), Option("Add3"), Option("Add4"), Option("AA111AA"),
                  Option("United Kingdom"), Option("AB123456"), Option("1234567890"))
                Future.successful(soc.asInstanceOf[T])
              }
              case CacheUtil.ALT_AMENDS_CACHE_CONTROLLER => {
                val aac: AlterationAmends = new AlterationAmends(Option("1"), Option("1"), Option("1"), Option("1"), Option("1"))
                Future.successful(aac.asInstanceOf[T])
              }
            }
          }
        }
      }

      override def fetchOption[T](key: String, cacheId: String)(implicit hc: HeaderCarrier, formats: json.Format[T], request: Request[AnyRef]): Future[Option[T]] = {
        fetchMapVal match {
          case "e" => Future(throw new NoSuchElementException)
          case "withSchemeTypeSchemeRef" => {
            key match {
              case CacheUtil.ersMetaData => {
                Future.successful(Option(rsc.asInstanceOf[T]))
              }
              case CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER => {
                val gsc: GroupSchemeInfo = new GroupSchemeInfo(Option("1"), None)
                Future.successful(Option(gsc.asInstanceOf[T]))
              }
              case CacheUtil.SCHEME_ORGANISER_CACHE => {
                val soc: SchemeOrganiserDetails = new SchemeOrganiserDetails(Fixtures.companyName,
                  "Add1", Option("Add2"), Option("Add3"), Option("Add4"), Option("AA111AA"),
                  Option("United Kingdom"), Option("AB123456"), Option("1234567890"))
                Future.successful(Option(soc.asInstanceOf[T]))
              }
              case CacheUtil.ALT_AMENDS_CACHE_CONTROLLER => {
                val aac: AlterationAmends = new AlterationAmends(Option("1"), Option("1"), Option("1"), Option("1"), Option("1"))
                Future.successful(Option(aac.asInstanceOf[T]))
              }
            }
          }
        }
      }

      @throws(classOf[NoSuchElementException])
      override def fetchAll(cacheId: String)(implicit hc: HeaderCarrier, request: Request[AnyRef]): Future[CacheMap] = {

        val schemeOrganiser: SchemeOrganiserDetails = new SchemeOrganiserDetails(Fixtures.companyName,
          "Add1", Option("Add2"), Option("Add3"), Option("Add4"),
          Option("UK"), Option("AA111AA"), Option("AB123456"), Option("1234567890"))
        val groupSchemeInfo: GroupSchemeInfo = new GroupSchemeInfo(Option("1"), None)
        val gscomp: CompanyDetails = new CompanyDetails(Fixtures.companyName, "Adress Line 1", None, None, None, None, None, None, None)
        val gscomps: CompanyDetailsList = new CompanyDetailsList(List(gscomp))

        val reportableEvents: ReportableEvents = new ReportableEvents(Some(PageBuilder.OPTION_YES))
        val fileTypeCSV: CheckFileType = new CheckFileType(Some(PageBuilder.OPTION_CSV))
        val fileTypeODS: CheckFileType = new CheckFileType(Some(PageBuilder.OPTION_ODS))
        val callbackData: CallbackData = new CallbackData("", "", 0, None, None, None, None, None)
        val csvFilesCallBack = new CsvFilesCallback("file0", Some(callbackData))
        val csvFilesCallbackList: CsvFilesCallbackList = new CsvFilesCallbackList(List(csvFilesCallBack))
        val trustees: TrusteeDetails = new TrusteeDetails("T Name", "T Add 1", None, None, None, None, None)
        val trusteesList: TrusteeDetailsList = new TrusteeDetailsList(List(trustees))
        val fileNameODS: String = "test.osd"

        fetchAllMapVal match {
          case "e" => Future(throw new NoSuchElementException)
          case "withSchemeTypeSchemeRef" => {
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map((CacheUtil.SCHEME_CACHE -> Json.toJson("1")),
              (CacheUtil.PORTAL_SCHEME_REF -> Json.toJson("CSOP - MyScheme - XA1100000000000 - 2014/15")))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
          case "withAll" => {
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map(
              CacheUtil.SCHEME_ORGANISER_CACHE -> Json.toJson(schemeOrganiser),
              CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER -> Json.toJson(groupSchemeInfo),
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(gscomps),
              CacheUtil.TRUSTEES_CACHE -> Json.toJson(trusteesList),
              CacheUtil.reportableEvents -> Json.toJson(reportableEvents),
              CacheUtil.FILE_TYPE_CACHE -> Json.toJson(fileTypeCSV),
              CacheUtil.CHECK_CSV_FILES -> Json.toJson(csvFilesCallbackList),
              CacheUtil.ersMetaData -> Json.toJson(rsc))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
          case "noGroupSchemeInfo" => {
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map(
              CacheUtil.SCHEME_ORGANISER_CACHE -> Json.toJson(schemeOrganiser),
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(gscomps),
              CacheUtil.TRUSTEES_CACHE -> Json.toJson(trusteesList),
              CacheUtil.reportableEvents -> Json.toJson(reportableEvents),
              CacheUtil.FILE_TYPE_CACHE -> Json.toJson(fileTypeCSV),
              CacheUtil.CHECK_CSV_FILES -> Json.toJson(csvFilesCallbackList),
              CacheUtil.ersMetaData -> Json.toJson(rsc))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
          case "odsFile" => {
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map(
              CacheUtil.SCHEME_CACHE -> Json.toJson("1"),
              CacheUtil.PORTAL_SCHEME_REF -> Json.toJson("CSOP - MyScheme - XA1100000000000 - 2014/15"),
              CacheUtil.altAmendsActivity -> Json.toJson(new AltAmendsActivity("1")),
              CacheUtil.SCHEME_ORGANISER_CACHE -> Json.toJson(schemeOrganiser),
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(gscomps),
              CacheUtil.TRUSTEES_CACHE -> Json.toJson(trusteesList),
              CacheUtil.reportableEvents -> Json.toJson(reportableEvents),
              CacheUtil.FILE_TYPE_CACHE -> Json.toJson(fileTypeODS),
              CacheUtil.FILE_NAME_CACHE -> Json.toJson(fileNameODS),
              CacheUtil.ersMetaData -> Json.toJson(rsc))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
          case "withAllNillReturn" => {
            val reportableEvents: ReportableEvents = new ReportableEvents(Some(PageBuilder.OPTION_NIL_RETURN))
            val fileType: CheckFileType = new CheckFileType(None)
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map(
              CacheUtil.SCHEME_ORGANISER_CACHE -> Json.toJson(schemeOrganiser),
              CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER -> Json.toJson(groupSchemeInfo),
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(gscomps),
              CacheUtil.TRUSTEES_CACHE -> Json.toJson(trusteesList),
              CacheUtil.reportableEvents -> Json.toJson(reportableEvents),
              CacheUtil.FILE_TYPE_CACHE -> Json.toJson(fileType),
              CacheUtil.CHECK_CSV_FILES -> Json.toJson(csvFilesCallbackList),
              CacheUtil.ersMetaData -> Json.toJson(rsc))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
          case "withAllCSVFile" => {
            val reportableEvents: ReportableEvents = new ReportableEvents(Some(PageBuilder.OPTION_UPLOAD_SPREEDSHEET))
            val fileType: CheckFileType = new CheckFileType(Some(PageBuilder.OPTION_CSV))
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map(
              CacheUtil.SCHEME_ORGANISER_CACHE -> Json.toJson(schemeOrganiser),
              CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER -> Json.toJson(groupSchemeInfo),
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(gscomps),
              CacheUtil.TRUSTEES_CACHE -> Json.toJson(trusteesList),
              CacheUtil.reportableEvents -> Json.toJson(reportableEvents),
              CacheUtil.FILE_TYPE_CACHE -> Json.toJson(fileType),
              CacheUtil.CHECK_CSV_FILES -> Json.toJson(csvFilesCallbackList),
              CacheUtil.ersMetaData -> Json.toJson(rsc))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
          case "withAllODSFile" => {
            val reportableEvents: ReportableEvents = new ReportableEvents(Some(PageBuilder.OPTION_UPLOAD_SPREEDSHEET))
            val fileType: CheckFileType = new CheckFileType(Some(PageBuilder.OPTION_ODS))
            val data: scala.Predef.Map[scala.Predef.String, play.api.libs.json.JsValue] = Map(
              CacheUtil.SCHEME_ORGANISER_CACHE -> Json.toJson(schemeOrganiser),
              CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER -> Json.toJson(groupSchemeInfo),
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(gscomps),
              CacheUtil.TRUSTEES_CACHE -> Json.toJson(trusteesList),
              CacheUtil.reportableEvents -> Json.toJson(reportableEvents),
              CacheUtil.FILE_TYPE_CACHE -> Json.toJson(fileType),
              CacheUtil.CHECK_CSV_FILES -> Json.toJson(csvFilesCallbackList),
              CacheUtil.FILE_NAME_CACHE -> Json.toJson(fileNameODS),
              CacheUtil.ersMetaData -> Json.toJson(rsc))
            val cm: CacheMap = new CacheMap("id1", data)
            Future.successful(cm)
          }
        }
      }

      override def getAllData(bundleRef: String, ersMetaData: ErsMetaData)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[AnyRef]): Future[ErsSummary] = {
        Future.successful(new ErsSummary("testbundle", "false", None, DateTime.now, ersMetaData, None, None, None, None, None, None, None, None))
      }

      override def shortLivedCache: ShortLivedCache = ???
    }

  }

  "Calling SummaryDeclarationController.summaryDeclarationPage (GET) without authentication" should {
    "give a redirect status (to company authentication frontend)" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      val result = controllerUnderTest.summaryDeclarationPage().apply(FakeRequest("GET", ""))
      status(result) shouldBe Status.SEE_OTHER
    }
  }

  "Calling SummaryDeclarationController.showSummaryDeclarationPage (GET) with authentication missing elements in the cache" should {
    "direct to ers errors page" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      contentAsString(await(controllerUnderTest.showSummaryDeclarationPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc))) shouldBe contentAsString(controllerUnderTest.getGlobalErrorPage)
    }
  }

  "Calling SummaryDeclarationController.showSummaryDeclarationPage (GET) with authentication and required elements (Nil Return) in the cache" should {
    "show the scheme organiser page" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      controllerUnderTest.fetchAllMapVal = "withAllNillReturn"
      val result = controllerUnderTest.showSummaryDeclarationPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }
  }

  "Calling SummaryDeclarationController.showSummaryDeclarationPage (GET) with authentication and required elements (CSV File Upload) in the cache" should {
    "show the scheme organiser page" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      controllerUnderTest.fetchAllMapVal = "withAllCSVFile"
      val result = controllerUnderTest.showSummaryDeclarationPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }
  }

  "Calling SummaryDeclarationController.showSummaryDeclarationPage (GET) with authentication and required elements (ODS File Upload) in the cache" should {
    "show the scheme organiser page" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      controllerUnderTest.fetchAllMapVal = "withAllODSFile"
      val result = controllerUnderTest.showSummaryDeclarationPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }
  }

  "Calling SummaryDeclarationController.showSummaryDeclarationPage (GET) with authentication and required elements in the cache (ODS)" should {
    "show the scheme organiser page" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      controllerUnderTest.fetchAllMapVal = "odsFile"
      val result = controllerUnderTest.showSummaryDeclarationPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }
  }

  "Calling SummaryDeclarationController.showSummaryDeclarationPage (GET) with authentication and required elements (no group scheme info) in the cache" should {
    "show the scheme organiser page" in {
      val controllerUnderTest = buildFakeSummaryDeclarationController
      controllerUnderTest.fetchAllMapVal = "noGroupSchemeInfo"
      val result = controllerUnderTest.showSummaryDeclarationPage(ersRequestObject)(Fixtures.buildFakeUser, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe Status.OK
    }
  }

}

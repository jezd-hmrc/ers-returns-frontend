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
import models._
import org.jsoup.Jsoup
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import utils.{CacheUtil, ERSFakeApplicationConfig, Fixtures, PageBuilder}

import scala.concurrent.Future
import utils.Fixtures.fakeRequestToRequestWithSchemeInfo

class GroupSchemeControllerTest extends UnitSpec with MockitoSugar with ERSUsers with ERSFakeApplicationConfig with BeforeAndAfterEach with OneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val materializer: Materializer = app.materializer

  lazy val mockAuthConnector = mock[AuthConnector]

  lazy val companyDetailsList: CompanyDetailsList = CompanyDetailsList(
    List(
      CompanyDetails(Fixtures.companyName, "Adress Line 1", None, None, None, None, None, None, None),
      CompanyDetails(Fixtures.companyName, "Adress Line 1", None, None, None, None, None, None, None)
    )
  )

  lazy val mockCacheUtil = mock[CacheUtil]

  override def beforeEach() = {
    super.beforeEach()
    reset(mockCacheUtil)
  }

  lazy val testGroupSchemeController = new GroupSchemeController {
    override val cacheUtil: CacheUtil = mockCacheUtil
  }

  "manualCompanyDetailsPage" should {

    def manualCompanyDetailsPageHandler(index: Int, request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.manualCompanyDetailsPage(index).apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      manualCompanyDetailsPageHandler(0, Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showManualCompanyDetailsPage" should {
    "display company details page for correct scheme" in {
      val result = await(testGroupSchemeController.showManualCompanyDetailsPage(1)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET")))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("ers_manual_company_details.csop.title"))
    }
  }

  "manualCompanyDetailsSubmit" should {
    def manualCompanyDetailsSubmitHandler(index: Int, request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.manualCompanyDetailsSubmit(index).apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      manualCompanyDetailsSubmitHandler(0, Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showManualCompanyDetailsSubmit" should {

    def buildCompanyDetailsRequest(isValid: Boolean) = {
      val data = if (isValid) {
        Map("companyName" -> Fixtures.companyName, "addressLine1" -> "Add1", "addressLine2" -> "Add2", "addressLine3" -> "Add3", "addressLine4" -> "Add4", "postcode" -> "AA111AA", "country" -> "United Kingdom", "companyReg" -> "", "corporationRef" -> "")
      }
      else {
        Map("" -> "")
      }
      val form = _root_.models.RsFormMappings.companyDetailsForm.bind(data)
      Fixtures.buildFakeRequestWithSessionId("POST").withFormUrlEncodedBody(form.data.toSeq: _*)
    }

    "display error if showManualCompanyDetailsSubmit is called with authentication and form errors" in {
      val result = testGroupSchemeController.showManualCompanyDetailsSubmit(1000)(Fixtures.buildFakeAuthContext, buildCompanyDetailsRequest(isValid = false))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("validation.summary.heading")) shouldBe true
    }

    "redirect to Group Summary page if showManualCompanyDetailsSubmit is called with authentication and correct form data entered for 1st company" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(companyDetailsList)
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_COMPANIES), any[CompanyDetailsList](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val result = testGroupSchemeController.showManualCompanyDetailsSubmit(10000)(Fixtures.buildFakeAuthContext, buildCompanyDetailsRequest(isValid = true))
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/group-summary")
    }


    "redirect to Group Summary page if showManualCompanyDetailsSubmit is called with authentication and correct form data for additional company" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(companyDetailsList)
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_COMPANIES), any[CompanyDetailsList](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val result = testGroupSchemeController.showManualCompanyDetailsSubmit(1)(Fixtures.buildFakeAuthContext, buildCompanyDetailsRequest(isValid = true))
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/group-summary")
    }

    "redirect to Group Summary page if showManualCompanyDetailsSubmit is called with authentication and correct form data for updated company" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(companyDetailsList)
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_COMPANIES), any[CompanyDetailsList](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val result = testGroupSchemeController.showManualCompanyDetailsSubmit(0)(Fixtures.buildFakeAuthContext, buildCompanyDetailsRequest(isValid = true))
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/group-summary")
    }


    "redirect to Group Summary page if data is filled correctly and there is nothing in existing cache" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new NoSuchElementException("Nothing in cache"))
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_COMPANIES), any[CompanyDetailsList](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val result = testGroupSchemeController.showManualCompanyDetailsSubmit(1000)(Fixtures.buildFakeAuthContext, buildCompanyDetailsRequest(isValid = true))
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/group-summary")
    }
  }

  "deleteCompany" should {
    def deleteCompanyHandler(index: Int, request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.deleteCompany(index).apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      deleteCompanyHandler(0, Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showDeleteCompany" should {
    "direct to ers errors page if showDeleteCompany is called with authentication and missing cache" in {
      when(
        mockCacheUtil.fetchAll(anyString())(any(), any())
      ).thenReturn(
        Future.failed(new NoSuchElementException("Nothing in cache"))
      )
      val result = await(testGroupSchemeController.showDeleteCompany(0)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("ers.global_errors.title")) shouldBe true
    }

    "give a redirect to groupPlanSummaryPage with the selected company deleted if showDeleteCompany is called with authentication and correct cache" in {
      when(
        mockCacheUtil.fetchAll(anyString())(any(), any())
      ).thenReturn(
        Future.successful(
          CacheMap(
            "id1",
            Map(
              CacheUtil.GROUP_SCHEME_COMPANIES -> Json.toJson(companyDetailsList)
            )
          )
        )
      )
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_COMPANIES), any[CompanyDetailsList](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )

      val result = await(testGroupSchemeController.showDeleteCompany(0)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/group-summary")
    }
  }

  "editCompany" should {
    def editCompanyHandler(index: Int, request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.editCompany(index).apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      editCompanyHandler(0, Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showEditCompany" should {
    "display error page if showEditCompany is called with authentication and missing cache" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new NoSuchElementException("Nothing in cache"))
      )
      val result = await(testGroupSchemeController.showEditCompany(0)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("ers.global_errors.title")) shouldBe true
    }

    "display manualCompanyDetailsPage with the selected company details if showEditCompany is called with authentication and correct cache" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(companyDetailsList)
      )
      val request = Fixtures.buildFakeRequestWithSessionIdCSOP("GET")
      val result = await(testGroupSchemeController.showEditCompany(0)(Fixtures.buildFakeAuthContext, request, hc))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("ers_manual_company_details.csop.title"))
    }

  }

  "groupSchemePage" should {
    def groupSchemePageHandler(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.groupSchemePage().apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      groupSchemePageHandler(Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showGroupSchemePage" should {
    "display group scheme page if there is no data in cache" in {
      when(
        mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new NoSuchElementException("Nothing in cache"))
      )
      val request = Fixtures.buildFakeRequestWithSessionId("GET")
      val result = await(testGroupSchemeController.showGroupSchemePage()(Fixtures.buildFakeAuthContext,request , hc))
      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=yes]").hasAttr("checked") shouldEqual false
      document.select("input[id=no]").hasAttr("checked") shouldEqual false
    }
    "display group scheme page if there is cached data" in {
      when(
        mockCacheUtil.fetch[GroupSchemeInfo](refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(
          GroupSchemeInfo(Option(PageBuilder.OPTION_YES), None)
        )
      )
      val result = await(testGroupSchemeController.showGroupSchemePage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      document.select("input[id=yes]").hasAttr("checked") shouldEqual true
      document.select("input[id=no]").hasAttr("checked") shouldEqual false
    }
  }

  "groupSchemeSelected" should {
    def groupSchemeSelectedHandler(scheme: String, request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.groupSchemeSelected(scheme).apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      groupSchemeSelectedHandler("", Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showGroupSchemeSelected" should {
    def buildGroupSchemeSelectedRequest(result: Option[Boolean] = None, scheme: String) = {
      val data = result match {
        case None => Map("" -> "")
        case Some(true) => Map("groupScheme" -> PageBuilder.OPTION_YES)
        case Some(false) => Map("groupScheme" -> PageBuilder.OPTION_NO)
      }
      val form = _root_.models.RsFormMappings.groupForm.bind(data)
      val request = scheme match {
        case "CSOP" => Fixtures.buildFakeRequestWithSessionIdCSOP("POST")
        case "SAYE" => Fixtures.buildFakeRequestWithSessionIdSAYE("POST")
        case "EMI" => Fixtures.buildFakeRequestWithSessionIdEMI("POST")
        case "SIP" => Fixtures.buildFakeRequestWithSessionIdSIP("POST")
        case "OTHER" => Fixtures.buildFakeRequestWithSessionIdOTHER("POST")
      }
      request.withFormUrlEncodedBody(form.data.toSeq: _*)
    }

    "display errors if invalid data is sent" in {
      val request = buildGroupSchemeSelectedRequest(None, "CSOP")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_CSOP)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("validation.summary.heading")) shouldBe true
    }

    "redirect to company details page if user select yes for CSOP" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(true), "CSOP")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_CSOP)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/add-company-details-manually")
    }

    "redirect to alterations page if user select no for CSOP" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(false), "CSOP")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_CSOP)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/alterations")
    }

    "redirect to company details page if user select yes for SAYE" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(true), "SAYE")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_SAYE)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/add-company-details-manually")
    }

    "redirect to alterations page if user select no for SAYE" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(false), "SAYE")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_SAYE)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/alterations")
    }

    "redirect to company details page if user select yes for EMI" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(true), "EMI")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_EMI)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/add-company-details-manually")
    }

    "redirect to summary page if user select no for EMI" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(false), "EMI")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_EMI)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/summary")
    }

    "redirect to company details page if user select yes for SIP" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(true), "SIP")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_SIP)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/add-company-details-manually")
    }

    "redirect to trustees page if user select no for SIP" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(false), "SIP")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_SIP)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/trustee-details")
    }

    "redirect to company details page if user select yes for OTHER" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(true), "OTHER")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_OTHER)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/add-company-details-manually")
    }

    "redirect to summary page if user select no for OTHER" in {
      when(
        mockCacheUtil.cache(refEq(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER), any[GroupSchemeInfo](), anyString())(any(), any(), any())
      ).thenReturn(
        mock[CacheMap]
      )
      val request = buildGroupSchemeSelectedRequest(Some(false), "OTHER")
      val result = testGroupSchemeController.showGroupSchemeSelected(PageBuilder.SCHEME_OTHER)(Fixtures.buildFakeAuthContext, request)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/summary")
    }

  }

  "groupPlanSummaryPage" should {
    def groupPlanSummaryPageHandler(request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.groupPlanSummaryPage().apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      groupPlanSummaryPageHandler(Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "showGroupPlanSummaryPage" should {
    "display error page if showGroupPlanSummaryPage is called with authentication and missing cache" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.failed(new NoSuchElementException("Nothing in cache"))
      )
      val result = await(testGroupSchemeController.showGroupPlanSummaryPage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("ers.global_errors.title")) shouldBe true
    }

    "display group plan summary if showGroupPlanSummaryPage is called with authentication and correct cache" in {
      when(
        mockCacheUtil.fetch[CompanyDetailsList](refEq(CacheUtil.GROUP_SCHEME_COMPANIES), anyString())(any(), any(), any())
      ).thenReturn(
        Future.successful(companyDetailsList)
      )
      val result = await(testGroupSchemeController.showGroupPlanSummaryPage()(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionIdCSOP("GET"), hc))
      status(result) shouldBe OK
      bodyOf(result).contains(Messages("ers_group_summary.csop.title"))
    }
  }

  "groupPlanSummaryContinue" should {
    def groupPlanSummaryContinueHandler(scheme: String, request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest())(handler: Future[Result] => Any): Unit = {
      handler(testGroupSchemeController.groupPlanSummaryContinue(scheme).apply(request))
    }

    "redirect to sign in page if user is not authenticated" in {
      groupPlanSummaryContinueHandler("", Fixtures.buildFakeRequestWithSessionId("GET")) { result =>
        status(result) shouldBe SEE_OTHER
        headers(result).get("Location").get.contains("/gg/sign-in") shouldBe true
      }
    }
  }

  "continueFromGroupPlanSummaryPage" should {
    "redirect to alterations page for CSOP" in {
      val result = testGroupSchemeController.continueFromGroupPlanSummaryPage(PageBuilder.SCHEME_CSOP)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/alterations")
    }

    "redirect to alterations page for SAYE" in {
      val result = testGroupSchemeController.continueFromGroupPlanSummaryPage(PageBuilder.SCHEME_SAYE)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/alterations")
    }

    "redirect to summary page for EMI" in {
      val result = testGroupSchemeController.continueFromGroupPlanSummaryPage(PageBuilder.SCHEME_EMI)(Fixtures.buildFakeAuthContext, Fixtures.buildFakeRequestWithSessionId("GET"), hc)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/summary")
    }

    "redirect to trustee page for SIP" in {
      val request = Fixtures.buildFakeRequestWithSessionId("GET")
      val result = testGroupSchemeController.continueFromGroupPlanSummaryPage(PageBuilder.SCHEME_SIP)(Fixtures.buildFakeAuthContext, request, hc)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/trustee-details")
    }

    "redirect to summary page for OTHER" in {
      val request = Fixtures.buildFakeRequestWithSessionId("GET")
      val result = testGroupSchemeController.continueFromGroupPlanSummaryPage(PageBuilder.SCHEME_OTHER)(Fixtures.buildFakeAuthContext,request , hc)
      status(result) shouldBe SEE_OTHER
      headers(result).get("Location").get.contains("/summary")
    }
  }
}

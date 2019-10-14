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

import models._
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.PageBuilder._
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object GroupSchemeController extends GroupSchemeController {
  override val cacheUtil: CacheUtil = CacheUtil
}

trait GroupSchemeController extends ERSReturnBaseController with Authenticator with ErsConstants with LegacyI18nSupport {
  val cacheUtil: CacheUtil


  def manualCompanyDetailsPage(index: Int) = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showManualCompanyDetailsPage(index)(user, request)
  }

  def showManualCompanyDetailsPage(index: Int)(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    Future(Ok(views.html.manual_company_details(index, RsFormMappings.companyDetailsForm)))
  }

  def manualCompanyDetailsSubmit(index: Int) = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showManualCompanyDetailsSubmit(index)(user, request)
  }

  def showManualCompanyDetailsSubmit(index: Int)(implicit authContext: AuthContext, request: Request[AnyRef]): Future[Result] = {
    RsFormMappings.companyDetailsForm.bindFromRequest.fold(
      errors => {
        Future(Ok(views.html.manual_company_details(index, errors)))
      },
      successful => {
        val scRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
        cacheUtil.fetch[CompanyDetailsList](CacheUtil.GROUP_SCHEME_COMPANIES, scRef).flatMap { cachedCompaniesList =>

          var cachedCompaniesListPlusNewCompany = CompanyDetailsList(List[CompanyDetails]())
          var companyDetails: CompanyDetails = CompanyDetails(PageBuilder.DEFAULT, PageBuilder.DEFAULT, Option(PageBuilder.DEFAULT), Option(PageBuilder.DEFAULT), Option(PageBuilder.DEFAULT), Option(PageBuilder.DEFAULT), Option(PageBuilder.DEFAULT), Option(PageBuilder.DEFAULT), Option(PageBuilder.DEFAULT))
          if (index == 10000) {
            cachedCompaniesListPlusNewCompany = CompanyDetailsList(cachedCompaniesList.companies :+ successful)
          } else {
            for ((company, id) <- cachedCompaniesList.companies.zipWithIndex) {
              if (index == id) {
                // Update company details
                companyDetails = CompanyDetails(
                  (successful.companyName), //Company Name
                  (successful.addressLine1), //Address line 1
                  (successful.addressLine2), //Address line 2
                  (successful.addressLine3), //Address line 3
                  (successful.addressLine4), //Address line 4
                  (successful.country), //Country
                  (successful.postcode), //Postcode
                  (successful.companyReg), //Company Reg
                  (successful.corporationRef) //Corporation Ref
                )

              } else {
                companyDetails = CompanyDetails(
                  (company.companyName), //Company Name
                  (company.addressLine1), //Address line 1
                  (company.addressLine2), //Address line 2
                  (company.addressLine3), //Address line 3
                  (company.addressLine4), //Address line 4
                  (company.country), //Country
                  (company.postcode), //Postcode
                  (company.companyReg), //Company Reg
                  (company.corporationRef) //Corporation Ref
                )
              }
              cachedCompaniesListPlusNewCompany = CompanyDetailsList(cachedCompaniesListPlusNewCompany.companies :+ companyDetails)
            }
          }
          cacheUtil.cache(CacheUtil.GROUP_SCHEME_COMPANIES, cachedCompaniesListPlusNewCompany, scRef).map { all =>
            Redirect(routes.GroupSchemeController.groupPlanSummaryPage)
          }
        } recoverWith {
          case e: NoSuchElementException => {
            val companiesList = CompanyDetailsList(List(successful))
            cacheUtil.cache(CacheUtil.GROUP_SCHEME_COMPANIES, companiesList, scRef).map { all =>
              Redirect(routes.GroupSchemeController.groupPlanSummaryPage)
            }
          }
        }
      }
    )
  }

  def deleteCompany(id: Int) = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showDeleteCompany(id)(user, request, hc)
  }

  def showDeleteCompany(id: Int)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val scRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
    cacheUtil.fetchAll(scRef).flatMap { all =>
      val companies: CompanyDetailsList = (all.getEntry[CompanyDetailsList](CacheUtil.GROUP_SCHEME_COMPANIES).get)
      var companyDetailsList = List[CompanyDetails]()
      for ((company, index) <- companies.companies.zipWithIndex) {
        if (index != id) {
          val companyDetiails = CompanyDetails(
            (company.companyName), //Company Name
            (company.addressLine1), //Address line 1
            (company.addressLine2), //Address line 2
            (company.addressLine3), //Address line 3
            (company.addressLine4), //Address line 4
            (company.country), //Country
            (company.postcode), //Postcode
            (company.companyReg), //Company Reg
            (company.corporationRef) //Corporation Ref
          )
          companyDetailsList = companyDetailsList :+ companyDetiails
        }
      }
      cacheUtil.cache(CacheUtil.GROUP_SCHEME_COMPANIES, CompanyDetailsList(companyDetailsList), scRef).map { all =>
        Redirect(routes.GroupSchemeController.groupPlanSummaryPage)
      }
    } recover {
      case e: NoSuchElementException => {
        Logger.error(s"Fetch all data failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def editCompany(id: Int) = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showEditCompany(id)(user, request, hc)
  }

  def showEditCompany(id: Int)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val schemeRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
    cacheUtil.fetch[CompanyDetailsList](CacheUtil.GROUP_SCHEME_COMPANIES, schemeRef).map { companies =>
      var companyDetails: CompanyDetails = CompanyDetails(PageBuilder.DEFAULT, PageBuilder.DEFAULT, Some(PageBuilder.DEFAULT), Some(PageBuilder.DEFAULT), Some(PageBuilder.DEFAULT), Some(PageBuilder.DEFAULT), Some(PageBuilder.DEFAULT), Some(PageBuilder.DEFAULT), Some(PageBuilder.DEFAULT))
      for ((company, index) <- companies.companies.zipWithIndex) {
        if (index == id) {
          companyDetails = CompanyDetails(
            (company.companyName), //Company Name
            (company.addressLine1), //Address line 1
            (company.addressLine2), //Address line 2
            (company.addressLine3), //Address line 3
            (company.addressLine4), //Address line 4
            (company.country), //Country
            (company.postcode), //Postcode
            (company.companyReg), //Company Reg
            (company.corporationRef) //Corporation Ref
          )
        }
      }
      Ok(views.html.manual_company_details(id, RsFormMappings.companyDetailsForm.fill(companyDetails)))
    } recover {
      case e: NoSuchElementException => {
        Logger.error(s"Fetch group scheme companies for edit failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def groupSchemePage() = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
          showGroupSchemePage(requestObject)(user, request, hc)
        }
  }

  def showGroupSchemePage(requestObject: RequestObject)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val schemeRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
    cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, schemeRef).map { groupSchemeInfo =>
      Ok(views.html.group(requestObject, groupSchemeInfo.groupScheme, RsFormMappings.groupForm.fill(RS_groupScheme(groupSchemeInfo.groupScheme))))
    } recover {
      case e: NoSuchElementException => {
        val form = RS_groupScheme(Some(""))
        Ok(views.html.group(requestObject, Some(PageBuilder.DEFAULT), RsFormMappings.groupForm.fill(form)))
      }
    }
  }

  def groupSchemeSelected(scheme: String) = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
          showGroupSchemeSelected(requestObject, scheme)(user, request)
        }
  }

  def showGroupSchemeSelected(requestObject: RequestObject, scheme: String)(implicit authContext: AuthContext, request: Request[AnyRef]): Future[Result] = {
    Logger.info(request.session.get(screenSchemeInfo).get.split(" - ").head)
    RsFormMappings.groupForm.bindFromRequest.fold(
      errors => {
        val correctOrder = errors.errors.map(_.key).distinct
        val incorrectOrderGrouped = errors.errors.groupBy(_.key).map(_._2.head).toSeq
        val correctOrderGrouped = correctOrder.flatMap(x => incorrectOrderGrouped.find(_.key == x))
        val firstErrors: Form[models.RS_groupScheme] = new Form[RS_groupScheme](errors.mapping, errors.data, correctOrderGrouped, errors.value)
        Future.successful(Ok(views.html.group(requestObject, Some(""), firstErrors)))
      },
      formData => {
        val gsc: GroupSchemeInfo = GroupSchemeInfo(Some(formData.groupScheme.get),
          if (formData.groupScheme.get == PageBuilder.OPTION_YES) Some(PageBuilder.OPTION_MANUAL)
          else None)
        val schRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
        cacheUtil.cache(CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, gsc, schRef).map { all =>
          request.session.get(screenSchemeInfo).get.split(" - ").head match {
            case PageBuilder.SCHEME_CSOP => {
              formData.groupScheme.get match {
                case PageBuilder.OPTION_NO => Redirect(routes.AltAmendsController.altActivityPage())
                case PageBuilder.OPTION_YES => Redirect(routes.GroupSchemeController.manualCompanyDetailsPage())
              }
            }
            case PageBuilder.SCHEME_SAYE => {
              formData.groupScheme.get match {
                case PageBuilder.OPTION_NO => Redirect(routes.AltAmendsController.altActivityPage())
                case PageBuilder.OPTION_YES => Redirect(routes.GroupSchemeController.manualCompanyDetailsPage())
              }
            }
            case PageBuilder.SCHEME_EMI => {
              formData.groupScheme.get match {
                case PageBuilder.OPTION_NO => Redirect(routes.SummaryDeclarationController.summaryDeclarationPage())
                case PageBuilder.OPTION_YES => Redirect(routes.GroupSchemeController.manualCompanyDetailsPage())
              }
            }
            case PageBuilder.SCHEME_SIP => {
              formData.groupScheme.get match {
                case PageBuilder.OPTION_NO => Redirect(routes.TrusteeController.trusteeDetailsPage())
                case PageBuilder.OPTION_YES => Redirect(routes.GroupSchemeController.manualCompanyDetailsPage())
              }
            }
            case PageBuilder.SCHEME_OTHER => {
              formData.groupScheme.get match {
                case PageBuilder.OPTION_NO => Redirect(routes.SummaryDeclarationController.summaryDeclarationPage())
                case PageBuilder.OPTION_YES => Redirect(routes.GroupSchemeController.manualCompanyDetailsPage())
              }
            }
          }
        }

      }
    )
  }


  def groupPlanSummaryPage() = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showGroupPlanSummaryPage()(user, request, hc)
  }

  def showGroupPlanSummaryPage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val schemeRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
    cacheUtil.fetch[CompanyDetailsList](CacheUtil.GROUP_SCHEME_COMPANIES, schemeRef).map { compDetails =>
      Ok(views.html.group_plan_summary(OPTION_MANUAL, compDetails))
    } recover {
      case e: NoSuchElementException => {
        Logger.error(s"Fetch group scheme companies before call to group plan summary page failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def groupPlanSummaryContinue(scheme: String) = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        continueFromGroupPlanSummaryPage(scheme)(user, request, hc)
  }

  def continueFromGroupPlanSummaryPage(scheme: String)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    scheme match {
      case SCHEME_CSOP => {
        Future(Redirect(routes.AltAmendsController.altActivityPage()))
      }
      case SCHEME_SAYE => {
        Future(Redirect(routes.AltAmendsController.altActivityPage()))
      }
      case SCHEME_EMI => {
        Future(Redirect(routes.SummaryDeclarationController.summaryDeclarationPage()))
      }
      case SCHEME_SIP => {
        Future(Redirect(routes.TrusteeController.trusteeDetailsPage()))
      }
      case SCHEME_OTHER => {
        Future(Redirect(routes.SummaryDeclarationController.summaryDeclarationPage()))
      }
    }
  }

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

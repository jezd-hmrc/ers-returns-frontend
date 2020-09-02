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

import config.ApplicationConfig
import javax.inject.{Inject, Singleton}
import models._
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils._

import scala.concurrent.Future

@Singleton
class GroupSchemeController @Inject()(val messagesApi: MessagesApi,
																			val authConnector: DefaultAuthConnector,
																			implicit val countryCodes: CountryCodes,
																			implicit val ersUtil: ERSUtil,
																			implicit val appConfig: ApplicationConfig
																		 ) extends FrontendController with Authenticator with I18nSupport {

  def manualCompanyDetailsPage(index: Int): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        showManualCompanyDetailsPage(index)(user, request)
  }

  def showManualCompanyDetailsPage(index: Int)(implicit authContext: ERSAuthData, request: Request[AnyContent]): Future[Result] = {
    ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).map { requestObject =>
      Ok(views.html.manual_company_details(requestObject, index, RsFormMappings.companyDetailsForm))
    }
  }

  def manualCompanyDetailsSubmit(index: Int): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).flatMap { requestObject =>
          showManualCompanyDetailsSubmit(requestObject, index)(user, request)
        }
  }

  def showManualCompanyDetailsSubmit(requestObject: RequestObject, index: Int)(implicit authContext: ERSAuthData, request: Request[AnyRef]): Future[Result] = {
    RsFormMappings.companyDetailsForm.bindFromRequest.fold(
      errors => {
        Future(Ok(views.html.manual_company_details(requestObject, index, errors)))
      },
      successful => {
        ersUtil.fetch[CompanyDetailsList](ersUtil.GROUP_SCHEME_COMPANIES, requestObject.getSchemeReference).flatMap { cachedCompaniesList =>

          val processedFormData = CompanyDetailsList(replaceCompany(cachedCompaniesList.companies, index, successful))

          ersUtil.cache(ersUtil.GROUP_SCHEME_COMPANIES, processedFormData, requestObject.getSchemeReference).map { _ =>
            Redirect(routes.GroupSchemeController.groupPlanSummaryPage())
          }
        } recoverWith {
          case _: NoSuchElementException =>
						val companiesList = CompanyDetailsList(List(successful))
						ersUtil.cache(ersUtil.GROUP_SCHEME_COMPANIES, companiesList, requestObject.getSchemeReference).map { _ =>
							Redirect(routes.GroupSchemeController.groupPlanSummaryPage())
						}
				}
      }
    )
  }

  def replaceCompany(companies: List[CompanyDetails], index: Int, formData: CompanyDetails): List[CompanyDetails] =

    (if (index == 10000) {
      companies :+ formData
    } else {
      companies.zipWithIndex.map{
        case (a, b) => if (b == index) formData else a
      }
    }).distinct

  def deleteCompany(id: Int): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        showDeleteCompany(id)(user, request, hc)
  }

  def showDeleteCompany(id: Int)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject <- ersUtil.fetch[RequestObject](ersUtil.ersRequestObject)
      all           <- ersUtil.fetchAll(requestObject.getSchemeReference)

      companies =  all.getEntry[CompanyDetailsList](ersUtil.GROUP_SCHEME_COMPANIES).getOrElse(CompanyDetailsList(Nil))
      companyDetailsList = CompanyDetailsList(filterDeletedCompany(companies, id))

      _ <- ersUtil.cache(ersUtil.GROUP_SCHEME_COMPANIES, companyDetailsList, requestObject.getSchemeReference)
    } yield {

        Redirect(routes.GroupSchemeController.groupPlanSummaryPage())

    }) recover {
      case e: Exception =>
        Logger.error(s"Fetch all data failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  private def filterDeletedCompany(companyList: CompanyDetailsList, id: Int): List[CompanyDetails] =
    companyList.companies.zipWithIndex.filterNot(_._2 == id).map(_._1)

  def editCompany(id: Int): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
          showEditCompany(id)(user, request, hc)
  }

  def showEditCompany(id: Int)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject <- ersUtil.fetch[RequestObject](ersUtil.ersRequestObject)
      all           <- ersUtil.fetchAll(requestObject.getSchemeReference)

      companies =  all.getEntry[CompanyDetailsList](ersUtil.GROUP_SCHEME_COMPANIES).getOrElse(CompanyDetailsList(Nil))
      companyDetails = companies.companies(id)

    } yield {

      Ok(views.html.manual_company_details(requestObject, id, RsFormMappings.companyDetailsForm.fill(companyDetails)))

    }) recover {
      case e: Exception =>
        Logger.error(s"Fetch group scheme companies for edit failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def groupSchemePage(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).flatMap { requestObject =>
          showGroupSchemePage(requestObject)(user, request, hc)
        }
  }

  def showGroupSchemePage(requestObject: RequestObject)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    ersUtil.fetch[GroupSchemeInfo](ersUtil.GROUP_SCHEME_CACHE_CONTROLLER, requestObject.getSchemeReference).map { groupSchemeInfo =>
      Ok(views.html.group(requestObject, groupSchemeInfo.groupScheme, RsFormMappings.groupForm.fill(RS_groupScheme(groupSchemeInfo.groupScheme))))
    } recover {
      case e: Exception =>
        Logger.warn(s"Fetching GroupSchemeInfo from the cache failed: $e")
        val form = RS_groupScheme(Some(""))
        Ok(views.html.group(requestObject, Some(ersUtil.DEFAULT), RsFormMappings.groupForm.fill(form)))
    }
  }

  def groupSchemeSelected(scheme: String): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).flatMap { requestObject =>
          showGroupSchemeSelected(requestObject, scheme)(user, request)
        }
  }

  def showGroupSchemeSelected(requestObject: RequestObject, scheme: String)(implicit authContext: ERSAuthData, request: Request[AnyRef]): Future[Result] = {
    Logger.info(request.session.get("screenSchemeInfo").get.split(" - ").head)
    RsFormMappings.groupForm.bindFromRequest.fold(
      errors => {
        val correctOrder = errors.errors.map(_.key).distinct
        val incorrectOrderGrouped = errors.errors.groupBy(_.key).map(_._2.head).toSeq
        val correctOrderGrouped = correctOrder.flatMap(x => incorrectOrderGrouped.find(_.key == x))
        val firstErrors: Form[models.RS_groupScheme] = new Form[RS_groupScheme](errors.mapping, errors.data, correctOrderGrouped, errors.value)
        Future.successful(Ok(views.html.group(requestObject, Some(""), firstErrors)))
      },
      formData => {
        val gsc: GroupSchemeInfo =
          GroupSchemeInfo(
            Some(formData.groupScheme.getOrElse("")),
          if (formData.groupScheme.contains(ersUtil.OPTION_YES)) Some(ersUtil.OPTION_MANUAL) else None
          )

        ersUtil.cache(ersUtil.GROUP_SCHEME_CACHE_CONTROLLER, gsc, requestObject.getSchemeReference).map { _ =>

          (requestObject.getSchemeId, formData.groupScheme) match {

            case (_, Some(ersUtil.OPTION_YES)) => Redirect(routes.GroupSchemeController.manualCompanyDetailsPage())

            case (ersUtil.SCHEME_CSOP | ersUtil.SCHEME_SAYE, _) => Redirect(routes.AltAmendsController.altActivityPage())

            case (ersUtil.SCHEME_EMI | ersUtil.SCHEME_OTHER, _) => Redirect(routes.SummaryDeclarationController.summaryDeclarationPage())

            case (ersUtil.SCHEME_SIP, _) => Redirect(routes.TrusteeController.trusteeDetailsPage())

            case (_,_) => getGlobalErrorPage
          }
        }
      }
    )
  }


  def groupPlanSummaryPage(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
          showGroupPlanSummaryPage()(user, request, hc)
  }

  def showGroupPlanSummaryPage()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject <- ersUtil.fetch[RequestObject](ersUtil.ersRequestObject)
      compDetails   <- ersUtil.fetch[CompanyDetailsList](ersUtil.GROUP_SCHEME_COMPANIES, requestObject.getSchemeReference)
    } yield {
      Ok(views.html.group_plan_summary(requestObject, ersUtil.OPTION_MANUAL, compDetails))
    }) recover {
      case e: Exception =>
        Logger.error(s"Fetch group scheme companies before call to group plan summary page failed with exception ${e.getMessage}, " +
					s"timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def groupPlanSummaryContinue(scheme: String): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        continueFromGroupPlanSummaryPage(scheme)(user, request, hc)
  }

  def continueFromGroupPlanSummaryPage(scheme: String)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    scheme match {
      case ersUtil.SCHEME_CSOP | ersUtil.SCHEME_SAYE =>
        Future(Redirect(routes.AltAmendsController.altActivityPage()))

      case ersUtil.SCHEME_EMI | ersUtil.SCHEME_OTHER =>
        Future(Redirect(routes.SummaryDeclarationController.summaryDeclarationPage()))

      case ersUtil.SCHEME_SIP =>
        Future(Redirect(routes.TrusteeController.trusteeDetailsPage()))

    }
  }

	def getGlobalErrorPage(implicit request: Request[_], messages: Messages): Result = {
		Ok(views.html.global_error(
			"ers.global_errors.title",
			"ers.global_errors.heading",
			"ers.global_errors.message"
		)(request, messages, appConfig))
	}
}

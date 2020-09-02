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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils._

import scala.concurrent.Future

@Singleton
class SchemeOrganiserController @Inject()(val messagesApi: MessagesApi,
																					val authConnector: DefaultAuthConnector,
																					implicit val countryCodes: CountryCodes,
																					implicit val ersUtil: ERSUtil,
																					implicit val appConfig: ApplicationConfig
																				 ) extends FrontendController with Authenticator with I18nSupport {

  def schemeOrganiserPage(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).flatMap { requestObject =>
          showSchemeOrganiserPage(requestObject)(user, request, hc)
        }
  }

  def showSchemeOrganiserPage(requestObject: RequestObject)
														 (implicit authContext: ERSAuthData, req: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {
    Logger.info(s"SchemeOrganiserController: showSchemeOrganiserPage:  schemeRef: ${requestObject.getSchemeReference}.")
		lazy val form = SchemeOrganiserDetails("", "", Some(""), Some(""), Some(""), Some(ersUtil.DEFAULT_COUNTRY), Some(""), Some(""), Some(""))

		ersUtil.fetch[ReportableEvents](ersUtil.reportableEvents, requestObject.getSchemeReference).flatMap { reportableEvent =>
      ersUtil.fetchOption[CheckFileType](ersUtil.FILE_TYPE_CACHE, requestObject.getSchemeReference).flatMap { fileType =>
        ersUtil.fetch[SchemeOrganiserDetails](ersUtil.SCHEME_ORGANISER_CACHE, requestObject.getSchemeReference).map { res =>
          val FileType = if (fileType.isDefined) {
            fileType.get.checkFileType.get
          } else {
            ""
          }
          Ok(views.html.scheme_organiser(requestObject, FileType, RsFormMappings.schemeOrganiserForm.fill(res), reportableEvent.isNilReturn.get))
        } recover {
          case _: NoSuchElementException =>
            Ok(views.html.scheme_organiser(
							requestObject,
							fileType.get.checkFileType.get,
							RsFormMappings.schemeOrganiserForm.fill(form),
							reportableEvent.isNilReturn.get
						))
        }
      } recover {
        case _: NoSuchElementException =>
          Ok(views.html.scheme_organiser(requestObject, "", RsFormMappings.schemeOrganiserForm.fill(form), reportableEvent.isNilReturn.get))
      }
    } recover {
      case e: Exception =>
				Logger.error(s"Get reportableEvent.isNilReturn failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
				getGlobalErrorPage
		}
  }

  def schemeOrganiserSubmit(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).flatMap { requestObject =>
          showSchemeOrganiserSubmit(requestObject)(user, request, hc)
        }
  }

  def showSchemeOrganiserSubmit(requestObject: RequestObject)
															 (implicit authContext: ERSAuthData, req: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.schemeOrganiserForm.bindFromRequest.fold(
      errors => {
        val correctOrder = errors.errors.map(_.key).distinct
        val incorrectOrderGrouped = errors.errors.groupBy(_.key).map(_._2.head).toSeq
        val correctOrderGrouped = correctOrder.flatMap(x => incorrectOrderGrouped.find(_.key == x))
        val firstErrors: Form[models.SchemeOrganiserDetails] = new Form[SchemeOrganiserDetails](errors.mapping, errors.data, correctOrderGrouped, errors.value)
        Future.successful(Ok(views.html.scheme_organiser(requestObject, "", firstErrors)))
      },
      successful => {

        Logger.warn(s"SchemeOrganiserController: showSchemeOrganiserSubmit:  schemeRef: ${requestObject.getSchemeReference}.")

        ersUtil.cache(ersUtil.SCHEME_ORGANISER_CACHE, successful, requestObject.getSchemeReference).map {
          _ => Redirect(routes.GroupSchemeController.groupSchemePage())
        } recover {
          case e: Exception =>
            Logger.error(s"Save scheme organiser details failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }
      }
    )
  }

	def getGlobalErrorPage(implicit request: Request[_], messages: Messages): Result = {
		Ok(views.html.global_error(
			"ers.global_errors.title",
			"ers.global_errors.heading",
			"ers.global_errors.message"
		)(request, messages, appConfig))
	}

}

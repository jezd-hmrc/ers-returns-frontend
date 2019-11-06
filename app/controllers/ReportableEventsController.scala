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

import _root_.models.{RsFormMappings, _}
import connectors.ErsConnector
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object ReportableEventsController extends ReportableEventsController {
  override val cacheUtil: CacheUtil = CacheUtil
  override val ersConnector: ErsConnector = ErsConnector
}

trait ReportableEventsController extends ERSReturnBaseController with Authenticator {
  val ersConnector: ErsConnector
  val cacheUtil: CacheUtil

  def reportableEventsPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObj =>

          updateErsMetaData(requestObj)(user, request, hc)
          showReportableEventsPage(requestObj)(user, request, hc)
        }
  }

  def updateErsMetaData(requestObject: RequestObject)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Object] = {
    ersConnector.connectToEtmpSapRequest(requestObject.getSchemeReference).flatMap { sapNumber =>
      cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, requestObject.getSchemeReference).map { metaData =>
        val ersMetaData = ErsMetaData(
          metaData.schemeInfo, metaData.ipRef, metaData.aoRef, metaData.empRef, metaData.agentRef, Some(sapNumber))
        cacheUtil.cache(CacheUtil.ersMetaData, ersMetaData, requestObject.getSchemeReference).recover {
          case e: Exception => {
            Logger.error(s"updateErsMetaData save failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
          }
        }
      } recover {
        case e: NoSuchElementException => {
          Logger.error(s"updateErsMetaData fetch failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
        }
      }
    }
  }

  def showReportableEventsPage(requestObject: RequestObject)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[ReportableEvents](CacheUtil.reportableEvents, requestObject.getSchemeReference).map { activity =>
      Ok(views.html.reportable_events(requestObject, activity.isNilReturn, RsFormMappings.chooseForm.fill(activity)))
    } recover {
      case e: NoSuchElementException =>
        val form = ReportableEvents(Some(""))
        Ok(views.html.reportable_events(requestObject, Some(""), RsFormMappings.chooseForm.fill(form)))
    }
  }

  def reportableEventsSelected(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObj =>
          showReportableEventsSelected(requestObj)(user, request) recover {
            case e: Exception =>
              Logger.error(s"reportableEventsSelected failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
              getGlobalErrorPage
          }
        }
  }

  def showReportableEventsSelected(requestObject: RequestObject)(implicit authContext: AuthContext, request: Request[AnyRef]): Future[Result] = {
    RsFormMappings.chooseForm.bindFromRequest.fold(
      errors => {
        Future.successful(Ok(views.html.reportable_events(requestObject, Some(""), errors)))
      },
      formData => {
        cacheUtil.cache(CacheUtil.reportableEvents, formData, requestObject.getSchemeReference).map { _ =>
          if (formData.isNilReturn.get == PageBuilder.OPTION_NIL_RETURN) {
            Redirect(routes.SchemeOrganiserController.schemeOrganiserPage())
          } else {
            Logger.info(s"Redirecting to FileUplaod controller to get Partial, timestamp: ${System.currentTimeMillis()}.")
            Redirect(routes.CheckFileTypeController.checkFileTypePage())
          }
        } recover {
          case e: Exception =>
            Logger.error(s"Save reportable event failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }

      }
    )
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))
}

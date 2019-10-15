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
import play.api.mvc.{AnyContent, LegacyI18nSupport, Request, Result}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object SchemeOrganiserController extends SchemeOrganiserController {
  override val cacheUtil: CacheUtil = CacheUtil
}

trait SchemeOrganiserController extends ERSReturnBaseController with Authenticator with LegacyI18nSupport {
  val cacheUtil: CacheUtil

  def schemeOrganiserPage() = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showSchemeOrganiserPage()(user, request, hc)
  }

  def showSchemeOrganiserPage()(implicit authContext: AuthContext, request: RequestWithSchemeInfo[AnyContent], hc: HeaderCarrier): Future[Result] = {
    val schemeRef = request.schemeInfo.schemeRef
    Logger.warn(s"SchemeOrganiserController: showSchemeOrganiserPage:  schemeRef: ${schemeRef}.")

    cacheUtil.fetch[ReportableEvents](CacheUtil.reportableEvents, schemeRef).flatMap { reportableEvent =>
      cacheUtil.fetchOption[CheckFileType](CacheUtil.FILE_TYPE_CACHE, schemeRef).flatMap { fileType =>
        cacheUtil.fetch[SchemeOrganiserDetails](CacheUtil.SCHEME_ORGANISER_CACHE, schemeRef).map { res =>
          val FileType = if (fileType.isDefined) {
            fileType.get.checkFileType.get
          } else {
            ""
          }
          Ok(views.html.scheme_organiser(FileType, RsFormMappings.schemeOrganiserForm.fill(res), reportableEvent.isNilReturn.get))
        } recover {
          case e: NoSuchElementException =>
            val form = SchemeOrganiserDetails("", "", Some(""), Some(""), Some(""), Some(PageBuilder.DEFAULT_COUNTRY), Some(""), Some(""), Some(""))
            Ok(views.html.scheme_organiser(fileType.get.checkFileType.get, RsFormMappings.schemeOrganiserForm.fill(form), reportableEvent.isNilReturn.get))
        }
      } recover {
        case e: NoSuchElementException =>
          val form = SchemeOrganiserDetails("", "", Some(""), Some(""), Some(""), Some(PageBuilder.DEFAULT_COUNTRY), Some(""), Some(""), Some(""))
          Ok(views.html.scheme_organiser("", RsFormMappings.schemeOrganiserForm.fill(form), reportableEvent.isNilReturn.get))
      }
    } recover {
      case e: Exception => {
        Logger.error(s"Get reportableEvent.isNilReturn failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def schemeOrganiserSubmit() = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showSchemeOrganiserSubmit()(user, request, hc)
  }

  def showSchemeOrganiserSubmit()(implicit authContext: AuthContext, request: RequestWithSchemeInfo[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.schemeOrganiserForm.bindFromRequest.fold(
      errors => {
        val correctOrder = errors.errors.map(_.key).distinct
        val incorrectOrderGrouped = errors.errors.groupBy(_.key).map(_._2.head).toSeq
        val correctOrderGrouped = correctOrder.flatMap(x => incorrectOrderGrouped.find(_.key == x))
        val firstErrors: Form[models.SchemeOrganiserDetails] = new Form[SchemeOrganiserDetails](errors.mapping, errors.data, correctOrderGrouped, errors.value)
        println(Console.RED + Console.BLINK + "PAN" + Console.RESET + correctOrderGrouped)
        Future.successful(Ok(views.html.scheme_organiser("", firstErrors)))
      },
      successful => {
        val schemeRef = request.schemeInfo.schemeRef
        Logger.warn(s"SchemeOrganiserController: showSchemeOrganiserSubmit:  schemeRef: $schemeRef.")

        cacheUtil.cache(CacheUtil.SCHEME_ORGANISER_CACHE, successful, schemeRef).map {
          res => Redirect(routes.GroupSchemeController.groupSchemePage)
        } recover {
          case e: Exception => {
            Logger.error(s"Save scheme organiser details failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
          }
        }
      }
    )
  }

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

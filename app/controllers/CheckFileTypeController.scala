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

import models.{RsFormMappings, _}
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{LegacyI18nSupport, _}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.{PageBuilder, _}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


object CheckFileTypeController extends CheckFileTypeController {
  override val cacheUtil: CacheUtil = CacheUtil
}

trait CheckFileTypeController extends ERSReturnBaseController with Authenticator with LegacyI18nSupport {

  val jsonParser = JsonParser
  val contentUtil = ContentUtil
  val cacheUtil: CacheUtil

  def checkFileTypePage(): Action[AnyContent] = AuthenticatedBy(ERSGovernmentGateway, pageVisibilityPredicate).async {
    implicit authContext =>
      implicit request =>
          showCheckFileTypePage()(authContext, request, hc)

  }

  def showCheckFileTypePage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      fileType      <- cacheUtil.fetch[CheckFileType](CacheUtil.FILE_TYPE_CACHE, requestObject.getSchemeReference).recover{
        case _: NoSuchElementException => CheckFileType(Some(""))
      }
    } yield {
      Ok(views.html.check_file_type(requestObject, fileType.checkFileType, RsFormMappings.checkFileTypeForm.fill(fileType)))
    }).recover{
      case e: Throwable =>
        Logger.error(s"Rendering AltAmends view failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def checkFileTypeSelected(): Action[AnyContent] = AuthenticatedBy(ERSGovernmentGateway, pageVisibilityPredicate).async {
    implicit authContext =>
      implicit request =>
          showCheckFileTypeSelected()(authContext, request, hc)
  }

  def showCheckFileTypeSelected()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
      RsFormMappings.checkFileTypeForm.bindFromRequest.fold(
        errors => {
          Future.successful(Ok(views.html.check_file_type(requestObject, Some(""), errors)))
        },
        formData => {
          cacheUtil.cache(CacheUtil.FILE_TYPE_CACHE, formData, requestObject.getSchemeReference).map { _ =>
            if (formData.checkFileType.contains(PageBuilder.OPTION_ODS)) {
              Redirect(routes.FileUploadController.uploadFilePage())
            } else {
              Redirect(routes.CheckCsvFilesController.checkCsvFilesPage())
            }
          }.recover {
            case e: Exception =>
              Logger.error("showCheckFileTypeSelected: Unable to save file type. Error: " + e.getMessage)
              getGlobalErrorPage
          }
        }
      )
    }
  }

  def getGlobalErrorPage(implicit messages: Messages): Result = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

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

import _root_.models._
import config.ERSFileValidatorAuthConnector
import connectors.{AttachmentsConnector, ErsConnector}
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import play.twirl.api.Html
import services.SessionService
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils._
import views.html.file_upload

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait FileUploadController extends FrontendController with Authenticator with LegacyI18nSupport {

  val attachmentsConnector: AttachmentsConnector
  val sessionService: SessionService
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector

  def uploadFilePage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
      cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap{ responseObject =>
    attachmentsConnector.getFileUploadPartial().map {
          Logger.info(s"uploadFilePage: Response received from Attachments for partial, timestamp: ${System.currentTimeMillis()}.")
          partial =>
              Ok(file_upload(responseObject, Html(partial.body)))
            }
        }
  }

  def success(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { responseObject =>
          showSuccess(responseObject)
        }
  }


  def showSuccess(requestObject: RequestObject)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val scRef = requestObject.getSchemeReference
    Logger.info("success: Attachments Success: " + (System.currentTimeMillis() / 1000))
    sessionService.retrieveCallbackData().flatMap { callbackData =>
      cacheUtil.cache[String](CacheUtil.FILE_NAME_CACHE, callbackData.get.name.get, scRef).map { cached =>
        Ok(views.html.success(requestObject, None, Some(callbackData.get.name.get)))
      } recover {
        case e: Exception => {
          Logger.error(s"success: failed to save ods filename with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
        }
      }
    }
  }


  def validationResults() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request => {
        val scRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
        cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, scRef).flatMap { all =>
          sessionService.retrieveCallbackData().flatMap { callbackData =>
            ersConnector.removePresubmissionData(all.schemeInfo).flatMap(result =>
              result.status match {
                case 200 => {
                  ersConnector.validateFileData(callbackData.get, all.schemeInfo).map { res =>
                    Logger.info(s"validationResults: Response from validator: ${res.status}, timestamp: ${System.currentTimeMillis()}.")
                    res.status match {
                      case 200 => {
                        Logger.warn(s"validationResults: Validation is successful for schemeRef: ${scRef}, callback: ${callbackData.get}, timestamp: ${System.currentTimeMillis()}.")
                        cacheUtil.cache(cacheUtil.VALIDATED_SHEEETS, res.body, scRef)
                        Redirect(routes.SchemeOrganiserController.schemeOrganiserPage())
                      }
                      case 202 => {
                        Logger.warn(s"validationResults: Validation is not successful for schemeRef: ${scRef}, callback: ${callbackData.get}, timestamp: ${System.currentTimeMillis()}.")
                        Redirect(routes.FileUploadController.validationFailure())
                      }
                      case _ => Logger.error(s"validationResults: Validate file data failed with Status ${res.status}, timestamp: ${System.currentTimeMillis()}.")
                        getGlobalErrorPage
                    }
                  }
                }
                case _ => Logger.error(s"validationResults: removePresubmissionData failed with status ${result.status}, timestamp: ${System.currentTimeMillis()}.")
                  Future(getGlobalErrorPage)
              }
            )
          }.recover {
            case e: Throwable => Logger.error(s"validationResults: validationResults failed with Exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
              getGlobalErrorPage
          }
        }
      }
  }

  def validationFailure() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        Logger.info("validationFailure: Validation Failure: " + (System.currentTimeMillis() / 1000))
        val scRef = cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
        cacheUtil.fetch[CheckFileType](CacheUtil.FILE_TYPE_CACHE, scRef).flatMap { fileType =>
          cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, scRef).flatMap { all =>
            cacheUtil.fetch[RequestObject](CacheUtil.ersRequestObject).map { requestObject =>
              val scheme: String = all.schemeInfo.schemeId
              val schemeName: String = all.schemeInfo.schemeName
              Ok(views.html.file_upload_errors(requestObject, scheme, schemeName, scRef, fileType.checkFileType.get))
            }
          }
        }
  }

  def failure() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        Logger.error("failure: Attachments Failure: " + (System.currentTimeMillis() / 1000))
        Future(getGlobalErrorPage)
  }

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

object FileUploadController extends FileUploadController {
  val attachmentsConnector = AttachmentsConnector
  val authConnector = ERSFileValidatorAuthConnector
  val sessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
}

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
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

trait FileUploadController extends FrontendController with Authenticator with LegacyI18nSupport {

  val attachmentsConnector: AttachmentsConnector
  val sessionService: SessionService
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector

  def uploadFilePage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>

        (for {
          responseObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
          partial        <- attachmentsConnector.getFileUploadPartial
        } yield {

          Logger.info(s"uploadFilePage: Response received from Attachments for partial, timestamp: ${System.currentTimeMillis()}.")

          Ok(file_upload(responseObject, Html(partial.body)))
        }).recover{
          case e: Throwable =>
          Logger.error(s"showUploadFilePage failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
        }
  }

  def success(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
          showSuccess()
  }


  def showSuccess()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    val futureRequestObject = cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
    val futureCallbackData = sessionService.retrieveCallbackData

    (for {
      requestObject <- futureRequestObject
      callbackData  <- futureCallbackData
      fileName      = callbackData.fold("")(_.name.getOrElse(""))
      scRef         = requestObject.getSchemeReference
      _             <- cacheUtil.cache[String](CacheUtil.FILE_NAME_CACHE, fileName, scRef)
    } yield {

      Logger.info("success: Attachments Success: " + (System.currentTimeMillis() / 1000))
      Ok(views.html.success(requestObject, None, Some(fileName)))

    }) recover {
      case e: Exception =>

        Logger.error(s"success: failed to save ods filename with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }


  def validationResults() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>

        val futureRequestObject = cacheUtil.fetch[RequestObject](CacheUtil.ersRequestObject)
        val futureCallbackData = sessionService.retrieveCallbackData()

        (for {
          requestObject      <- futureRequestObject
          all                <- cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, requestObject.getSchemeReference)
          callbackData       <- futureCallbackData
          connectorResponse  <- ersConnector.removePresubmissionData(all.schemeInfo)
          validationResponse <-

            if(connectorResponse.status == OK) {
              handleValidationResponse(connectorResponse, callbackData, all.schemeInfo, requestObject.getSchemeReference)
            }
            else {
              Logger.error(s"validationResults: removePresubmissionData failed with status ${connectorResponse.status}, timestamp: ${System.currentTimeMillis()}.")
              Future.successful(getGlobalErrorPage)
            }

        } yield {

          validationResponse

        }) recover {

          case e: Throwable =>
            Logger.error(s"validationResults: validationResults failed with Exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")

            getGlobalErrorPage

        }
  }

  def handleValidationResponse(response: HttpResponse, callbackData: Option[CallbackData], schemeInfo: SchemeInfo, schemeRef: String)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

      ersConnector.validateFileData(callbackData.get, schemeInfo).map { res =>
        Logger.info(s"validationResults: Response from validator: ${res.status}, timestamp: ${System.currentTimeMillis()}.")

        res.status match {

          case OK =>
            Logger.warn(s"validationResults: Validation is successful for schemeRef: $schemeRef, timestamp: ${System.currentTimeMillis()}.")
            cacheUtil.cache(cacheUtil.VALIDATED_SHEEETS, res.body, schemeRef)
            Redirect(routes.SchemeOrganiserController.schemeOrganiserPage())

          case ACCEPTED =>
            Logger.warn(s"validationResults: Validation is not successful for schemeRef: $schemeRef, timestamp: ${System.currentTimeMillis()}.")
            Redirect(routes.FileUploadController.validationFailure())

          case _ => Logger.error(s"validationResults: Validate file data failed with Status ${res.status}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }
      }

  }

  def validationFailure() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>

        Logger.info("validationFailure: Validation Failure: " + (System.currentTimeMillis() / 1000))

        (for {
          requestObject <- cacheUtil.fetch[RequestObject](CacheUtil.ersRequestObject)
          fileType      <- cacheUtil.fetch[CheckFileType](CacheUtil.FILE_TYPE_CACHE, requestObject.getSchemeReference)
        } yield {

          Ok(views.html.file_upload_errors(requestObject, fileType.checkFileType.getOrElse("")))
        }) recover {

          case e: Throwable =>
            Logger.error(s"validationResults: validationFailure failed with Exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")

            getGlobalErrorPage
        }
  }

  def failure() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        Logger.error("failure: Attachments Failure: " + (System.currentTimeMillis() / 1000))
        Future(getGlobalErrorPage)
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))

}

object FileUploadController extends FileUploadController {
  val attachmentsConnector = AttachmentsConnector
  val authConnector = ERSFileValidatorAuthConnector
  val sessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
}

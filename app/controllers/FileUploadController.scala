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
import akka.actor.ActorSystem
import config.{ApplicationConfig, ERSFileValidatorAuthConnector}
import connectors.ErsConnector
import models.upscan.{UploadStatus, UploadedSuccessfully}
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import services.{SessionService, UpscanService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils._
import views.html.upscan_ods_file_upload

import scala.concurrent.Future

trait FileUploadController extends FrontendController with Authenticator with LegacyI18nSupport {

  private val logger = Logger(this.getClass)

  val sessionService: SessionService
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector
  val upscanService: UpscanService = current.injector.instanceOf[UpscanService]
  val appConfig: ApplicationConfig = ApplicationConfig
  implicit val actorSystem: ActorSystem = current.actorSystem


  def uploadFilePage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit request =>
      implicit user =>
        (for {
          requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
          response <- upscanService.getUpscanFormDataOds()
          _ <- sessionService.createCallbackRecord
        } yield {
          Ok(upscan_ods_file_upload(requestObject, response))
        }) recover{
          case e: Throwable =>
            Logger.error(s"showUploadFilePage failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }
  }
//TODO fix error logging everywhere for exceptions and timestamps
  def success(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        val futureRequestObject: Future[RequestObject] = cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
        val futureCallbackData: Future[Option[UploadStatus]] = sessionService.getCallbackRecord

       (for {
          requestObject <- futureRequestObject
          file          <- futureCallbackData
        } yield {
          file match {
            case Some(file: UploadedSuccessfully) =>
              cacheUtil.cache[String](CacheUtil.FILE_NAME_CACHE, file.name, requestObject.getSchemeReference).map { _ =>
                Ok(views.html.upscan_ods_success(requestObject, file))
              }
            case Some(status: UploadStatus) =>
              //TODO can we only show page if cache is successful? For current behaviour yes.
              // We can only cache on success upload. What do we do with other status? What to do with None?? - Probs retry! None fail
              // This isnt tested either ATM
              //TODO depends on content. If design want file name here, then we put file name here and fail/retru if its not uploaded by this page
              //TODO if they dont then we can cache on validation results post maybe??
              Future.successful(Ok(views.html.upscan_ods_success(requestObject, status)))
          }
        }).flatMap(identity) recover {
         case e: Exception =>
           Logger.error(s"success: failed to save ods filename with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
           getGlobalErrorPage
       }
  }

  def validationResults(): Action[AnyContent] = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        val futureRequestObject = cacheUtil.fetch[RequestObject](CacheUtil.ersRequestObject)
        val futureCallbackData = sessionService.getCallbackRecord
        (for {
          requestObject <- futureRequestObject
          all <- cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, requestObject.getSchemeReference) //TODO different config below
          callbackData <- futureCallbackData.withRetry(appConfig.csvCacheCompletedRetryAmount)(_.exists(_.isInstanceOf[UploadedSuccessfully]))
          connectorResponse <- ersConnector.removePresubmissionData(all.schemeInfo)
          validationResponse <-
            if (connectorResponse.status == OK) {
              handleValidationResponse(callbackData.get.asInstanceOf[UploadedSuccessfully], all.schemeInfo)
            } else {
              logger.error(s"validationResults: removePresubmissionData failed with status ${connectorResponse.status}, timestamp: ${System.currentTimeMillis()}.")
              Future.successful(getGlobalErrorPage)
            }
        } yield {
          validationResponse
        }) recover {
          case e: LoopException[Option[UploadStatus]] =>
            logger.error(s"Failed to validate as file is not yet successfully uploaded. Current cache data: ${e.finalFutureData.flatten}", e)
            getGlobalErrorPage
          case e: Throwable =>
            Logger.error(s"validationResults: validationResults failed with Exception ${e.getMessage}", e)
            getGlobalErrorPage
        }
  }

  def handleValidationResponse(callbackData: UploadedSuccessfully, schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val schemeRef = schemeInfo.schemeRef
    ersConnector.validateFileData(callbackData, schemeInfo).map { res =>
      Logger.info(s"validationResults: Response from validator: ${res.status}, timestamp: ${System.currentTimeMillis()}.")

      res.status match {

        case OK =>
          Logger.warn(s"validationResults: Validation is successful for schemeRef: $schemeRef, timestamp: ${System.currentTimeMillis()}.")
          cacheUtil.cache(cacheUtil.VALIDATED_SHEEETS, res.body, schemeRef)
          Redirect(routes.SchemeOrganiserController.schemeOrganiserPage())

        case ACCEPTED =>
          Logger.warn(s"validationResults: Validation is not successful for schemeRef: $schemeRef, timestamp: ${System.currentTimeMillis()}.")
          Redirect(routes.FileUploadController.validationFailure())

        case _ => logger.error(s"validationResults: Validate file data failed with Status ${res.status}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
      }
    }
  }

  def validationFailure() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        logger.info("validationFailure: Validation Failure: " + (System.currentTimeMillis() / 1000))
        (for {
          requestObject <- cacheUtil.fetch[RequestObject](CacheUtil.ersRequestObject)
          fileType      <- cacheUtil.fetch[CheckFileType](CacheUtil.FILE_TYPE_CACHE, requestObject.getSchemeReference)
        } yield {
          Ok(views.html.file_upload_errors(requestObject, fileType.checkFileType.getOrElse("")))
        }) recover {
          case e: Throwable =>
            logger.error(s"validationResults: validationFailure failed with Exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
            getGlobalErrorPage
        }
  }

  def failure() = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence){
    implicit user =>
      implicit request =>
        val errorCode = request.getQueryString("errorCode").getOrElse("Unknown")
        val errorMessage = request.getQueryString("errorMessage").getOrElse("Unknown")
        val errorRequestId = request.getQueryString("errorRequestId").getOrElse("Unknown")
        logger.error(s"Upscan Failure. errorCode: $errorCode, errorMessage: $errorMessage, errorRequestId: $errorRequestId")
        getGlobalErrorPage
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))

}

object FileUploadController extends FileUploadController {
  val authConnector = ERSFileValidatorAuthConnector
  val sessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
}

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

import akka.actor.ActorSystem
import config.{ApplicationConfig, ERSFileValidatorAuthConnector}
import connectors.ErsConnector
import models._
import models.upscan._
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{SessionService, UpscanService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait CsvFileUploadController extends FrontendController with Authenticator {

  private val logger = Logger(this.getClass)

  val sessionService: SessionService
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector
  val appConfig: ApplicationConfig = ApplicationConfig
  val upscanService: UpscanService = current.injector.instanceOf[UpscanService]
  implicit val actorSystem: ActorSystem = current.actorSystem

  def uploadFilePage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        (for {
          requestObject   <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
          csvFilesList    <- cacheUtil.fetch[UpscanCsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, requestObject.getSchemeReference)
          currentCsvFile  = csvFilesList.files.find(a => a.uploadStatus == NotStarted)
          if currentCsvFile.isDefined
          upscanFormData  <- upscanService.getUpscanFormDataCsv(currentCsvFile.get.uploadId, requestObject.getSchemeReference)
        } yield {
          Ok(views.html.upscan_csv_file_upload(requestObject, upscanFormData, currentCsvFile.get, csvFilesList.files))
        }) recover {
          case _: NoSuchElementException =>
            logger.warn(s"Attempting to load upload page when no files are ready to upload")
            getGlobalErrorPage
          case e: Throwable =>
            logger.error(s"Failed to display csv upload page with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
            getGlobalErrorPage
        }
  }

  def success(uploadId: UploadId): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        logger.info(s"Upload form submitted for ID: $uploadId")
        (for {
          requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
          csvFileList   <- cacheUtil.fetch[UpscanCsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, requestObject.getSchemeReference)
          updatedCacheFileList = {
            logger.info(s"Updating uploadId: ${uploadId.value} to InProgress")
            csvFileList.updateToInProgress(uploadId)
          }
          _ <- cacheUtil.cache(CacheUtil.CHECK_CSV_FILES, updatedCacheFileList, requestObject.getSchemeReference)
        } yield {
          if(updatedCacheFileList.files.count(_.uploadStatus == NotStarted) == 0)
            Ok(views.html.upscan_csv_success(requestObject, csvFileList))
          else
            Redirect(routes.CsvFileUploadController.uploadFilePage())
        }) recover {
          case NonFatal(e) =>
            logger.error(s"success: failed to fetch callback data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
            getGlobalErrorPage
        }
  }

  def validationResults(): Action[AnyContent] = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        processValidationResults()
  }

  def processValidationResults()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      all           <- cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, requestObject.getSchemeReference)
      result        <- removePresubmissionData(all.schemeInfo)
    } yield {
      result
    }) recover {
      case e: Exception => {
        logger.error(s"Failed to fetch metadata data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def removePresubmissionData(schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    ersConnector.removePresubmissionData(schemeInfo).flatMap { result =>
      result.status match {
        case OK => extractCsvCallbackData(schemeInfo)
        case _ =>
          logger.error(s"validationResults: removePresubmissionData failed with status ${result.status}, timestamp: ${System.currentTimeMillis()}.")
          Future.successful(getGlobalErrorPage)
      }
    } recover {
      case e: Exception => {
        logger.error(s"Failed to remove presubmission data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def extractCsvCallbackData(schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[UpscanCsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, schemeInfo.schemeRef).withRetry(appConfig.csvCacheCompletedRetryAmount)(
      _.areAllFilesComplete()
    ).flatMap { csvFilesCallbackList =>
      if(csvFilesCallbackList.areAllFilesSuccessful()) {
        val callbackDataList: List[UploadedSuccessfully] = csvFilesCallbackList.files.map(_.uploadStatus.asInstanceOf[UploadedSuccessfully])
        validateCsv(callbackDataList, schemeInfo)
      } else {
        val failedFiles: String = csvFilesCallbackList.files.filter(_.uploadStatus == Failed).map(_.uploadId.value).mkString(", ")
        logger.error(s"Validation failed as one or more csv files failed to upload via Upscan. Failure IDs: $failedFiles")
        Future.successful(getGlobalErrorPage)
      }
    } recover {
      case e: LoopException[UpscanCsvFilesCallbackList] =>
        val incompleteFiles = e.finalFutureData.fold("Unknown")(_.files.filterNot(_.isComplete).map(_.uploadId.value).mkString(", "))
        logger.error(s"Failed to validate as not all csv files have completed upload to upscan. Incomplete IDs: $incompleteFiles")
        getGlobalErrorPage
      case e: Exception =>
        logger.error(s"Failed to fetch CsvFilesCallbackList with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
        getGlobalErrorPage
    }
  }


  def validateCsv(csvCallbackData: List[UploadedSuccessfully], schemeInfo: SchemeInfo)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    ersConnector.validateCsvFileData(csvCallbackData, schemeInfo).map { res =>
      res.status match {
        case OK =>
          logger.warn(s"validateCsv: Validation is successful for schemeRef: ${schemeInfo.schemeRef}, callback: ${csvCallbackData.toString}, timestamp: ${System.currentTimeMillis()}.")
          cacheUtil.cache(cacheUtil.VALIDATED_SHEEETS, res.body, schemeInfo.schemeRef)
          Redirect(routes.SchemeOrganiserController.schemeOrganiserPage())
        case ACCEPTED =>
          logger.warn(s"validateCsv: Validation is not successful for schemeRef: ${schemeInfo.schemeRef}, callback: ${csvCallbackData.toString}, timestamp: ${System.currentTimeMillis()}.")
          Redirect(routes.CsvFileUploadController.validationFailure())
        case _ => logger.error(s"validateCsv: Validate file data failed with Status ${res.status}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
      }
    } recover {
      case e: Exception =>
        logger.error(s"validateCsv: Failed to fetch CsvFilesCallbackList with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def validationFailure(): Action[AnyContent] = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
          processValidationFailure()(user, request, hc)
  }

  def processValidationFailure()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    logger.info("validationFailure: Validation Failure: " + (System.currentTimeMillis() / 1000))
    (for {
      requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      fileType      <- cacheUtil.fetch[CheckFileType](CacheUtil.FILE_TYPE_CACHE, requestObject.getSchemeReference)
    } yield {
      Ok(views.html.file_upload_errors(requestObject, fileType.checkFileType.get))
    }).recover {
      case e: Exception => {
        logger.error(s"processValidationFailure: failed to save callback data list with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def failure(): Action[AnyContent] = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence){
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

object CsvFileUploadController extends CsvFileUploadController {
  val authConnector = ERSFileValidatorAuthConnector
  val sessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
}

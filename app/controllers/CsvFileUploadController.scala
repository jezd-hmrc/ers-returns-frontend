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

import akka.actor.ActorSystem
import config.{ApplicationConfig, ERSFileValidatorAuthConnector}
import uk.gov.hmrc.auth.core.PlayAuthConnector
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
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait CsvFileUploadController extends FrontendController with Authenticator with Retryable {

  val logger = Logger(this.getClass)

  val sessionService: SessionService
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector
  val appConfig: ApplicationConfig
  val upscanService: UpscanService = current.injector.instanceOf[UpscanService]
  val allCsvFilesCacheRetryAmount: Int
  implicit val actorSystem: ActorSystem = current.actorSystem

  def uploadFilePage(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        (for {
          requestObject   <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
          csvFilesList    <- cacheUtil.fetch[UpscanCsvFilesList](CacheUtil.CSV_FILES_UPLOAD, requestObject.getSchemeReference)
          currentCsvFile  = csvFilesList.ids.find(ids => ids.uploadStatus == NotStarted)
          if currentCsvFile.isDefined
          upscanFormData  <- upscanService.getUpscanFormDataCsv(currentCsvFile.get.uploadId, requestObject.getSchemeReference)
        } yield {
          logger.error(s"REMOVEME UPLOADFILEPAGE CSV Showing CSV upload. DATA DUMP: requestObject $requestObject upscanInitiateResponse $upscanFormData csvFilesList $csvFilesList currentCsvFile $currentCsvFile")
          logger.error(s"REMOVEME UPLOADFILEPAGE CSV Focus on this: upscanInitiateResponse postTarget: ${upscanFormData.postTarget}")
          Ok(views.html.upscan_csv_file_upload(requestObject, upscanFormData, currentCsvFile.get.fileId))
        }) recover {
          case _: NoSuchElementException =>
            logger.warn(s"Attempting to load upload page when no files are ready to upload")
            getGlobalErrorPage
          case e: Throwable =>
            logger.error(s"Failed to display csv upload page with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
            getGlobalErrorPage
        }
  }

  def success(uploadId: UploadId): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        logger.info(s"Upload form submitted for ID: $uploadId")
        (for {
          requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
          csvFileList   <- cacheUtil.fetch[UpscanCsvFilesList](CacheUtil.CSV_FILES_UPLOAD, requestObject.getSchemeReference)
          updatedCacheFileList = {
            logger.info(s"Updating uploadId: ${uploadId.value} to InProgress")
            csvFileList.updateToInProgress(uploadId)
          }
          _ <- cacheUtil.cache(CacheUtil.CSV_FILES_UPLOAD, updatedCacheFileList, requestObject.getSchemeReference)
        } yield {
          if(updatedCacheFileList.noOfFilesToUpload == updatedCacheFileList.noOfUploads)
            Redirect(routes.CsvFileUploadController.validationResults())
          else
            Redirect(routes.CsvFileUploadController.uploadFilePage())
        }) recover {
          case NonFatal(e) =>
            logger.error(s"success: failed to fetch callback data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
            getGlobalErrorPage
        }
  }

  def validationResults(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        processValidationResults()
  }

  def processValidationResults()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      all           <- cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, requestObject.getSchemeReference)
      result        <- removePresubmissionData(all.schemeInfo)
    } yield {
      result
    }) recover {
      case e: Exception =>
        logger.error(s"Failed to fetch metadata data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def removePresubmissionData(schemeInfo: SchemeInfo)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
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

  def extractCsvCallbackData(schemeInfo: SchemeInfo)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[UpscanCsvFilesList](CacheUtil.CSV_FILES_UPLOAD, schemeInfo.schemeRef).flatMap {
      data =>
        logger.error(s"weGotAFetch \n\n\n $data")
        cacheUtil.fetchAll(schemeInfo.schemeRef).map {
          cacheMap =>
            logger.error(s"weAllGotAFetchAll \n\n\n $cacheMap")
            data.ids.foldLeft(Option(List.empty[UpscanCsvFilesCallback])) {
              case(Some(upscanCallbackList), UpscanIds(uploadId, fileId, uploadStatus)) =>
                logger.error(s"wowWeFolding, $upscanCallbackList, $uploadId, $fileId, $uploadStatus")
                cacheMap.getEntry[UploadStatus](s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}").map {
                  UpscanCsvFilesCallback(uploadId, fileId, _):: upscanCallbackList
                }
              case(_, _) => None
            }
        }.withRetry(allCsvFilesCacheRetryAmount)(_.isDefined).flatMap {
          files =>
            val csvFilesCallbackList: UpscanCsvFilesCallbackList = UpscanCsvFilesCallbackList(files.get)
            cacheUtil.cache(CacheUtil.CHECK_CSV_FILES, csvFilesCallbackList, schemeInfo.schemeRef).flatMap { _ =>
              if (csvFilesCallbackList.files.nonEmpty && csvFilesCallbackList.areAllFilesComplete()) {
                if(csvFilesCallbackList.areAllFilesSuccessful()) {
                  val callbackDataList: List[UploadedSuccessfully] =
                    csvFilesCallbackList.files.map(_.uploadStatus.asInstanceOf[UploadedSuccessfully])
                  validateCsv(callbackDataList, schemeInfo)
                } else {
                  val failedFiles: String = csvFilesCallbackList.files.filter(_.uploadStatus == Failed).map(_.uploadId.value).mkString(", ")
                  logger.error(s"Validation failed as one or more csv files failed to upload via Upscan. Failure IDs: $failedFiles")
                  Future.successful(getGlobalErrorPage)
                }
              } else {
                logger.error(s"Failed to validate as not all csv files have completed upload to upscan. Data: $csvFilesCallbackList")
                Future.successful(getGlobalErrorPage)
              }
            }
        } recover {
          case e: LoopException[_] =>
            logger.error(s"Could not fetch all files from cache map. UploadIds: ${data.ids.map(_.uploadId).mkString}")
            getGlobalErrorPage
        }
    } recover {
      case e: Exception =>
        logger.error(s"Failed to fetch CsvFilesCallbackList with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
        getGlobalErrorPage
    }
  }


  def validateCsv(csvCallbackData: List[UploadedSuccessfully], schemeInfo: SchemeInfo)(implicit authContext: ERSAuthData,
                                                                                       request: Request[AnyRef],
                                                                                       hc: HeaderCarrier): Future[Result] = {
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

  def validationFailure(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
          processValidationFailure()(user, request, hc)
  }

  def processValidationFailure()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
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

  def failure(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        val errorCode = request.getQueryString("errorCode").getOrElse("Unknown")
        val errorMessage = request.getQueryString("errorMessage").getOrElse("Unknown")
        val errorRequestId = request.getQueryString("errorRequestId").getOrElse("Unknown")
        logger.error(s"Upscan Failure. errorCode: $errorCode, errorMessage: $errorMessage, errorRequestId: $errorRequestId")
        Future.successful(getGlobalErrorPage)
  }

  def getGlobalErrorPage(implicit request: Request[AnyRef], messages: Messages): Result = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))

}

object CsvFileUploadController extends CsvFileUploadController {
  val authConnector: PlayAuthConnector = ERSFileValidatorAuthConnector
  val sessionService: SessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  val appConfig: ApplicationConfig = ApplicationConfig
  override val cacheUtil: CacheUtil = CacheUtil
  override val allCsvFilesCacheRetryAmount: Int = appConfig.allCsvFilesCacheRetryAmount
}

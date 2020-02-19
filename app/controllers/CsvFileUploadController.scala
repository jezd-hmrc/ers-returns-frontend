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
import org.joda.time.DateTime
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.Html
import services.SessionService
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait CsvFileUploadController extends FrontendController with Authenticator {

  val attachmentsConnector: AttachmentsConnector
  val sessionService: SessionService
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector

  def uploadFilePage(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
          showUploadFilePage()(user, request, hc)
  }

  def showUploadFilePage()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject        <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      csvFilesCallbackList <- cacheUtil.fetch[CsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, requestObject.getSchemeReference)
      partial              <- attachmentsConnector.getCsvFileUploadPartial
    } yield {
      Ok(views.html.csv_file_upload(requestObject, Html(partial.body), csvFilesCallbackList.files))
  }) recover {
      case e: Throwable =>
        Logger.error(s"showUploadFilePage failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def success(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        showSuccess()
  }

  def showSuccess()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    Logger.info("success: Attachments Success: " + (System.currentTimeMillis() / 1000))
    sessionService.retrieveCallbackData().flatMap { callbackData =>
      proceedCallbackData(callbackData)
    }
  }

  def proceedCallbackData(callbackData: Option[CallbackData])(implicit authContext: ERSAuthData,
																															request: Request[AnyRef],
																															hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject           <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      csvFilesCallbackList    <- cacheUtil.fetch[CsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, requestObject.getSchemeReference)
      newCsvFilesCallbackList <- Future.successful(updateCallbackData(requestObject, callbackData, csvFilesCallbackList.files))
      result                  <- modifyCachedCallbackData(requestObject, newCsvFilesCallbackList)
    } yield {

      result
    }) recover {
      case e: Exception =>
        Logger.error(s"success: failed to fetch callback data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def modifyCachedCallbackData(requestObject: RequestObject, newCsvFilesCallbackList: List[CsvFilesCallback])
															(implicit authContext: ERSAuthData,
															 request: Request[AnyRef],
															 hc: HeaderCarrier
															): Future[Result] = {

    cacheUtil.cache[CsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, CsvFilesCallbackList(newCsvFilesCallbackList), requestObject.getSchemeReference).map { _ =>
      if (newCsvFilesCallbackList.count(_.callbackData.isEmpty) > 0) {
        Redirect(routes.CsvFileUploadController.uploadFilePage())
      } else {
        Ok(views.html.success(requestObject, Some(CsvFilesCallbackList(newCsvFilesCallbackList)), None))
      }
    } recover {
      case e: Exception => {
        Logger.error(s"success: failed to save callback data list with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def updateCallbackData(requestObject: RequestObject, callbackData: Option[CallbackData], csvFilesCallbackList: List[CsvFilesCallback])
												(implicit authContext: ERSAuthData,
												 request: Request[AnyRef],
												 hc: HeaderCarrier
												): List[CsvFilesCallback] = {
    for (csvFileCallback <- csvFilesCallbackList) yield {
      val filename = Messages(PageBuilder.getPageElement(requestObject.getSchemeId, PageBuilder.PAGE_CHECK_CSV_FILE, csvFileCallback.fileId + ".file_name"))
      val callbackName = callbackData.map(x => x.name.getOrElse(""))

      if (callbackName.contains(filename)) CsvFilesCallback(csvFileCallback.fileId, callbackData)
      else csvFileCallback
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
				Logger.error(s"Failed to fetch metadata data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
				getGlobalErrorPage
		}
  }

  def removePresubmissionData(schemeInfo: SchemeInfo)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    ersConnector.removePresubmissionData(schemeInfo).flatMap { result =>
      result.status match {
        case OK => extractCsvCallbackData(schemeInfo)
        case _ =>
          Logger.error(s"validationResults: removePresubmissionData failed with status ${result.status}, timestamp: ${System.currentTimeMillis()}.")
          Future(getGlobalErrorPage)
      }
    } recover {
      case e: Exception => {
        Logger.error(s"Failed to remove presubmission data with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def extractCsvCallbackData(schemeInfo: SchemeInfo)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    cacheUtil.fetch[CsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES, schemeInfo.schemeRef).flatMap { csvFilesCallbackList =>
      val csvCallbackValidatorData: List[CallbackData] = for (csvCallback <- csvFilesCallbackList.files) yield {
        csvCallback.callbackData.get
      }
      validateCsv(csvCallbackValidatorData, schemeInfo)
    } recover {
      case e: Exception => {
        Logger.error(s"Failed to fetch CsvFilesCallbackList with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def validateCsv(csvCallbackValidatorData: List[CallbackData], schemeInfo: SchemeInfo)(implicit authContext: ERSAuthData,
																																												request: Request[AnyRef],
																																												hc: HeaderCarrier): Future[Result] = {
    ersConnector.validateCsvFileData(csvCallbackValidatorData, schemeInfo).map { res =>
      Logger.info(s"validateCsv: Response from validator: ${res.status}, timestamp: ${System.currentTimeMillis()}.")
      res.status match {
        case OK => {
          Logger.warn(s"validateCsv: Validation is successful for schemeRef: ${schemeInfo.schemeRef}, callback: ${csvCallbackValidatorData.toString}, timestamp: ${System.currentTimeMillis()}.")
          cacheUtil.cache(cacheUtil.VALIDATED_SHEEETS, res.body, schemeInfo.schemeRef)
          Redirect(routes.SchemeOrganiserController.schemeOrganiserPage())
        }
        case 202 => {
          Logger.warn(s"validateCsv: Validation is not successful for schemeRef: ${schemeInfo.schemeRef}, callback: ${csvCallbackValidatorData.toString}, timestamp: ${System.currentTimeMillis()}.")
          Redirect(routes.CsvFileUploadController.validationFailure())
        }
        case _ => Logger.error(s"validateCsv: Validate file data failed with Status ${res.status}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
      }
    } recover {
      case e: Exception => {
        Logger.error(s"validateCsv: Failed to fetch CsvFilesCallbackList with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def validationFailure(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
          processValidationFailure()(user, request, hc)
  }

  def processValidationFailure()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    Logger.info("validationFailure: Validation Failure: " + (System.currentTimeMillis() / 1000))

    (for {
      requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      fileType      <- cacheUtil.fetch[CheckFileType](CacheUtil.FILE_TYPE_CACHE, requestObject.getSchemeReference)
    } yield {
      Ok(views.html.file_upload_errors(requestObject, fileType.checkFileType.get))
    }).recover {
      case e: Exception => {
        Logger.error(s"processValidationFailure: failed to save callback data list with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def failure(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        Logger.error("failure: Attachments Failure: " + (System.currentTimeMillis() / 1000))
        Future(getGlobalErrorPage)
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages): Result = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))

}

object CsvFileUploadController extends CsvFileUploadController {
  val attachmentsConnector: AttachmentsConnector.type = AttachmentsConnector
  val authConnector: ERSFileValidatorAuthConnector.type = ERSFileValidatorAuthConnector
  val sessionService: SessionService.type = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
}

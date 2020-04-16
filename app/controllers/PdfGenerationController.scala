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

import connectors.ErsConnector
import models._
import models.upscan.{UploadedSuccessfully, UpscanCsvFilesCallback, UpscanCsvFilesCallbackList}
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, LegacyI18nSupport, Request, Result}
import play.api.{Configuration, Logger, Play}
import services.SessionService
import services.pdf.{ApachePdfContentsStreamer, ErsReceiptPdfBuilderService}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.{CacheUtil, PageBuilder}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait PdfGenerationController extends ERSReturnBaseController with Authenticator with LegacyI18nSupport {
  val cacheUtil: CacheUtil
  val pdfBuilderService: ErsReceiptPdfBuilderService


  def buildPdfForBundle(bundle: String, dateSubmitted: String): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
          generatePdf(requestObject, bundle, dateSubmitted)
        }
  }

  def generatePdf(requestObject: RequestObject, bundle: String, dateSubmitted: String)(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    Logger.debug("ers returns frontend getting into the controller to generate the pdf")
    val cache: Future[ErsMetaData] = cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, requestObject.getSchemeReference)
    cache.flatMap { all =>
      Logger.debug("ers returns frontend pdf generation: got the metadata")
      cacheUtil.getAllData(bundle, all).flatMap { alldata =>
        Logger.debug("ers returns frontend generation: got the cache map")

        cacheUtil.fetchAll(requestObject.getSchemeReference).map { all =>
          val filesUploaded: ListBuffer[String] = ListBuffer()
          if (all.getEntry[ReportableEvents](CacheUtil.reportableEvents).get.isNilReturn.get == PageBuilder.OPTION_UPLOAD_SPREEDSHEET) {
            val fileType = all.getEntry[CheckFileType](CacheUtil.FILE_TYPE_CACHE).get.checkFileType.get
            if (fileType == PageBuilder.OPTION_CSV) {
              val csvCallback = all.getEntry[UpscanCsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES).getOrElse (
                throw new Exception(s"Cache data missing for key: ${CacheUtil.CHECK_CSV_FILES} in CacheMap")
              )
              val csvFilesCallback: List[UpscanCsvFilesCallback] = if(csvCallback.areAllFilesSuccessful()) {
                csvCallback.files.collect{
                  case successfulFile@UpscanCsvFilesCallback(_, _, _: UploadedSuccessfully) => successfulFile
                }
              } else {
                throw new Exception("Not all files have been complete")
              }
              
              for (file <- csvFilesCallback) {
                filesUploaded += PageBuilder.getPageElement(requestObject.getSchemeId, PageBuilder.PAGE_CHECK_CSV_FILE, file.fileId + ".file_name")
              }
            } else {
              filesUploaded += all.getEntry[String](CacheUtil.FILE_NAME_CACHE).get
            }
          }
          val pdf = pdfBuilderService.createPdf(new ApachePdfContentsStreamer(alldata), alldata, Some(filesUploaded), dateSubmitted).toByteArray
          Ok(pdf)
            .as("application/pdf")
            .withHeaders(CONTENT_DISPOSITION -> s"inline; filename=$bundle-confirmation.pdf")
        } recover {
          case e: Throwable =>
            Logger.error(s"Problem fetching file list from cache ${e.getMessage}.", e)
            getGlobalErrorPage
        }
      }.recover {
        case e: Throwable =>
          Logger.error(s"Problem saving Pdf Receipt ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
      }
    }
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))
}

object PdfGenerationController extends PdfGenerationController {
  val currentConfig: Configuration = Play.current.configuration
  val sessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
  override val pdfBuilderService: ErsReceiptPdfBuilderService = ErsReceiptPdfBuilderService
}

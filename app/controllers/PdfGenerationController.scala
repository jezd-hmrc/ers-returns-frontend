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

import connectors.{AttachmentsConnector, ErsConnector}
import models._
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


  def buildPdfForBundle(bundle: String, dateSubmitted: String): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        generatePdf(bundle, dateSubmitted)
  }

  def generatePdf(bundle: String, dateSubmitted: String)(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {

    Logger.debug("ers returns frontend getting into the controller to generate the pdf")
    val cache: Future[ErsMetaData] = cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, request.schemeInfo.schemeRef)
    cache.flatMap { all =>
      Logger.debug("ers returns frontend pdf generation: got the metadata")
      cacheUtil.getAllData(bundle, all).flatMap { alldata =>
        Logger.debug("ers returns frontend generation: got the cache map")

        cacheUtil.fetchAll(request.schemeInfo.schemeRef).map { all =>
          val filesUploaded: ListBuffer[String] = ListBuffer()
          val schemeId = request.session.get("screenSchemeInfo").get.split(" - ").head
          if (all.getEntry[ReportableEvents](CacheUtil.reportableEvents).get.isNilReturn.get == PageBuilder.OPTION_UPLOAD_SPREEDSHEET) {
            val fileType = all.getEntry[CheckFileType](CacheUtil.FILE_TYPE_CACHE).get.checkFileType.get
            if (fileType == PageBuilder.OPTION_CSV) {
              val csvFilesCallback: List[CsvFilesCallback] = all.getEntry[CsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES).get.files
              for (file <- csvFilesCallback if file.callbackData.isDefined) {
                filesUploaded += PageBuilder.getPageElement(schemeId, PageBuilder.PAGE_CHECK_CSV_FILE, file.fileId + ".file_name")
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

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))
}

object PdfGenerationController extends PdfGenerationController {
  val attachmentsConnector = AttachmentsConnector
  val currentConfig: Configuration = Play.current.configuration
  val sessionService = SessionService
  val ersConnector: ErsConnector = ErsConnector
  override val cacheUtil: CacheUtil = CacheUtil
  override val pdfBuilderService: ErsReceiptPdfBuilderService = ErsReceiptPdfBuilderService
}

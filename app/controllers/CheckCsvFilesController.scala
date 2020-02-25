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

import models._
import models.upscan.{UploadId, UpscanCsvFilesCallback, UpscanCsvFilesCallbackList}
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.{CacheUtil, PageBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object CheckCsvFilesController extends CheckCsvFilesController {
  override val cacheUtil: CacheUtil = CacheUtil
  override val pageBuilder: PageBuilder = PageBuilder
}

trait CheckCsvFilesController extends ERSReturnBaseController with Authenticator {
  val cacheUtil: CacheUtil
  val pageBuilder: PageBuilder
  private val logger = Logger(this.getClass)

  def checkCsvFilesPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showCheckCsvFilesPage()(user, request, hc)
  }

  def showCheckCsvFilesPage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val requestObjectFuture = cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
    cacheUtil.remove(CacheUtil.CHECK_CSV_FILES)
    (for {
      requestObject <- requestObjectFuture
    } yield {
      val csvFilesList: List[CsvFiles] = PageBuilder.getCsvFilesList(requestObject.getSchemeType)
      Ok(views.html.check_csv_file(requestObject, CsvFilesList(csvFilesList)))
    }) recover {
      case _: Throwable => getGlobalErrorPage
    }
  }

  def checkCsvFilesPageSelected(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        validateCsvFilesPageSelected()
  }

  def validateCsvFilesPageSelected()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.csvFileCheckForm.bindFromRequest.fold(
      _ => {
        reloadWithError()
      },
      formData => {
        performCsvFilesPageSelected(formData)
      }
    )
  }

  def performCsvFilesPageSelected(formData: CsvFilesList)(implicit request: Request[AnyRef], hc: HeaderCarrier) = {
    val csvFilesCallbackList: List[UpscanCsvFilesCallback] = createCacheData(formData.files)
    if(csvFilesCallbackList.isEmpty) {
      reloadWithError()
    } else {
      (for{
        requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
        _             <- cacheUtil.cache(CacheUtil.CHECK_CSV_FILES, UpscanCsvFilesCallbackList(csvFilesCallbackList), requestObject.getSchemeReference)
      } yield {
        Redirect(routes.CsvFileUploadController.uploadFilePage())
      }).recover {
        case e: Throwable =>
          logger.error(s"performCsvFilesPageSelected: Save data to cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.", e)
          getGlobalErrorPage
      }
    }
  }

  def createCacheData(csvFilesList: List[CsvFiles]): List[UpscanCsvFilesCallback] = {
    for(fileData <- csvFilesList if fileData.isSelected.contains(PageBuilder.OPTION_YES)) yield {
      UpscanCsvFilesCallback(UploadId.generate, fileData.fileId)
    }
  }

  def reloadWithError()(implicit messages: Messages): Future[Result] = {
    Future.successful(
      Redirect(routes.CheckCsvFilesController.checkCsvFilesPage()).flashing("csv-file-not-selected-error" -> messages(PageBuilder.PAGE_CHECK_CSV_FILE + ".err.message"))
    )
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))

}

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

import java.time.LocalDateTime

import _root_.models._
import connectors.ErsConnector
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object SummaryDeclarationController extends SummaryDeclarationController {
  override val cacheUtil: CacheUtil = CacheUtil
  override val ersConnector: ErsConnector = ErsConnector
}

trait SummaryDeclarationController extends ERSReturnBaseController with Authenticator with ErsConstants {

  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector

  def summaryDeclarationPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showSummaryDeclarationPage()(user, request, hc)
  }

  def showSummaryDeclarationPage()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetchAll(request.schemeRef).flatMap { all =>
      val schemeOrganiser: SchemeOrganiserDetails = all.getEntry[SchemeOrganiserDetails](CacheUtil.SCHEME_ORGANISER_CACHE).get
      val groupSchemeInfo: GroupSchemeInfo = all.getEntry[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER).getOrElse(new GroupSchemeInfo(None, None))
      val groupScheme: String = groupSchemeInfo.groupScheme.getOrElse("")
      val reportableEvents: String = all.getEntry[ReportableEvents](CacheUtil.reportableEvents).get.isNilReturn.get
      val schemeId = request.session.get("screenSchemeInfo").get.split(" - ").head
      var fileType: String = ""
      var fileNames: String = ""
      var fileCount: Int = 0


      //TODO WRITE FUNCTIONALLY!
      if (reportableEvents == PageBuilder.OPTION_YES) {
        fileType = all.getEntry[CheckFileType](CacheUtil.FILE_TYPE_CACHE).get.checkFileType.get
        if (fileType == PageBuilder.OPTION_CSV) {
          val csvFilesCallback: List[CsvFilesCallback] = all.getEntry[CsvFilesCallbackList](CacheUtil.CHECK_CSV_FILES).get.files
          for (file <- csvFilesCallback if file.callbackData.isDefined) {
            fileNames = fileNames + Messages(PageBuilder.getPageElement(schemeId, PageBuilder.PAGE_CHECK_CSV_FILE, file.fileId + ".file_name")) + "<br/>"
            fileCount += 1
          }
        } else {
          fileNames = all.getEntry[String](CacheUtil.FILE_NAME_CACHE).get
          fileCount += 1
        }
      }

      val altAmendsActivity = all.getEntry[AltAmendsActivity](CacheUtil.altAmendsActivity).getOrElse(AltAmendsActivity(""))
      val altActivity = schemeId match {
        case PageBuilder.SCHEME_CSOP | PageBuilder.SCHEME_SIP | PageBuilder.SCHEME_SAYE => altAmendsActivity.altActivity
        case _ => ""
      }
      Future(Ok(views.html.summary(reportableEvents, fileType, fileNames, fileCount, groupScheme, schemeOrganiser,
        getCompDetails(all), altActivity, getAltAmends(all), getTrustees(all))))
    } recover {
      case e: Throwable => Logger.error(s"showSummaryDeclarationPage failed to fetch data with exception ${e.getMessage}.", e)
        getGlobalErrorPage
    }
  }

  def getTrustees(cacheMap: CacheMap): TrusteeDetailsList =
    cacheMap.getEntry[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE).getOrElse(TrusteeDetailsList(List[TrusteeDetails]()))

  def getAltAmends(cacheMap: CacheMap): AlterationAmends =
    cacheMap.getEntry[AlterationAmends](CacheUtil.ALT_AMENDS_CACHE_CONTROLLER).getOrElse(new AlterationAmends(None, None, None, None, None))

  def getCompDetails(cacheMap: CacheMap): CompanyDetailsList =
    cacheMap.getEntry[CompanyDetailsList](CacheUtil.GROUP_SCHEME_COMPANIES).getOrElse(CompanyDetailsList(List[CompanyDetails]()))

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

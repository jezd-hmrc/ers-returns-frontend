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

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import _root_.models._
import config.ApplicationConfig
import connectors.ErsConnector
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.audit.AuditEvents
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionKeys.{BUNDLE_REF, DATE_TIME_SUBMITTED}
import utils._

import scala.concurrent.Future

@Singleton
class ConfirmationPageController @Inject()(val messagesApi: MessagesApi,
																					 val ersConnector: ErsConnector,
																					 val authConnector: DefaultAuthConnector,
																					 val auditEvents: AuditEvents,
																					 implicit val ersUtil: ERSUtil,
																					 implicit val appConfig: ApplicationConfig
																					) extends FrontendController with Authenticator with I18nSupport {

  def confirmationPage(): Action[AnyContent] = authorisedForAsync() {
    implicit authContext: ERSAuthData =>
			implicit request =>
          showConfirmationPage()(authContext, request, hc)
  }

  def showConfirmationPage()(implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).flatMap { requestObject =>
      val schemeRef: String = requestObject.getSchemeReference
      val sessionBundleRef: String = request.session.get(BUNDLE_REF).getOrElse("")
      val sessionDateTimeSubmitted: String = request.session.get(DATE_TIME_SUBMITTED).getOrElse("")
      if (sessionBundleRef == "") {
        ersUtil.fetch[ErsMetaData](ersUtil.ersMetaData, schemeRef).flatMap { all =>
          if (all.sapNumber.isEmpty) Logger.error(s"Did cache util fail for scheme $schemeRef all.sapNumber is empty: $all")
					val submissionJson = ersUtil.getSubmissionJson(all.schemeInfo.schemeRef, all.schemeInfo.schemeType, all.schemeInfo.taxYear, "EOY-RETURN")
          ersConnector.connectToEtmpSummarySubmit(all.sapNumber.get, submissionJson).flatMap { bundle =>
            ersUtil.getAllData(bundle, all).flatMap { alldata =>
              if (alldata.isNilReturn == ersUtil.OPTION_NIL_RETURN) {
                saveAndSubmit(alldata, all, bundle)
              } else {
                ersUtil.fetch[String](ersUtil.VALIDATED_SHEEETS, schemeRef).flatMap { validatedSheets =>
                  ersConnector.checkForPresubmission(all.schemeInfo, validatedSheets).flatMap { checkResult =>
                    checkResult.status match {
                      case OK =>
                        Logger.info(s"Check for presubmission success with status ${checkResult.status}.")
                        saveAndSubmit(alldata, all, bundle)
                      case _ =>
                        Logger.error(s"File data not found: ${checkResult.status}")
                        Future(getGlobalErrorPage)
                    }
                  }
                }
              }
            }
          }
        }
      } else {
        val url: String = appConfig.portalDomain
        ersUtil.fetch[ErsMetaData](ersUtil.ersMetaData, schemeRef).flatMap { all =>
          Logger.info(s"Preventing resubmission of confirmation page, timestamp: ${System.currentTimeMillis()}.")

          Future(Ok(views.html.confirmation(requestObject, sessionDateTimeSubmitted, sessionBundleRef, all.schemeInfo.taxYear, url)))
        }
      }
    } recoverWith {
      case e: Throwable =>
        Logger.error(s"Failed to render Confirmation page: ${e.getMessage}")
        Future.successful(getGlobalErrorPage)
    }
  }

  def saveAndSubmit(alldata: ErsSummary, all: ErsMetaData, bundle: String)
									 (implicit authContext: ERSAuthData, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    val jsonDateTimeFormat = new SimpleDateFormat("d MMMM yyyy, h:mma")
    val dateTimeSubmitted = jsonDateTimeFormat.format(alldata.confirmationDateTime.toDate).replace("AM", "am").replace("PM", "pm")

    ersConnector.saveMetadata(alldata).flatMap { res =>
      res.status match {
        case OK =>
          val startTime = System.currentTimeMillis()
          Logger.info("alldata.transferStatus is " + alldata.transferStatus)
          if (alldata.transferStatus.contains(ersUtil.largeFileStatus)) {
            None
          } else {
            ersConnector.submitReturnToBackend(alldata).map { response =>
              response.status match {
                case OK =>
                  auditEvents.ersSubmissionAuditEvent(all, bundle)
                  ersUtil.submitReturnToBackend(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
                  Logger.info(s"Submitting return to backend success with status ${response.status}.")
                case _ =>
                  ersUtil.submitReturnToBackend(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
                  Logger.info(s"Submitting return to backend failed with status ${response.status}.")
              }
              Logger.info(s"Process data ends: ${System.currentTimeMillis()}")
            } recover {
              case e: Throwable =>
                Logger.error(s"Submitting return to backend failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
                auditEvents.auditRunTimeError(e.getCause, e.getMessage, all, bundle)
            }
          }

          Logger.warn(s"Submission completed for schemeInfo: ${all.schemeInfo.toString}, bundle: $bundle ")
          val url: String = appConfig.portalDomain

          ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).map { requestObject =>
            Ok(views.html.confirmation(requestObject, dateTimeSubmitted, bundle, all.schemeInfo.taxYear, url))
							.withSession(request.session + (BUNDLE_REF -> bundle) + (DATE_TIME_SUBMITTED -> dateTimeSubmitted))
          }
        case _ =>
          Logger.info(s"Save meta data to backend returned status ${res.status}, timestamp: ${System.currentTimeMillis()}.")
          Future.successful(getGlobalErrorPage)
      }
    } recover { case e: Throwable =>
      Logger.error(s"Save meta data to backend failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
      getGlobalErrorPage
    }

  }

	def getGlobalErrorPage(implicit request: Request[_], messages: Messages): Result = {
		Ok(views.html.global_error(
			"ers.global_errors.title",
			"ers.global_errors.heading",
			"ers.global_errors.message"
		)(request, messages, appConfig))
	}
}

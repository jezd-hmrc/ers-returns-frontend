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

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import _root_.models._
import connectors.ErsConnector
import metrics.Metrics
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Request, Result}
import services.audit.AuditEvents
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.{CacheUtil, ExternalUrls, _}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object ConfirmationPageController extends ConfirmationPageController {
  override val cacheUtil: CacheUtil = CacheUtil
  override val ersConnector: ErsConnector = ErsConnector
  override val jsonParser: JsonParser = JsonParser
  override val metrics: Metrics = Metrics
}

trait ConfirmationPageController extends ERSReturnBaseController with Authenticator with ErsConstants {

  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector
  val jsonParser: JsonParser
  val metrics: Metrics

  def confirmationPage() = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
          showConfirmationPage()(user, request, hc)
  }

  def showConfirmationPage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
      if (request.session.get(screenSchemeInfo).isEmpty) Logger.error(s"Session doesn't contain scheme info: ${request.session}")
      val schemeRef: String = requestObject.getSchemeReference
      val sessionBundelRef: String = request.session.get("bundelRef").getOrElse("")
      val sessionDateTimeSubmitted: String = request.session.get("dateTimeSubmitted").getOrElse("")
      if (sessionBundelRef == "") {
        cacheUtil.fetch[ErsMetaData](cacheUtil.ersMetaData, schemeRef).flatMap { all =>
          if (all.sapNumber.isEmpty) Logger.error(s"Did cache util fail for scheme ${schemeRef} all.sapNumber is empty: ${all}")
          ersConnector.connectToEtmpSummarySubmit(all.sapNumber.get, jsonParser.getSubmissionJson(all.schemeInfo.schemeRef, all.schemeInfo.schemeType, all.schemeInfo.taxYear, "EOY-RETURN")).flatMap { bundle =>
            cacheUtil.getAllData(bundle, all).flatMap { alldata =>
              if (alldata.isNilReturn == PageBuilder.OPTION_NIL_RETURN) {
                saveAndSubmit(alldata, all, bundle)
              } else {
                cacheUtil.fetch[String](cacheUtil.VALIDATED_SHEEETS, schemeRef).flatMap { validatedSheets =>
                  ersConnector.checkForPresubmission(all.schemeInfo, validatedSheets).flatMap { checkResult =>
                    checkResult.status match {
                      case 200 =>
                        Logger.info(s"Check for presubmission success with status ${checkResult.status}.")
                        saveAndSubmit(alldata, all, bundle)
                      case _ =>
                        Logger.error(s"File data not found: ${checkResult.status}")
                        Future(getGlobalErrorPage)
                    }
                  } recover {
                    case e: Throwable =>
                      Logger.error(s"ERS connector call failed: ${e.getMessage}")
                      getGlobalErrorPage
                  }
                }
              }
            } recover {
              case e: Throwable =>
                Logger.error(s"Get all data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
                getGlobalErrorPage
            }
          } recover {
            case e: Throwable =>
              Logger.error(s"Get bundle failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
              getGlobalErrorPage
          }
        } recover {
          case e: Throwable =>
            Logger.error(s"Get ersMetaData with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }
      } else {
        val url: String = ExternalUrls.portalDomain
        cacheUtil.fetch[ErsMetaData](cacheUtil.ersMetaData, schemeRef).flatMap { all =>
          Logger.info(s"Preventing resubmission of confirmation page, timestamp: ${System.currentTimeMillis()}.")

          Future(Ok(views.html.confirmation(requestObject, sessionDateTimeSubmitted, sessionBundelRef, all.schemeInfo.taxYear, url)(request, context, implicitly)))

        } recover {
          case e: Throwable =>
            Logger.error(s"Get ersMetaData with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }
      }
    } recover {
      case e: Throwable =>
        Logger.error(s"Get request object with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def saveAndSubmit(alldata: ErsSummary, all: ErsMetaData, bundle: String)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    val jsonDateTimeFormat = new SimpleDateFormat("d MMMM yyyy, h:mma")
    val dateTimeSubmitted = jsonDateTimeFormat.format(alldata.confirmationDateTime.toDate).replace("AM", "am").replace("PM", "pm")

    ersConnector.saveMetadata(alldata).flatMap { res =>
      res.status match {
        case 200 =>
          val startTime = System.currentTimeMillis()
          Logger.info("alldata.transferStatus  is " + alldata.transferStatus)
          if (alldata.transferStatus.contains(CacheUtil.largeFileStatus)) {
            None
          } else {
            ersConnector.submitReturnToBackend(alldata).map { response =>
              response.status match {
                case 200 =>
                  AuditEvents.ErsSubmissionAuditEvent(all, bundle)
                  metrics.submitReturnToBackend(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
                  Logger.info(s"Submitting return to backend success with status ${response.status}.")
                case _ =>
                  metrics.submitReturnToBackend(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
                  Logger.info(s"Submitting return to backend failed with status ${response.status}.")
              }
              Logger.info(s"Process data ends: ${System.currentTimeMillis()}")
            } recover {
              case e: Throwable =>
                Logger.error(s"Submitting return to backend failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
                AuditEvents.auditRunTimeError(e.getCause, e.getMessage, all, bundle)
            }
          }

          Logger.warn(s"Submission completed for schemeInfo: ${all.schemeInfo.toString}, bundle: ${bundle} ")
          val url: String = ExternalUrls.portalDomain

          cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).map { requestObject =>
            Ok(views.html.confirmation(requestObject, dateTimeSubmitted, bundle, all.schemeInfo.taxYear, url)(request, context, implicitly)).withSession(request.session + ("bundelRef" -> bundle) + ("dateTimeSubmitted" -> dateTimeSubmitted))
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

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

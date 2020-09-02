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
import config._
import javax.inject.{Inject, Singleton}
import play.Logger
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionKeys.{BUNDLE_REF, DATE_TIME_SUBMITTED}
import utils._

import scala.concurrent.Future

@Singleton
class ReturnServiceController @Inject()(val messagesApi: MessagesApi,
																				val authConnector: DefaultAuthConnector,
																				implicit val ersUtil: ERSUtil,
																				implicit val appConfig: ApplicationConfig
																			 ) extends FrontendController with Authenticator {

	lazy val accessThreshold: Int = appConfig.accessThreshold
	val accessDeniedUrl = "/outage-ers-frontend/index.html"

  def cacheParams(ersRequestObject: RequestObject)(implicit request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    implicit val formatRSParams: OFormat[RequestObject] = Json.format[RequestObject]

    Logger.debug("Request Object created --> " + ersRequestObject)
    ersUtil.cache(ersUtil.ersMetaData, ersRequestObject.toErsMetaData, ersRequestObject.getSchemeReference).flatMap { _ =>
      Logger.info(s"[ReturnServiceController][cacheParams]Meta Data Cached --> ${ersRequestObject.toErsMetaData}")
      ersUtil.cache(ersUtil.ersRequestObject, ersRequestObject) flatMap {
        _ => {
          Logger.info(s"[ReturnServiceController][cacheParams] Request Object Cached -->  $ersRequestObject")
					Future.successful(showInitialStartPage(ersRequestObject)(request, hc))
        }
    }
    } recover { case e: Exception =>
      Logger.warn(s"[ReturnServiceController][cacheParams] Caught exception ${e.getMessage}", e)
      getGlobalErrorPage
    }
  }

  def getRequestParameters(request: Request[AnyContent]): RequestObject = {
    val aoRef: Option[String] = request.getQueryString("aoRef")
    val taxYear: Option[String] = request.getQueryString("taxYear")
    val ersSchemeRef: Option[String] = request.getQueryString("ersSchemeRef")
    val schemeType: Option[String] = request.getQueryString("schemeType")
    val schemeName: Option[String] = request.getQueryString("schemeName")
    val agentRef: Option[String] = request.getQueryString("agentRef")
    val empRef: Option[String] = request.getQueryString("empRef")
    val ts: Option[String] = request.getQueryString("ts")
    val hmac: Option[String] = request.getQueryString("hmac")
    val reqObj = RequestObject(aoRef, taxYear, ersSchemeRef, schemeName, schemeType, agentRef, empRef, ts, hmac)
    Logger.info(s"Request Parameters:  ${reqObj.toString}")
    reqObj
  }

  def hmacCheck(): Action[AnyContent] = Action.async {
      implicit request =>
				authorisedByGovGateway {
					implicit user =>
					Logger.info("HMAC Check Authenticated")
					if (request.getQueryString("ersSchemeRef").getOrElse("") == "") {
						Logger.warn("Missing SchemeRef in URL")
						Future(getGlobalErrorPage)
					} else {
						if (ersUtil.isHmacAndTimestampValid(getRequestParameters(request))) {
							Logger.info("HMAC Check Valid")
							try {
								cacheParams(getRequestParameters(request))
							} catch {
								case e: Throwable => Logger.warn(s"Caught exception ${e.getMessage}", e)
									Future(getGlobalErrorPage)
							}
						} else {
							Logger.warn("HMAC Check Invalid")
							showUnauthorisedPage(request)
						}
					}
				}
  }

  def showInitialStartPage(requestObject: RequestObject)
													(implicit request: Request[AnyRef], hc: HeaderCarrier): Result = {
    val sessionData = s"${requestObject.getSchemeId} - ${requestObject.getPageTitle}"
    Ok(views.html.start(requestObject)).
      withSession(request.session + ("screenSchemeInfo" -> sessionData) - BUNDLE_REF - DATE_TIME_SUBMITTED)
  }

  def startPage(): Action[AnyContent] = authorisedByGG {
    implicit user =>
      implicit request =>
        ersUtil.fetch[RequestObject](ersUtil.ersRequestObject).map{
          result =>
            Ok(views.html.start(result)).withSession(request.session - BUNDLE_REF - DATE_TIME_SUBMITTED)
        }
  }

  def showUnauthorisedPage(implicit request: Request[AnyRef]): Future[Result] = {
    Future.successful(Ok(views.html.unauthorised()))
  }

	def getGlobalErrorPage(implicit request: Request[_], messages: Messages): Result = {
		Ok(views.html.global_error(
			"ers.global_errors.title",
			"ers.global_errors.heading",
			"ers.global_errors.message"
		)(request, messages, appConfig))
	}
}

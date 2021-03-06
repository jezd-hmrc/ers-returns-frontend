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

package controllers.auth

import config.ApplicationConfig
import models.ERSAuthData
import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.auth.core.AffinityGroup._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{authorisedEnrolments, _}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthFunctionality extends AuthorisedFunctions {

  lazy val signInUrl: String = ApplicationConfig.ggSignInUrl
  val origin: String = "ers-returns-frontend"

  def loginParams: Map[String, Seq[String]] = Map(
    "continue" -> Seq(ExternalUrls.loginCallback),
    "origin" -> Seq(origin)
  )

	private def handleException(implicit request: Request[AnyContent]): PartialFunction[Throwable, Result] = {
		case _: NoActiveSession => Redirect(ApplicationConfig.ggSignInUrl, loginParams)
		case er: AuthorisationException =>
			Logger.error(s"[AuthFunctionality][handleException] Auth exception: $er")
			Redirect(controllers.routes.ApplicationController.unauthorised().url)
	}

  def authoriseFor[A](body: ERSAuthData => Future[Result])
                     (implicit hc: HeaderCarrier, ec: ExecutionContext, req: Request[AnyContent]): Future[Result] = {
    authorised((Enrolment("IR-PAYE") or Enrolment("HMRC-AGENT-AGENT") or Agent) and AuthProviders(GovernmentGateway))
      .retrieve(authorisedEnrolments and affinityGroup) {
        case authorisedEnrolments ~ affinityGroup  =>
						body(ERSAuthData(authorisedEnrolments.enrolments, affinityGroup))
      } recover handleException
	}

	def authorisedByGovGateway[A](body: ERSAuthData => Future[Result])
										 (implicit hc: HeaderCarrier, ec: ExecutionContext, req: Request[AnyContent]): Future[Result] = {
		authorised(AuthProviders(GovernmentGateway))
			.retrieve(authorisedEnrolments and affinityGroup) {
				case authorisedEnrolments ~ affinityGroup  =>
					body(ERSAuthData(authorisedEnrolments.enrolments, affinityGroup))
			} recover handleException
	}




}

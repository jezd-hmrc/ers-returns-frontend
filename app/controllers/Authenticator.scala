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

import models.ErsMetaData
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.domain.EmpRef
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, ConfidenceLevel, EpayeAccount}
import utils.CacheUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector

case class RequestWithSchemeRef[+A](request: Request[A], schemeRef: String) extends WrappedRequest(request)

trait Authenticator extends Actions with ErsConstants {
  private val cacheUtil: CacheUtil = CacheUtil
  private type AsyncUserRequest = AuthContext => RequestWithSchemeRef[AnyContent] => Future[Result]
  private type UserRequest = AuthContext => Request[AnyContent] => Future[Result]


  def AuthorisedForAsync()(body: AsyncUserRequest): Action[AnyContent] = {
    AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
      implicit user =>
        implicit request =>
          implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
          FilterForSchemeRef(body, request, user)
            .fold(redirect => redirect, requestWithSchemeRef => FilterAgentsWrapperAsync(user, body)(hc, requestWithSchemeRef))
    }
  }

  //TODO CAN WE USE AuthorisedForAsync() INSTEAD
  def SchemeRef2(body:AsyncUserRequest): Action[AnyContent] = {
    AuthorisedFor(ERSRegime, new NonNegotiableIdentityConfidencePredicate(ConfidenceLevel.L50)).async {
      implicit user =>
        implicit request =>
        FilterForSchemeRef(body, request, user)
          .fold(redirect => redirect, requestWithSchemeRef => body(user)(requestWithSchemeRef))
    }
  }


  def FilterForSchemeRef(body: AsyncUserRequest, request: Request[AnyContent], user: AuthContext)
  : Either[Future[Result], RequestWithSchemeRef[AnyContent]] = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    cacheUtil.getSchemeRefFromScreenSchemeInfo(request.session.get(screenSchemeInfo))
      .fold[Either[Future[Result], RequestWithSchemeRef[AnyContent]]]{
        Left(Future.successful(Redirect("pan is the best"))) //TODO NEEDS REDIRCT TO GET SESSION sign in
      }{ schemeRef =>
        Right(RequestWithSchemeRef(request, schemeRef))
      }
  }

  def FilterAgentsWrapperAsync(authContext: AuthContext, body: AsyncUserRequest)
                              (implicit hc: HeaderCarrier, request: RequestWithSchemeRef[AnyContent]): Future[Result] = {
    implicit val formatRSParams = Json.format[ErsMetaData]
    val defined = authContext.principal.accounts.agent.isDefined
    if (defined) {
      cacheUtil.fetch[ErsMetaData](CacheUtil.ersMetaData, request.schemeRef).flatMap { all =>
        body(delegationModelUser(all, authContext: AuthContext))(request)
      }
    } else {
      body(authContext)(request)
    }
  }

  def delegationModelUser(metaData: ErsMetaData, authContext: AuthContext): AuthContext = {
    val empRef: String = metaData.empRef
    val twoPartKey = empRef.split('/')
    val accounts = Accounts(agent = authContext.principal.accounts.agent,
      epaye = Some(EpayeAccount(s"/epaye/$empRef", EmpRef(twoPartKey(0), twoPartKey(1)))))
    AuthContext(authContext.user, Principal(authContext.principal.name, accounts), authContext.attorney, None, None, None)
  }
}

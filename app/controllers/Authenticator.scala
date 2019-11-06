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

import models.{ErsMetaData, RequestObject}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.domain.EmpRef
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, EpayeAccount}
import utils.CacheUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter


trait Authenticator extends Actions with ErsConstants {
  private val cacheUtil: CacheUtil = CacheUtil
  private type AsyncUserRequest = AuthContext => Request[AnyContent] => Future[Result]
  private type UserRequest = AuthContext => Request[AnyContent] => Result

  def AuthorisedForAsync()(body: AsyncUserRequest): Action[AnyContent] = {
    AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
      implicit user =>
        implicit request => {
          implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
          FilterAgentsWrapperAsync(user, body)
        }
    }
  }

  def AuthorisedFor(body: UserRequest): Action[AnyContent] = {
    AuthorisedFor(ERSRegime, pageVisibility = GGConfidence).async {
      implicit user =>
        implicit request =>
          implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
          FilterAgentsWrapper(user, body)
    }
  }

  def FilterAgentsWrapper(authContext: AuthContext, body: UserRequest)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    implicit val formatRSParams = Json.format[ErsMetaData]
    if (authContext.principal.accounts.agent.isDefined) {

      for {
        requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
        all           <- cacheUtil.fetch[ErsMetaData](cacheUtil.ersMetaData, requestObject.getSchemeReference)
      } yield {
        body(delegationModelUser(all, authContext: AuthContext))(request)
      }

    } else {
      Future {body(authContext)(request)}
    }
  }

  def FilterAgentsWrapperAsync(authContext: AuthContext, body: AsyncUserRequest)
                              (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    implicit val formatRSParams = Json.format[ErsMetaData]
    if (authContext.principal.accounts.agent.isDefined) {

      for {
        requestObject <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
        all           <- cacheUtil.fetch[ErsMetaData](cacheUtil.ersMetaData, requestObject.getSchemeReference)
        result        <- body(delegationModelUser(all, authContext: AuthContext))(request)
      } yield {
        result
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

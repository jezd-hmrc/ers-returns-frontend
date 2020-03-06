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

import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.frontend.controller.UnauthorisedAction
import utils.{CacheUtil, ExternalUrls}

import scala.concurrent.Future
// $COVERAGE-OFF$
object AuthorizationController extends AuthorizationController {
  override val cacheUtil: CacheUtil = CacheUtil
}

trait AuthorizationController extends ERSReturnBaseController with Authenticator {

  def notAuthorised(): Action[AnyContent] = authorisedForAsync() {
    implicit user =>
      implicit request =>
      Future.successful(Ok(views.html.not_authorised.render(request, context)))
  }

  def timedOut() = UnauthorisedAction {
    implicit request =>
      val loginScreenUrl = ExternalUrls.portalDomain
      Ok(views.html.signedOut(loginScreenUrl))
  }

}

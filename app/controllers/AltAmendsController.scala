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

import _root_.models._
import connectors.ErsConnector
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils.{CacheUtil, PageBuilder}

import scala.concurrent._
import uk.gov.hmrc.http.HeaderCarrier

object AltAmendsController extends AltAmendsController {
  override val cacheUtil: CacheUtil = CacheUtil
  override val ersConnector: ErsConnector = ErsConnector
}

trait AltAmendsController extends ERSReturnBaseController with Authenticator with ErsConstants {
  val cacheUtil: CacheUtil
  val ersConnector: ErsConnector

  def altActivityPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showAltActivityPage()(user, request, hc)
  }

  def showAltActivityPage()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val schemeRef: String = request.schemeInfo.schemeRef
    cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, schemeRef).flatMap { groupSchemeActivity =>
      cacheUtil.fetch[AltAmendsActivity](CacheUtil.altAmendsActivity, schemeRef).map { altAmendsActivity =>
        Ok(views.html.alterations_activity(altAmendsActivity.altActivity,
          groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT),
          RsFormMappings.altActivityForm.fill(altAmendsActivity)))
      } recover {
        case e: NoSuchElementException => {
          val form = AltAmendsActivity("")
          Ok(views.html.alterations_activity("", groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT), RsFormMappings.altActivityForm.fill(form)))
        }
      }
    } recover {
      case e: Throwable => {
        Logger.error(s"showAltActivityPage: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def altActivitySelected(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showAltActivitySelected()(user, request, hc)
  }

  def showAltActivitySelected()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.altActivityForm.bindFromRequest.fold(
      errors => {
        Future.successful(Ok(views.html.alterations_activity("", "", errors)))
      },
      formData => {
        cacheUtil.cache(CacheUtil.altAmendsActivity, formData, request.schemeInfo.schemeRef).map { _ =>
          formData.altActivity match {
            case PageBuilder.OPTION_NO => Redirect(routes.SummaryDeclarationController.summaryDeclarationPage())
            case PageBuilder.OPTION_YES => Redirect(routes.AltAmendsController.altAmendsPage())
          }
        } recover {
          case e: Throwable =>
            Logger.error(s"showAltActivitySelected: Save data to cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
        }
      }
    )
  }

  def altAmendsPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showAltAmendsPage()(user, request, hc)
  }

  def showAltAmendsPage()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[AltAmends](CacheUtil.ALT_AMENDS_CACHE_CONTROLLER, request.schemeInfo.schemeRef).map { altAmends =>
      Ok(views.html.alterations_amends(altAmends))
    } recover {
      case e: NoSuchElementException => Ok(views.html.alterations_amends(AltAmends(None, None, None, None, None)))
    }
  }

  def altAmendsSelected(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showAltAmendsSelected()(user, request, hc)
  }

  def showAltAmendsSelected()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.altAmendsForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(Redirect(routes.AltAmendsController.altAmendsPage()).flashing("alt-amends-not-selected-error" -> PageBuilder.getPageElement(request.schemeInfo.schemeRef, PageBuilder.PAGE_ALT_AMENDS, "err.message")))
      },
      formData => {
        val altAmends = AltAmends(
          if (formData.altAmendsTerms.isDefined) formData.altAmendsTerms else Some("0"),
          if (formData.altAmendsEligibility.isDefined) formData.altAmendsEligibility else Some("0"),
          if (formData.altAmendsExchange.isDefined) formData.altAmendsExchange else Some("0"),
          if (formData.altAmendsVariations.isDefined) formData.altAmendsVariations else Some("0"),
          if (formData.altAmendsOther.isDefined) formData.altAmendsOther else Some("0")
        )
        //TODO add Scheme Id to request
        val schemeId = request.session.get(screenSchemeInfo).get.split(" - ").head
        cacheUtil.cache(CacheUtil.ALT_AMENDS_CACHE_CONTROLLER, altAmends, schemeRef).flatMap { all =>
          if (formData.altAmendsTerms.isEmpty
            && formData.altAmendsEligibility.isEmpty
            && formData.altAmendsExchange.isEmpty
            && formData.altAmendsVariations.isEmpty
            && formData.altAmendsOther.isEmpty) {
            Future.successful(Redirect(routes.AltAmendsController.altAmendsPage()).flashing("alt-amends-not-selected-error" -> PageBuilder.getPageElement(schemeId, PageBuilder.PAGE_ALT_AMENDS, "err.message")))
          } else {
            Future.successful(Redirect(routes.SummaryDeclarationController.summaryDeclarationPage()))
          }
        } recover {
          case e: Throwable => {
            Logger.error(s"showAltAmendsSelected: Save data to cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
          }
        }
      }
    )
  }

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

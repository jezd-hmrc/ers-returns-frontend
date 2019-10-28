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

import models._
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import utils._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object TrusteeController extends TrusteeController {
  override val cacheUtil: CacheUtil = CacheUtil
}

trait TrusteeController extends ERSReturnBaseController with Authenticator {
  val cacheUtil: CacheUtil

  def trusteeDetailsPage(index: Int): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
          showTrusteeDetailsPage(requestObject, index)(user, request, hc)
        }
  }

  def showTrusteeDetailsPage(requestObject: RequestObject, index: Int)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, requestObject.getSchemeReference).map { groupSchemeActivity =>
      Ok(views.html.trustee_details(requestObject, groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT), index, RsFormMappings.trusteeDetailsForm))
    } recover {
      case e: Exception => {
        Logger.error(s"showTrusteeDetailsPage: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def trusteeDetailsSubmit(index: Int): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject).flatMap { requestObject =>
          showTrusteeDetailsSubmit(requestObject, index)(user, request, hc)
        }
  }

  def showTrusteeDetailsSubmit(requestObject: RequestObject, index: Int)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.trusteeDetailsForm.bindFromRequest.fold(
      errors => {
        cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, requestObject.getSchemeReference).map { groupSchemeActivity =>
          val correctOrder = errors.errors.map(_.key).distinct
          val incorrectOrderGrouped = errors.errors.groupBy(_.key).map(_._2.head).toSeq
          val correctOrderGrouped = correctOrder.flatMap(x => incorrectOrderGrouped.find(_.key == x))
          val firstErrors: Form[models.TrusteeDetails] = new Form[TrusteeDetails](errors.mapping, errors.data, correctOrderGrouped, errors.value)
          Ok(views.html.trustee_details(requestObject, groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT), index, firstErrors))
        } recover {
          case e: Exception => {
            Logger.error(s"showTrusteeDetailsSubmit: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
          }
        }
      },
      formData => {
        cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, requestObject.getSchemeReference).flatMap { cachedTrusteeList =>

          val trusteesList = if (index == 10000 || cachedTrusteeList.trustees.size >= index) {
            TrusteeDetailsList(cachedTrusteeList.trustees :+ formData)
          } else {
            TrusteeDetailsList(cachedTrusteeList.trustees)
          }
          cacheUtil.cache(CacheUtil.TRUSTEES_CACHE, trusteesList, requestObject.getSchemeReference).map { all =>
            Redirect(routes.TrusteeController.trusteeSummaryPage())
          }

        } recoverWith {
          case _: Exception =>
            val trusteeList = TrusteeDetailsList(List(formData))
            cacheUtil.cache(CacheUtil.TRUSTEES_CACHE, trusteeList, requestObject.getSchemeReference).map {
              _ => Redirect(routes.TrusteeController.trusteeSummaryPage())
            }
        }
      }
    )
  }

  def deleteTrustee(id: Int): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showDeleteTrustee(id)(user, request, hc)
  }

  def showDeleteTrustee(id: Int)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject      <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      cachedTrusteeList  <- cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, requestObject.getSchemeReference)
      trusteeDetailsList = TrusteeDetailsList(cachedTrusteeList.trustees.drop(id))
      _                  <- cacheUtil.cache(CacheUtil.TRUSTEES_CACHE, trusteeDetailsList, requestObject.getSchemeReference)
    } yield {

      Redirect(routes.TrusteeController.trusteeSummaryPage())

    }) recover {
      case _: Exception => getGlobalErrorPage
    }
  }

  def editTrustee(id: Int): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
          showEditTrustee(id)(user, request, hc)
  }

  def showEditTrustee(id: Int)(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject       <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      groupSchemeActivity <- cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, requestObject.getSchemeReference)
      trusteeDetailsList  <- cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, requestObject.getSchemeReference)
      formDetails         = trusteeDetailsList.trustees(id)
    } yield {

        Ok(views.html.trustee_details(requestObject, groupSchemeActivity.groupScheme.get, id, RsFormMappings.trusteeDetailsForm.fill(formDetails)))

    }) recover {
      case e: Exception =>
        Logger.error(s"showEditTrustee: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def trusteeSummaryPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
          showTrusteeSummaryPage()(user, request, hc)
  }

  def showTrusteeSummaryPage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {

    (for {
      requestObject      <- cacheUtil.fetch[RequestObject](cacheUtil.ersRequestObject)
      trusteeDetailsList <- cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, requestObject.getSchemeReference)
    } yield {

      Ok(views.html.trustee_summary(requestObject, trusteeDetailsList))
    }) recover {
      case e: Exception =>
        Logger.error(s"showTrusteeSummaryPage: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
    }
  }

  def trusteeSummaryContinue(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        continueFromTrusteeSummaryPage()(user, request, hc)
  }

  def continueFromTrusteeSummaryPage()(implicit authContext: AuthContext, request: Request[AnyRef], hc: HeaderCarrier): Future[Result] = {
    Future(Redirect(routes.AltAmendsController.altActivityPage()))
  }

  def getGlobalErrorPage(implicit request: Request[_], messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(request, messages))

}

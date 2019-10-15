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

import models.{RsFormMappings, TrusteeDetails, TrusteeDetailsList, _}
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
        showTrusteeDetailsPage(index)(user, request, hc)
  }

  def showTrusteeDetailsPage(index: Int)(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyContent], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, request.schemeInfo.schemeRef).map { groupSchemeActivity =>
      Ok(views.html.trustee_details(groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT), index, RsFormMappings.trusteeDetailsForm))
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
        showTrusteeDetailsSubmit(index)(user, request, hc)
  }

  def showTrusteeDetailsSubmit(index: Int)(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    RsFormMappings.trusteeDetailsForm.bindFromRequest.fold(
      errors => {
        cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, request.schemeInfo.schemeRef).map { groupSchemeActivity =>
          val correctOrder = errors.errors.map(_.key).distinct
          val incorrectOrderGrouped = errors.errors.groupBy(_.key).map(_._2.head).toSeq
          val correctOrderGrouped = correctOrder.flatMap(x => incorrectOrderGrouped.find(_.key == x))
          val firstErrors: Form[models.TrusteeDetails] = new Form[TrusteeDetails](errors.mapping, errors.data, correctOrderGrouped, errors.value)
          Ok(views.html.trustee_details(groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT), index, firstErrors))
        } recover {
          case e: Exception => {
            Logger.error(s"showTrusteeDetailsSubmit: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
            getGlobalErrorPage
          }
        }
      },
      formData => {
        val scRef = request.schemeInfo.schemeRef
        cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, scRef).flatMap { cachedTrusteeList =>
          var cachedTrusteeListPlusNewTrustee = TrusteeDetailsList(List[TrusteeDetails]())
          var trusteeDetails: TrusteeDetails = TrusteeDetails("", "", Option(""), Option(""), Option(""), Option(""), Option(""))
          if (index == 10000) {
            cachedTrusteeListPlusNewTrustee = TrusteeDetailsList(cachedTrusteeList.trustees :+ formData)
          } else {
            for ((trustee, id) <- cachedTrusteeList.trustees.zipWithIndex) {
              if (index == id) {
                // Update trustee details
                trusteeDetails = TrusteeDetails(
                  (formData.name), //trustee Name
                  (formData.addressLine1), //Address line 1
                  (formData.addressLine2), //Address line 2
                  (formData.addressLine3), //Address line 3
                  (formData.addressLine4), //Address line 4
                  (formData.country), //Country
                  (formData.postcode) //Postcode
                )
              } else {
                trusteeDetails = TrusteeDetails(
                  (trustee.name), //trustee Name
                  (trustee.addressLine1), //Address line 1
                  (trustee.addressLine2), //Address line 2
                  (trustee.addressLine3), //Address line 3
                  (trustee.addressLine4), //Address line 4
                  (trustee.country), //Country
                  (trustee.postcode) //Postcode
                )
              }
              cachedTrusteeListPlusNewTrustee = TrusteeDetailsList(cachedTrusteeListPlusNewTrustee.trustees :+ trusteeDetails)
            }
          }
          cacheUtil.cache(CacheUtil.TRUSTEES_CACHE, cachedTrusteeListPlusNewTrustee, scRef).map { all =>
            Redirect(routes.TrusteeController.trusteeSummaryPage())
          }

        } recoverWith {
          case e: NoSuchElementException => {
            val trusteeList = TrusteeDetailsList(List(formData))
            cacheUtil.cache(CacheUtil.TRUSTEES_CACHE, trusteeList, scRef).map {
              all => Redirect(routes.TrusteeController.trusteeSummaryPage())
            }
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

  def showDeleteTrustee(id: Int)(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val scRef = request.schemeInfo.schemeRef
    cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, scRef).flatMap { cachedTrusteeList =>

      var trusteeDetailsList = List[TrusteeDetails]()
      for ((trustee, index) <- cachedTrusteeList.trustees.zipWithIndex) {
        if (index != id) {
          val trusteeDetails = TrusteeDetails(
            (trustee.name), //trustee Name
            (trustee.addressLine1), //Address line 1
            (trustee.addressLine2), //Address line 2
            (trustee.addressLine3), //Address line 3
            (trustee.addressLine4), //Address line 4
            (trustee.country), //Country
            (trustee.postcode) //Postcode
          )
          trusteeDetailsList = trusteeDetailsList :+ trusteeDetails
        }
      }

      cacheUtil.cache(CacheUtil.TRUSTEES_CACHE, TrusteeDetailsList(trusteeDetailsList), scRef).map { all =>
        Redirect(routes.TrusteeController.trusteeSummaryPage())
      }

    } recover {
      case e: NoSuchElementException => getGlobalErrorPage
    }
  }

  def editTrustee(id: Int): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showEditTrustee(id)(user, request, hc)
  }

  def showEditTrustee(id: Int)(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    val scRef = request.schemeInfo.schemeRef

    cacheUtil.fetch[GroupSchemeInfo](CacheUtil.GROUP_SCHEME_CACHE_CONTROLLER, scRef).flatMap { groupSchemeActivity =>
      cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, scRef).map { tdc =>
        val trustees: TrusteeDetailsList = tdc
        var trusteeDetails: TrusteeDetails = TrusteeDetails("", "", Option(""), Option(""), Option(""), Option(""), Option(""))
        for ((trustee, index) <- trustees.trustees.zipWithIndex) {
          if (index == id) {
            trusteeDetails = TrusteeDetails(
              (trustee.name), //trustee Name
              (trustee.addressLine1), //Address line 1
              (trustee.addressLine2), //Address line 2
              (trustee.addressLine3), //Address line 3
              (trustee.addressLine4), //Address line 4
              (trustee.country), //Country
              (trustee.postcode) //Postcode
            )
          }
        }
        Ok(views.html.trustee_details(groupSchemeActivity.groupScheme.getOrElse(PageBuilder.DEFAULT), id, RsFormMappings.trusteeDetailsForm.fill(trusteeDetails)))
      } recover {
        case e: Exception => {
          Logger.error(s"showEditTrustee: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
          getGlobalErrorPage
        }
      }
    } recover {
      case e: Exception => {
        Logger.error(s"showEditTrustee: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def trusteeSummaryPage(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        showTrusteeSummaryPage()(user, request, hc)
  }

  def showTrusteeSummaryPage()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    cacheUtil.fetch[TrusteeDetailsList](CacheUtil.TRUSTEES_CACHE, request.schemeInfo.schemeRef).map { trusteeDetailsList =>
      Ok(views.html.trustee_summary(trusteeDetailsList))
    } recover {
      case e: Exception => {
        Logger.error(s"showTrusteeSummaryPage: Get data from cache failed with exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
        getGlobalErrorPage
      }
    }
  }

  def trusteeSummaryContinue(): Action[AnyContent] = AuthorisedForAsync() {
    implicit user =>
      implicit request =>
        continueFromTrusteeSummaryPage()(user, request, hc)
  }

  def continueFromTrusteeSummaryPage()(implicit authContext: AuthContext, request: RequestWithSchemeRef[AnyRef], hc: HeaderCarrier): Future[Result] = {
    Future(Redirect(routes.AltAmendsController.altActivityPage()))
  }

  def getGlobalErrorPage(implicit messages: Messages) = Ok(views.html.global_error(
    messages("ers.global_errors.title"),
    messages("ers.global_errors.heading"),
    messages("ers.global_errors.message"))(messages))

}

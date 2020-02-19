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

import config.ERSFileValidatorAuthConnector
import models.CallbackData
import play.api.libs.json.JsValue
import play.api.mvc.Action
import play.api.{Configuration, Logger, Play}
import services.SessionService
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.CacheUtil
import uk.gov.hmrc.http.logging.SessionId

trait FileUploadCallbackController extends FrontendController with ErsConstants {

  val cacheUtil: CacheUtil
  val currentConfig: Configuration
  val sessionService: SessionService

  def callback(): Action[JsValue] = Action.async(parse.json) {
    implicit request => {

      Logger.info("Attachments Callback: " + (System.currentTimeMillis() / 1000))

      val callbackData: CallbackData = request.body.as[CallbackData]

      val headerCarrier = callbackData.sessionId match {
        case Some(sid) => hc.copy(sessionId = Some(SessionId((sid))))
        case _ => hc
      }

      sessionService.storeCallbackData(callbackData)(request, headerCarrier).map {
        case callback: Option[CallbackData] if callback.isDefined => Ok("")
        case _ => Logger.error(s"storeCallbackData failed with Exception , timestamp: ${System.currentTimeMillis()}.")
          InternalServerError("Exception ")
      }.recover {
        case e: Throwable => Logger.error(s"storeCallbackData failed with Exception ${e.getMessage}, timestamp: ${System.currentTimeMillis()}.")
          InternalServerError("Exception occurred when attempting to store data")
      }
    }
  }
}

object FileUploadCallbackController extends FileUploadCallbackController {
  val authConnector = ERSFileValidatorAuthConnector
  val currentConfig: Configuration = Play.current.configuration
  val sessionService = SessionService

  override val cacheUtil: CacheUtil = CacheUtil
}

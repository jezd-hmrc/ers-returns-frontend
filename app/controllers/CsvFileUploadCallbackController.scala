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

import akka.actor.ActorSystem
import config.{ApplicationConfig, ERSFileValidatorAuthConnector}
import models.upscan._
import play.api.libs.json.JsValue
import play.api.mvc.Action
import play.api.{Logger, Play}
import services.SessionService
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.CacheUtil

import scala.concurrent.Future
import scala.util.control.NonFatal

trait CsvFileUploadCallbackController extends FrontendController with ErsConstants {
  val cacheUtil: CacheUtil
  val appConfig: ApplicationConfig
  implicit val actorSystem: ActorSystem = Play.current.actorSystem
  private val logger = Logger(this.getClass)

  def callback(uploadId: UploadId, scRef: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      request.body.validate[UpscanCallback].fold (
        invalid = errors => {
          logger.error(s"Failed to validate UpscanCallback json with errors: $errors")
          Future.successful(BadRequest)
        },
        valid = callback => {
          val uploadStatus: UploadStatus = callback match {
            case callback: UpscanReadyCallback =>
              UploadedSuccessfully(callback.uploadDetails.fileName, callback.downloadUrl.toExternalForm)
            case UpscanFailedCallback(_, details) =>
              logger.warn(s"CSV Callback for upload id: ${uploadId.value} failed. Reason: ${details.failureReason}. Message: ${details.message}")
              Failed
          }
          logger.info(s"Updating CSV callback for upload id: ${uploadId.value} to ${uploadStatus.getClass.getSimpleName}")
          cacheUtil.cache(s"${CacheUtil.CHECK_CSV_FILES}-${uploadId.value}", uploadStatus, scRef).map {
            _ => Ok
          } recover {
            case NonFatal(e) =>
              logger.error(s"Failed to update cache after Upscan callback for UploadID: ${uploadId.value}, ScRef: $scRef", e)
              InternalServerError("Exception occurred when attempting to store data")
          }
        }
      )
  }
}

object CsvFileUploadCallbackController extends CsvFileUploadCallbackController {
  val authConnector = ERSFileValidatorAuthConnector
  val sessionService = SessionService
  override val appConfig: ApplicationConfig = ApplicationConfig
  override val cacheUtil: CacheUtil = CacheUtil
}

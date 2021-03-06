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

import models.upscan._
import play.api.Logger
import play.api.libs.json.{JsResult, JsValue}
import play.api.mvc.Action
import services.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FileUploadCallbackController extends FrontendController with ErsConstants {

  val sessionService: SessionService
  private val logger = Logger(this.getClass)

  def callback(sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    implicit val headerCarrier: HeaderCarrier = hc.copy(sessionId = Some(SessionId(sessionId)))
    request.body.validate[UpscanCallback].fold (
      invalid = errors => {
        logger.error(s"Failed to validate UpscanCallback json with errors: $errors")
        Future.successful(BadRequest)
      },
      valid = callback => {
        val uploadStatus = callback match {
          case callback: UpscanReadyCallback =>
            UploadedSuccessfully(callback.uploadDetails.fileName, callback.downloadUrl.toExternalForm)
          case UpscanFailedCallback(_, details) =>
            logger.warn(s"Callback for session id: $sessionId failed. Reason: ${details.failureReason}. Message: ${details.message}")
            Failed
        }
        logger.info(s"Updating callback for session: $sessionId to ${uploadStatus.getClass.getSimpleName}")
        sessionService.updateCallbackRecord(sessionId, uploadStatus)(request, headerCarrier).map(_ => Ok) recover {
          case e: Throwable =>
            logger.error(s"Failed to upadte callback record for session: $sessionId, timestamp: ${System.currentTimeMillis()}.", e)
            InternalServerError("Exception occurred when attempting to update callback data")
        }
      }
    )
  }
}

object FileUploadCallbackController extends FileUploadCallbackController {
  val sessionService = SessionService
}

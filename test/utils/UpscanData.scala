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

package utils

import java.net.URL
import java.time.Instant

import models.upscan.{ErrorDetails, Failed, InProgress, NotStarted, Reference, UploadDetails, UploadId, UploadedSuccessfully, UpscanCsvFilesCallback, UpscanCsvFilesCallbackList, UpscanFailedCallback, UpscanInitiateResponse, UpscanReadyCallback}
import play.api.libs.json.{JsObject, JsString, JsValue, Json, OWrites}

trait UpscanData {

  val testUploadId: UploadId = UploadId("TestUploadId")

  val incompleteCsvList = UpscanCsvFilesCallbackList(
    List(
      UpscanCsvFilesCallback(UploadId("ID1"), "file1", InProgress),
      UpscanCsvFilesCallback(testUploadId, "file4", NotStarted)
    )
  )
  val notStartedCsvList = UpscanCsvFilesCallbackList(
    List(
      UpscanCsvFilesCallback(testUploadId, "file1", NotStarted),
      UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
    )
  )
  val incompleteCsvList2 = UpscanCsvFilesCallbackList(
    List(
      UpscanCsvFilesCallback(testUploadId, "file1", InProgress),
      UpscanCsvFilesCallback(UploadId("ID2"), "file4", NotStarted)
    )
  )
  val failedCsvList = UpscanCsvFilesCallbackList(
    List(
      UpscanCsvFilesCallback(testUploadId, "file1", Failed),
      UpscanCsvFilesCallback(UploadId("ID2"), "file4", Failed)
    )
  )

  val uploadDetails = UploadDetails(Instant.now(), "checksum", "fileMimeType", "fileName")
  val readyCallback = UpscanReadyCallback(Reference("Reference"), new URL("https://callbackUrl.com"), uploadDetails)
  val failedCallback = UpscanFailedCallback(Reference("Reference"), ErrorDetails("failureReason", "message"))

  import models.upscan.UpscanCallback._
  implicit val failedWrites: OWrites[UpscanFailedCallback] = Json.writes[UpscanFailedCallback]
    .transform((js: JsValue) => js.as[JsObject] + ("fileStatus" -> JsString("FAILED")))
  implicit val readWrites: OWrites[UpscanReadyCallback] =
    Json.writes[UpscanReadyCallback].transform((js: JsValue) => js.as[JsObject] + ("fileStatus" -> JsString("READY")))

  val uploadedSuccessfully = UploadedSuccessfully("fileName", "https://downloadUrl.com")

  val upscanInitiateResponse: UpscanInitiateResponse =
    UpscanInitiateResponse(Reference("reference"), "postTarget", formFields = Map.empty[String, String])
}

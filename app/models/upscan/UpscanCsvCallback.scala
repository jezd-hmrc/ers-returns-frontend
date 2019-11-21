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

package models.upscan

import play.api.libs.json.{Format, Json}

case class UpscanCsvFilesCallback(uploadId: UploadId, fileId: String, uploadStatus: UploadStatus = NotStarted) {
  def isStarted: Boolean = uploadStatus != NotStarted
  def isComplete: Boolean = uploadStatus match {
    case _: UploadedSuccessfully | Failed => true
    case _ => false
  }
}
object UpscanCsvFilesCallback {
  implicit val upscanCsvFileFormats: Format[UpscanCsvFilesCallback] = Json.format[UpscanCsvFilesCallback]
}

case class UpscanCsvFilesCallbackList(files: List[UpscanCsvFilesCallback]){
  def updateToInProgress(uploadId: UploadId): UpscanCsvFilesCallbackList = {
    updateUploadStatus(uploadId, InProgress, _ == NotStarted)
  }

  def findById(uploadId: UploadId): Option[UpscanCsvFilesCallback] = {
    files.find(_.uploadId == uploadId)
  }

  def updateUploadStatus(uploadId: UploadId, uploadStatus: UploadStatus, p: UploadStatus => Boolean = _ => true): UpscanCsvFilesCallbackList = {
    this.copy(files =
      files.map { file =>
        if(file.uploadId == uploadId && p(file.uploadStatus)) {
          file.copy(uploadStatus = uploadStatus)
        } else {
          file
        }
      }
    )
  }

  def areAllFilesComplete(): Boolean = files.forall(_.isComplete)

  def areAllFilesSuccessful(): Boolean = files.forall {
    _.uploadStatus.isInstanceOf[UploadedSuccessfully]
  }
}
object UpscanCsvFilesCallbackList {
  implicit val upscanCsvCallbackListFormat: Format[UpscanCsvFilesCallbackList] =
    Json.format[UpscanCsvFilesCallbackList]
}
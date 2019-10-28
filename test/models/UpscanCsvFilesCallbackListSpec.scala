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

package models

import models.upscan.InProgress
import uk.gov.hmrc.play.test.UnitSpec
import utils.UpscanData

class UpscanCsvFilesCallbackListSpec extends UnitSpec with UpscanData {

  "updateUploadStatus" should {
    "only update file with matching uploadId" in {
      val res = notStartedCsvList.updateUploadStatus(testUploadId, InProgress)
      res shouldBe incompleteCsvList2
    }

    "only update if predicate resolves to true" in {
      val res = notStartedCsvList.updateUploadStatus(testUploadId, InProgress, _ => false)
      res shouldBe notStartedCsvList
    }
  }

  "updateToInProgress" should {
    "update status to InProgress" when {
      "status is NotStarted" in {
        val res = notStartedCsvList.updateToInProgress(testUploadId)
        res shouldBe incompleteCsvList2
      }
    }

    "not update record" when {
      "status code is not NotStarted" in {
        failedCsvList.updateToInProgress(testUploadId) shouldBe failedCsvList
      }
    }
  }
}

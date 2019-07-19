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

import models.ErsMetaData
import play.api.i18n.Messages

object ErsMetaDataHelper {
  def getScreenSchemeInfo(metaData: ErsMetaData)(implicit messages: Messages) = {
    val taxYear = getFullTaxYear(metaData.schemeInfo.taxYear)
    metaData.schemeInfo.schemeId + " - " +metaData.schemeInfo.schemeType + " - " + metaData.schemeInfo.schemeName + " - " + metaData.schemeInfo.schemeRef + " - " + taxYear
  }

  def getFullTaxYear(taxYear : String)(implicit messages: Messages) : String = {
    taxYear.take(4) + Messages("ers.taxYear.text") + taxYear.take(2) + taxYear.takeRight(2)
  }

  def rewriteSchemeInfo(schemeInfo: String)(implicit messages: Messages): String = {
      schemeInfo.replaceAll("([0-9]{4}) (to|i) ([0-9]{4})", "$1" + Messages("ers.taxYear.text") + "$3")
  }
}

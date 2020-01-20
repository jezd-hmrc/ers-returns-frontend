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

package services.audit

import models.{ErsMetaData}
import org.apache.commons.lang3.exception.ExceptionUtils
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier


object AuditEvents extends AuditEvents {
  override def auditService : AuditService = AuditService
}

trait AuditEvents {
  def auditService: AuditService

  def auditRunTimeError(exception : Throwable, contextInfo : String, rsc: ErsMetaData, bundle : String) (implicit request: Request[_], hc: HeaderCarrier) : Unit = {
    auditService.sendEvent("RunTimeError",
      Map("ErrorMessage" -> exception.getMessage,
        "Context" -> contextInfo,
        "ReturnServiceCache" -> eventMap(rsc, bundle).toString,
        "StackTrace" -> ExceptionUtils.getStackTrace(exception)))
  }

  def ErsSubmissionAuditEvent(rsc : ErsMetaData, bundle : String)(implicit request: Request[_], hc: HeaderCarrier): Boolean = {

    auditService.sendEvent("ErsReturnsFrontendSubmission", eventMap(rsc, bundle))
    true

  }

  def eventMap(rsc : ErsMetaData, bundle : String): Map[String,String] = {
    Map(
      "schemeRef" -> rsc.schemeInfo.schemeRef.toString,
      "schemeId" -> rsc.schemeInfo.schemeId.toString,
      "taxYear" -> rsc.schemeInfo.taxYear.toString,
      "schemeName" -> rsc.schemeInfo.schemeName.toString,
      "schemeType" -> rsc.schemeInfo.schemeType.toString,
      "aoRef" -> rsc.aoRef.getOrElse(""),
      "empRef" -> rsc.empRef.toString,
      "agentRef" -> rsc.agentRef.getOrElse(""),
      "sapNumber" -> rsc.sapNumber.getOrElse(""),
      "bundleRed" -> bundle
    )
  }

}

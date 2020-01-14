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

import java.util.UUID

import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{EmpRef, SaUtr, Vrn}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain._

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, SessionKeys }

trait ERSUsers {

  implicit val mockAuthConnector: AuthConnector

  def withAuthorisedUser(test: FakeRequest[AnyContentAsEmpty.type] => Any) {
    val userId = s"user-${UUID.randomUUID}"
    when(mockAuthConnector.currentAuthority(Matchers.any[HeaderCarrier](), Matchers.any())) thenReturn {
      Future.successful(Some(Authority(userId, Accounts(sa = Some(SaAccount("", SaUtr("1234567890"))), vat = Some(VatAccount("", Vrn("123456789"))), epaye = Some(EpayeAccount("", EmpRef("000", "AA00000")))), None, None, CredentialStrength.Strong, ConfidenceLevel.L50, None, None, None, "")))
    }
    val sessionId = s"session-${UUID.randomUUID}"
    lazy val request = FakeRequest().withSession(
      SessionKeys.sessionId -> sessionId,
      SessionKeys.token -> "RANDOMTOKEN",
      SessionKeys.userId -> userId,
      "screenSchemeInfo" -> "2 - EMI - MYScheme - XX12345678 - 2016")
    test(request)
  }

}

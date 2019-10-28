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

package controllers.auth

import java.util.UUID

import akka.stream.Materializer
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.ERSFakeApplicationConfig

import scala.concurrent.Future
import uk.gov.hmrc.http.SessionKeys

class ErsRegimeSpec extends PlaySpec with OneServerPerSuite with ERSFakeApplicationConfig with MockitoSugar with BeforeAndAfterEach {

  override implicit lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  private val orgAccount = Some(OrgAccount("org/1234", Org("1234")))
  private val saAccount = Some(SaAccount("", SaUtr("1234567890")))
  private val vatAccount = Some(VatAccount("", Vrn("123456789")))
  private val epayeAccount = Some(EpayeAccount("", EmpRef("000", "AA00000")))
  private val authority = Authority("", Accounts(), None, None, CredentialStrength.Strong, ConfidenceLevel.L50, None, None, None, "")
  private val randomToken = "RANDOMTOKEN"


  "ERSRegime" must {
    "define isAuthorised" must {
      lazy val accounts = mock[Accounts](RETURNS_DEEP_STUBS)
      "return true when epaye is defined" in {
        when(accounts.epaye.isDefined).thenReturn(true)
        ERSRegime.isAuthorised(accounts) must be(true)
      }

      "return false when epaye is not defined" in {
        when(accounts.epaye.isDefined).thenReturn(false)
        ERSRegime.isAuthorised(accounts) must be(false)
      }
    }
    "define the authentication type as the epaye GG" in {
      ERSRegime.authenticationType must be(ErsFileValidatorGovernmentGateway)
    }

    "define the unauthorised landing page as /unauthorised" in {
      ERSRegime.unauthorisedLandingPage.get must be("/submit-your-ers-annual-return/unauthorised")
    }
  }


  "when applying the ERSRegime" must {
    lazy val mockAuthConnector = mock[AuthConnector]
    object TestController extends FrontendController with Actions {
      lazy val authConnector: AuthConnector = mockAuthConnector

      def testRoute: Action[AnyContent] = AuthorisedFor(ERSRegime, pageVisibility = GGConfidence) {
        implicit user =>
          implicit request =>
            Ok
      }
    }

    "logged-in users" must {
      lazy val userId = s"user-${UUID.randomUUID}"
      lazy val sessionId = s"session-${UUID.randomUUID}"
      lazy val request = FakeRequest().withSession(SessionKeys.sessionId -> sessionId, SessionKeys.token -> randomToken, SessionKeys.userId -> userId)
      "with a psa account" must {
        "allow the user access to the page" in {
          lazy val newAuthority = authority.copy(uri = userId, accounts = Accounts(sa = saAccount, vat = vatAccount, epaye = epayeAccount))
          when(mockAuthConnector.currentAuthority(Matchers.any(),Matchers.any())) thenReturn Future.successful(Some(newAuthority))
          lazy val result = TestController.testRoute.apply(request)
          status(result) must be(OK)
        }
      }

      "without a psa account" must {
        "redirect the user to the unauthorised page" in {
          lazy val newAuthority = authority.copy(uri = userId, accounts = Accounts(org = orgAccount))
          when(mockAuthConnector.currentAuthority(Matchers.any(),Matchers.any())) thenReturn Future.successful(Some(newAuthority))
          lazy val result = TestController.testRoute.apply(request)
          status(result) must be(SEE_OTHER)
          redirectLocation(result) must be(Some("/submit-your-ers-annual-return/unauthorised"))
        }
      }
    }

    "not logged-in users" must {
      "redirect to the gg sign-in page" in {
        lazy val result = TestController.testRoute.apply(FakeRequest())
        status(result) must be(SEE_OTHER)
        redirectLocation(result).get must include("/gg/sign-in")
      }
    }
  }
}

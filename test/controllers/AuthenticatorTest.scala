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

package controllers

import org.scalatest.EitherValues
import org.scalatestplus.play.OneAppPerSuite
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, Authority, ConfidenceLevel, CredentialStrength}
import uk.gov.hmrc.play.test.UnitSpec
import utils.{ERSFakeApplicationConfig, Fixtures}

import scala.concurrent.Future

class AuthenticatorTest extends UnitSpec with OneAppPerSuite with ERSFakeApplicationConfig with EitherValues  {


  class SUT extends Authenticator {

    def body: AuthContext => RequestWithSchemeRef[AnyContent] => Future[Result] = {
      implicit a: AuthContext =>
        implicit r: RequestWithSchemeRef[AnyContent] =>
          Future.successful(Ok(""))
    }
    override protected def authConnector: AuthConnector = ???
  }

    "FilterForSchemeRef " should {
      val authContext = AuthContext(Authority("", Accounts(), None, None, CredentialStrength.Strong, ConfidenceLevel.L50, None, None, None, ""))

      "redirect to portal" when {
        "session has dropped" in new SUT {
          val eitherResult: Either[Future[Result], RequestWithSchemeRef[AnyContent]] = FilterForSchemeRef(body, FakeRequest(), authContext)
          val result: Future[Result] = eitherResult.left.value

          status(result) shouldBe 303
          redirectLocation(result) shouldBe Some(routes.AuthorizationController.timedOut().url)
        }
      }

      "continue to body" when {
        "scheme ref is present in the session" in new SUT {
          val request = Fixtures.buildFakeRequestWithSessionId()
          val eitherResult: Either[Future[Result], RequestWithSchemeRef[AnyContent]] = FilterForSchemeRef(body, request, authContext)
          val result: RequestWithSchemeRef[AnyContent] = eitherResult.right.value

          result.schemeInfo.schemeRef shouldBe "XX12345678"
        }
      }
    }
}

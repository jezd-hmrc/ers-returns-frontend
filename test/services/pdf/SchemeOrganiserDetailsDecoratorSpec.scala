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

package services.pdf

import models.SchemeOrganiserDetails
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.internal.verification.VerificationModeFactory
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import play.api.i18n.Messages

class SchemeOrganiserDetailsDecoratorSpec extends UnitSpec with MockitoSugar {

  implicit val messages = mock[Messages]

  "Company details title decorator" should {

    "add company details to the ers stream" in {
      val streamer = mock[ErsContentsStreamer]
      val schemeOrganiser: SchemeOrganiserDetails = SchemeOrganiserDetails(
        "companyName",
        "addressLine1",
        Some("addressLine2"),
        Some("addressLine3"),
        Some("addressLine4"),
        None,
        Some("post code"),
        Some("company reg"),
        Some("corporationRef")
      )

      val decorator = new SchemeOrganiserDetailseDecorator("title", schemeOrganiser, 1.0F, 2.0F, 3.0F, 4.0F)

      decorator.decorate(streamer)

      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("title": String), org.mockito.Matchers.eq(1.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("companyName": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("addressLine1": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("addressLine2": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("addressLine3": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("addressLine4": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("post code": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("company reg": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("corporationRef": String), org.mockito.Matchers.eq(2.0F: Float))(Matchers.any())

      verify(streamer, VerificationModeFactory.times(8)).drawText(org.mockito.Matchers.eq("": String), org.mockito.Matchers.eq(3.0F: Float))(Matchers.any())
     }

    "show block spacer at the end of the section" in {
      val streamer = mock[ErsContentsStreamer]
      val schemeOrganiser: SchemeOrganiserDetails = SchemeOrganiserDetails(
        "companyName",
        "addressLine1",
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )

      val decorator = new SchemeOrganiserDetailseDecorator("title", schemeOrganiser, 1.0F, 2.0F, 3.0F, 4.0F)

      decorator.decorate(streamer)

      verify(streamer, VerificationModeFactory.times(3)).drawText(org.mockito.Matchers.eq("": String), org.mockito.Matchers.eq(4.0F: Float))(Matchers.any())
      verify(streamer, VerificationModeFactory.times(1)).drawLine()
    }
  }
}

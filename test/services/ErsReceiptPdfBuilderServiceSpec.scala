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

package services

import java.io.ByteArrayOutputStream

import akka.stream.Materializer
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.internal.verification.VerificationModeFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import services.pdf.{DecoratorController, ErsContentsStreamer, ErsReceiptPdfBuilderService}
import uk.gov.hmrc.play.test.UnitSpec
import utils.{ContentUtil, ERSFakeApplicationConfig, Fixtures}

class ErsReceiptPdfBuilderServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ERSFakeApplicationConfig with OneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer
  implicit lazy val messages: Messages = Messages(Lang("en"), app.injector.instanceOf[MessagesApi])

  def verifyBlankBlock(streamer: ErsContentsStreamer) {
    verify(streamer, VerificationModeFactory.times(4)).drawText(org.mockito.Matchers.eq("": String), org.mockito.Matchers.eq(36.0F: Float))
  }

  def verifyBlankLine(streamer: ErsContentsStreamer) {
    verify(streamer, VerificationModeFactory.times(4)).drawText(org.mockito.Matchers.eq("": String), org.mockito.Matchers.eq(12.0F: Float))
  }

  "ErsReceiptPdfBuilderService" should {
    "ask the streamer to add ers summary metadata to the output pdf" in {

      implicit val streamer = mock[ErsContentsStreamer]
      implicit val decorator = mock[DecoratorController]

      when(streamer.drawText(anyString(), anyInt())).thenReturn(true)
      when(streamer.saveErsSummary()).thenReturn(new ByteArrayOutputStream)
      when(streamer.savePageContent()).thenReturn(true)
      when(streamer.createNewPage()).thenReturn(true)

      ErsReceiptPdfBuilderService.addMetaData(Fixtures.ersSummary, "8 August 2016, 4:28pm")

      val expectedConfirmationMessage = s"Your ${ContentUtil.getSchemeAbbreviation("emi")} " +
        s"annual return has been submitted for tax year 2014 to 2015."
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq(expectedConfirmationMessage: String), org.mockito.Matchers.eq(16.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("Scheme name:": String), org.mockito.Matchers.eq(16.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("My scheme": String), org.mockito.Matchers.eq(12.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("Reference code:": String), org.mockito.Matchers.eq(16.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("testbundle": String), org.mockito.Matchers.eq(12.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("Date and time submitted:": String), org.mockito.Matchers.eq(16.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("8 August 2016, 4:28pm": String), org.mockito.Matchers.eq(12.0F: Float))

      verifyBlankBlock(streamer)
      verifyBlankLine(streamer)
    }

    "ask the streamer to save the metadata to the output pdf" in {
      implicit val streamer = mock[ErsContentsStreamer]
      implicit val decorator = mock[DecoratorController]

      when(streamer.saveErsSummary()).thenReturn(new ByteArrayOutputStream)
      when(streamer.savePageContent()).thenReturn(true)
      when(streamer.createNewPage()).thenReturn(true)

      ErsReceiptPdfBuilderService.addMetaData(Fixtures.ersSummary, "8 August 2016, 4:28pm")
      verify(streamer, VerificationModeFactory.times(1)).savePageContent
    }
  }
}

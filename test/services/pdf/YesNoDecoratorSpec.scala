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

package services.pdf

import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.internal.verification.VerificationModeFactory

class YesNoDecoratorSpec extends UnitSpec with MockitoSugar{

  "nil returns decorator" should {
    "show Yes if there is nil return" in {
      val streamer = mock[ErsContentsStreamer]
      val decorator = new YesNoDecorator("title", "1", 1.0f, 2.0F, 3.0F, 4.0F)

      decorator.decorate(streamer)

      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("title": String), org.mockito.Matchers.eq(1.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("Yes": String), org.mockito.Matchers.eq(2.0F: Float))
    }

    "show No if there is no nil return" in {
      val streamer = mock[ErsContentsStreamer]
      val decorator = new YesNoDecorator("title", "2", 1.0f, 2.0F, 3.0F, 4.0F)

      decorator.decorate(streamer)

      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("title": String), org.mockito.Matchers.eq(1.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("No": String), org.mockito.Matchers.eq(2.0F: Float))
    }

    "show section divider after the block is rendered" in {
      val streamer = mock[ErsContentsStreamer]
      val decorator = new YesNoDecorator("title", "2", 1.0f, 2.0F, 3.0F, 4.0F)

      decorator.decorate(streamer)

      verify(streamer, VerificationModeFactory.times(1)).drawText(org.mockito.Matchers.eq("": String), org.mockito.Matchers.eq(3.0F: Float))
      verify(streamer, VerificationModeFactory.times(2)).drawText(org.mockito.Matchers.eq("": String), org.mockito.Matchers.eq(4.0F: Float))
      verify(streamer, VerificationModeFactory.times(1)).drawLine()
    }
   }

}

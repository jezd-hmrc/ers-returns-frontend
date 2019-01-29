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

import uk.gov.hmrc.play.test.UnitSpec
import utils.Fixtures

class ApachePdfContentsStreamerSpec extends UnitSpec {

  val ersSummary = Fixtures.ersSummary
  val streamer = new ApachePdfContentsStreamer(ersSummary)

  "Apache Pdf Contents Streamer" should {


    "return false if contents can not be saved" in {
      streamer.savePageContent() shouldEqual false
    }

     "return saved given ErsSummary as ByteStream" in {
      val output = streamer.saveErsSummary().size() shouldEqual (3903)
    }

    "return true if page is created successfully" in {
      val streamer = new ApachePdfContentsStreamer(ersSummary)

      streamer.createNewPage() shouldEqual true
    }

     "return true if text is drawn successfully" in {
      val streamer = new ApachePdfContentsStreamer(ersSummary)
       streamer.createNewPage()

      streamer.drawText("blah", 1.0F) shouldEqual true
    }

    "return true if text that span more that one line is drawn successfully" in {
      val streamer = new ApachePdfContentsStreamer(ersSummary)
       streamer.createNewPage()
      streamer.drawText("text text text text text text text text text text text text ", 40.0F) shouldEqual true
    }

    "return true if line is drawn successfully" in {
      val streamer = new ApachePdfContentsStreamer(ersSummary)
       streamer.createNewPage()
      streamer.drawLine() shouldEqual true
    }


    "return true if contents is saved successfully" in {
      val streamer = new ApachePdfContentsStreamer(ersSummary)
       streamer.createNewPage()
      streamer.savePageContent() shouldEqual true
    }

 }
}

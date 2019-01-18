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

import models.TrusteeDetailsList
import play.api.Play.current
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._

class TrusteesDecorator(trusteesList: Option[TrusteeDetailsList], headingFontSize: Float, answerFontSize: Float, lineSpacer: Float, blockSpacer: Float) extends Decorator {

  def decorate(streamer: ErsContentsStreamer): Unit = {

    if (!trusteesList.isDefined)
      return

    streamer.drawText("", lineSpacer)
    streamer.drawText(Messages("ers_trustee_summary.title"), headingFontSize)
    streamer.drawText("", lineSpacer)

    for (trustee <- trusteesList.get.trustees) {
      streamer.drawText(trustee.name, answerFontSize)
      streamer.drawText("", lineSpacer)
    }

    streamer.drawText("", blockSpacer)
    streamer.drawLine()
    streamer.drawText("", blockSpacer)
  }
}

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

import java.io.ByteArrayOutputStream

import models.ErsSummary
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import utils.{ContentUtil, ErsMetaDataHelper}

import scala.collection.mutable.ListBuffer

object ErsReceiptPdfBuilderService extends ErsReceiptPdfBuilderService {
}

trait ErsReceiptPdfBuilderService {

  def createPdf(contentStreamer: ErsContentsStreamer, ersSummary: ErsSummary, filesUpoladed: Option[ListBuffer[String]], dateSubmitted: String): ByteArrayOutputStream = {
    implicit val streamer : ErsContentsStreamer = contentStreamer
    implicit val decorator = PdfDecoratorControllerFactory.createPdfDecoratorControllerForScheme(ersSummary.metaData.schemeInfo.schemeType, ersSummary, filesUpoladed)

    addMetaData(ersSummary, dateSubmitted)
    addSummary(ersSummary, filesUpoladed)
    streamer.saveErsSummary()
  }

  def addMetaData(ersSummary : ErsSummary, dateSubmitted: String)(implicit streamer: ErsContentsStreamer, decorator: DecoratorController): Unit = {

    val headingFontSize = 16
    val answerFontSize = 12
    val lineSpacer = 12
    val blockSpacer = 36

    val ersMetaData = ersSummary.metaData

    val pos = streamer.createNewPage

    Logger.info("Adding metadata")
    streamer.drawText("", blockSpacer)

    val confirmationMessage = s"Your ${ContentUtil.getSchemeAbbreviation(ersMetaData.schemeInfo.schemeType)} " +
                              s"annual return has been submitted for tax year " +
                              s"${ErsMetaDataHelper.getFullTaxYear(ersSummary.metaData.schemeInfo.taxYear)}."

    streamer.drawText(confirmationMessage, headingFontSize)
    streamer.drawText("", lineSpacer)

    streamer.drawText("", blockSpacer)
    streamer.drawText("Scheme name:", headingFontSize)
    streamer.drawText("", lineSpacer)
    streamer.drawText(ersMetaData.schemeInfo.schemeName, answerFontSize)

    streamer.drawText("", blockSpacer)
    streamer.drawText("Reference code:", headingFontSize)
    streamer.drawText("", lineSpacer)
    streamer.drawText(ersSummary.bundleRef, answerFontSize)

    streamer.drawText("", blockSpacer)
    streamer.drawText("Date and time submitted:", headingFontSize)
    streamer.drawText("", lineSpacer)
    streamer.drawText(dateSubmitted, answerFontSize)

    Logger.info("Save page content")
    streamer.savePageContent
  }

  private def addSummary(ersSummary: ErsSummary, filesUploaded: Option[ListBuffer[String]])(implicit streamer: ErsContentsStreamer, decorator: DecoratorController): Unit = {
    val blockSpacer = 20

    Logger.info("Adding ERS Summary")

    val pos = streamer.createNewPage

    streamer.drawText("Summary information", 18)
    streamer.drawText("", blockSpacer)
    streamer.drawLine()
    streamer.drawText("", blockSpacer)

    decorator.decorate(streamer)

    Logger.info("Adding ERS Summary complete")

    streamer.savePageContent
  }
}

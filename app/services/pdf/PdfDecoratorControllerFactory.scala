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

import models.ErsSummary
import play.api.i18n.Messages
import utils.PageBuilder

import scala.collection.mutable.ListBuffer

trait PdfDecoratorControllerFactory  {
  def createPdfDecoratorControllerForScheme(scheme: String, ersSummary: ErsSummary, filesUploaded: Option[ListBuffer[String]])(implicit messages: Messages): DecoratorController
}

object PdfDecoratorControllerFactory extends PdfDecoratorControllerFactory {

  def createPdfDecoratorControllerForScheme(scheme: String, ersSummary: ErsSummary, filesUploaded: Option[ListBuffer[String]])(implicit messages: Messages): DecoratorController = {

    class DecoratorControllerImpl(val decorators: Array[Decorator]) extends DecoratorController {

      def addDecorator(decorator: Decorator): DecoratorController = new DecoratorControllerImpl(decorators :+ decorator)

      def decorate(streamer: ErsContentsStreamer): Unit = decorators.foreach(decorator => decorator.decorate(streamer))

      def getNumberOfDecorator: Int = decorators.length

      def getDecorators: Array[Decorator] = decorators
    }

    val lineSpacer = 10
    val blockSpacer = 20
    val headingFontSize = 16
    val answerFontSize = 12

    scheme.trim.toLowerCase match {
      case "emi" => new DecoratorControllerImpl(Array[Decorator]()).
        addDecorator(new YesNoDecorator(Messages("ers_choose.emi.question"), ersSummary.isNilReturn, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new FileNamesDecorator(ersSummary.isNilReturn, filesUploaded, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new SchemeOrganiserDetailseDecorator(Messages("ers_summary_declaration.emi.organiser"), ersSummary.schemeOrganiser.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_group_activity.emi.question"), ersSummary.groupService.get.groupScheme.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new GroupSummaryDecorator(Messages("ers_group_summary.emi.title"), ersSummary.companies, headingFontSize, answerFontSize, lineSpacer, blockSpacer))

      case "csop" => new DecoratorControllerImpl(Array[Decorator]()).
        addDecorator(new YesNoDecorator(Messages("ers_choose.csop.question"), ersSummary.isNilReturn, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new FileNamesDecorator(ersSummary.isNilReturn, filesUploaded, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new SchemeOrganiserDetailseDecorator(Messages("ers_summary_declaration.csop.organiser"), ersSummary.schemeOrganiser.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_group_activity.csop.question"), ersSummary.groupService.get.groupScheme.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new GroupSummaryDecorator(Messages("ers_group_summary.csop.title"), ersSummary.companies, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_alt_activity.csop.question"), ersSummary.altAmendsActivity.get.altActivity, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new AlterationsAmendsDecorator(createAltAmendOptionsFor(ersSummary, "csop"), headingFontSize, answerFontSize, lineSpacer, blockSpacer))

      case "sip" => new DecoratorControllerImpl(Array[Decorator]()).
        addDecorator(new YesNoDecorator(Messages("ers_choose.sip.question"), ersSummary.isNilReturn, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new FileNamesDecorator(ersSummary.isNilReturn, filesUploaded, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new SchemeOrganiserDetailseDecorator(Messages("ers_summary_declaration.sip.organiser"), ersSummary.schemeOrganiser.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_group_activity.sip.question"), ersSummary.groupService.get.groupScheme.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new GroupSummaryDecorator(Messages("ers_group_summary.sip.title"), ersSummary.companies, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new TrusteesDecorator(ersSummary.trustees, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_alt_activity.sip.question"), ersSummary.altAmendsActivity.get.altActivity, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new AlterationsAmendsDecorator(createAltAmendOptionsFor(ersSummary, "sip"), headingFontSize, answerFontSize, lineSpacer, blockSpacer))

      case "saye" => new DecoratorControllerImpl(Array[Decorator]()).
        addDecorator(new YesNoDecorator(Messages("ers_choose.saye.question"), ersSummary.isNilReturn, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new FileNamesDecorator(ersSummary.isNilReturn, filesUploaded, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new SchemeOrganiserDetailseDecorator(Messages("ers_summary_declaration.saye.organiser"), ersSummary.schemeOrganiser.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_group_activity.saye.question"), ersSummary.groupService.get.groupScheme.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_alt_activity.saye.question"), ersSummary.altAmendsActivity.get.altActivity, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new AlterationsAmendsDecorator(createAltAmendOptionsFor(ersSummary, "saye"), headingFontSize, answerFontSize, lineSpacer, blockSpacer))

      case "other" => new DecoratorControllerImpl(Array[Decorator]()).
        addDecorator(new YesNoDecorator(Messages("ers_choose.other.question"), ersSummary.isNilReturn, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new FileNamesDecorator(ersSummary.isNilReturn, filesUploaded, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new SchemeOrganiserDetailseDecorator(Messages("ers_summary_declaration.other.organiser"), ersSummary.schemeOrganiser.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new YesNoDecorator(Messages("ers_group_activity.other.question"), ersSummary.groupService.get.groupScheme.get, headingFontSize, answerFontSize, lineSpacer, blockSpacer)).
        addDecorator(new GroupSummaryDecorator(Messages("ers_group_summary.other.title"), ersSummary.companies, headingFontSize, answerFontSize, lineSpacer, blockSpacer))

      case _ => throw new IllegalArgumentException
    }
  }

  def createAltAmendOptionsFor(ersSummary: ErsSummary, variant: String)(implicit messages: Messages): Map[String, String] = {

    val map = scala.collection.mutable.HashMap.empty[String, String]

    if (ersSummary.altAmendsActivity.isDefined) {

      if (ersSummary.altAmendsActivity.get.altActivity == PageBuilder.OPTION_YES)
        map += ("title" -> Messages("ers_trustee_summary.altamends.section"))

      if (ersSummary.alterationAmends.isDefined) {

        if (ersSummary.alterationAmends.get.altAmendsTerms.isDefined)
          if (ersSummary.alterationAmends.get.altAmendsTerms.get == PageBuilder.OPTION_YES)
            map += ("option1" -> s"${Messages(s"ers_alt_amends.${variant}.option_1")}")

        if (ersSummary.alterationAmends.get.altAmendsEligibility.isDefined)
          if (ersSummary.alterationAmends.get.altAmendsEligibility.get == PageBuilder.OPTION_YES)
            map += ("option2" -> s"${Messages(s"ers_alt_amends.${variant}.option_2")}")

        if (ersSummary.alterationAmends.get.altAmendsExchange.isDefined)
          if (ersSummary.alterationAmends.get.altAmendsExchange.get == PageBuilder.OPTION_YES)
            map += ("option3" -> s"${Messages(s"ers_alt_amends.${variant}.option_3")}")

        if (ersSummary.alterationAmends.get.altAmendsVariations.isDefined)
          if (ersSummary.alterationAmends.get.altAmendsVariations.get == PageBuilder.OPTION_YES)
            map += ("option4" -> s"${Messages(s"ers_alt_amends.${variant}.option_4")}")

        if (ersSummary.alterationAmends.get.altAmendsOther.isDefined)
          if (ersSummary.alterationAmends.get.altAmendsOther.get == PageBuilder.OPTION_YES)
            map += ("option5" -> s"${Messages(s"ers_alt_amends.${variant}.option_5")}")
      }
    }
    map.toMap
  }
}

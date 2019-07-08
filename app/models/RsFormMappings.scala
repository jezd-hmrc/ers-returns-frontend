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

package models

import play.api.Play.current
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc.LegacyI18nSupport

object RsFormMappings {

  /*
  * scheme type Form definition.
  */
  val schemeForm = Form(mapping("scheme" -> text)(RS_scheme.apply)(RS_scheme.unapply))


  /*
   * activity type Form definition
   */
  def chooseForm()(implicit messages: Messages) = Form(mapping(reportableEventsFields.isNilReturn -> optional(text)
    .verifying(Messages("ers_choose.err.message"), _.nonEmpty)
    .verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so.getOrElse("1"),
      fieldValidationPatterns.yesNoRegPattern)))(ReportableEvents.apply)(ReportableEvents.unapply))

  /*
   * check file type Form definition
   */
  def checkFileTypeForm()(implicit messages: Messages)  = Form(mapping(
    checkFileTypeFileds.checkFileType ->
      optional(text).verifying(Messages("ers_check_file_type.err.message"), _.nonEmpty)
        .verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so.getOrElse("csv"), fieldValidationPatterns.csvOdsRegPattern))
  )(CheckFileType.apply)(CheckFileType.unapply))

  /*
   * Is a group scheme Form definition
   */
  def groupForm()(implicit messages: Messages): Form[RS_groupScheme] = Form(mapping("groupScheme" ->
    optional(text).verifying("required field", _.nonEmpty)
      .verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so.getOrElse("1"), fieldValidationPatterns.yesNoRegPattern))
  )(RS_groupScheme.apply)(RS_groupScheme.unapply))

  /*
  * Is a group scheme type Form definition
  */
  def groupTypeForm()(implicit messages: Messages)  = Form(mapping("groupSchemeType" -> text)(RS_groupSchemeType.apply)(RS_groupSchemeType.unapply))

  /*
   * Alterations Activity Form definition
   */
  def altActivityForm()(implicit messages: Messages)  = Form(mapping("altActivity" -> text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern)))(AltAmendsActivity.apply)(AltAmendsActivity.unapply))

  /*
   * Alterations Amends Form definition
   */
  def altAmendsForm()(implicit messages: Messages)  = Form(mapping(
    "altAmendsTerms" -> optional(text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern))),
    "altAmendsEligibility" -> optional(text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern))),
    "altAmendsExchange" -> optional(text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern))),
    "altAmendsVariations" -> optional(text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern))),
    "altAmendsOther" -> optional(text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern))))(AltAmends.apply)(AltAmends.unapply))

  /*
  * CSV file check
  */

  def csvFileCheckForm()(implicit messages: Messages)  = Form(mapping(
    "files" -> list(
      mapping(
        "fileId" -> text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.csvFileNameRegx)),
        "isSelected" -> optional(text.verifying("required field", _.nonEmpty).verifying(Messages("ers.invalidCharacters"), so => validInputCharacters(so, fieldValidationPatterns.yesNoRegPattern)))
      )(CsvFiles.apply)(CsvFiles.unapply)
    )
  )(CsvFilesList.apply)(CsvFilesList.unapply))

  /*
   * Trustee Details Form definition
   */
  def trusteeDetailsForm()(implicit messages: Messages)  = Form(mapping(
    trusteeDetailsFields.name -> text.verifying(Messages("ers_trustee_details.err.summary.name_required"), _.nonEmpty).verifying(Messages("ers_trustee_details.err.name"), so => checklength(so, "trusteeDetailsFields.name")).verifying(Messages("ers_trustee_details.err.invalidChars.name"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx)),
    trusteeDetailsFields.addressLine1 -> text.verifying(Messages("ers_trustee_details.err.summary.address_line1_required"), _.nonEmpty).verifying(Messages("ers_trustee_details.err.address_line1"), so => checklength(so, "trusteeDetailsFields.addressLine1")).verifying(Messages("ers_trustee_details.err.invalidChars.address_line1"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx)),
    trusteeDetailsFields.addressLine2 -> optional(text.verifying(Messages("ers_trustee_details.err.address_line2"), so => checklength(so.toString, "trusteeDetailsFields.addressLine2")).verifying(Messages("ers_trustee_details.err.invalidChars.address_line2"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    trusteeDetailsFields.addressLine3 -> optional(text.verifying(Messages("ers_trustee_details.err.address_line3"), so => checklength(so.toString, "trusteeDetailsFields.addressLine3")).verifying(Messages("ers_trustee_details.err.invalidChars.address_line3"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    trusteeDetailsFields.addressLine4 -> optional(text.verifying(Messages("ers_trustee_details.err.address_line4"), so => checklength(so.toString, "trusteeDetailsFields.addressLine4")).verifying(Messages("ers_trustee_details.err.invalidChars.address_line4"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    trusteeDetailsFields.country -> optional(text verifying pattern(fieldValidationPatterns.addresssRegx.r, error = Messages("ers_scheme_organiser.err.summary.invalid_country"))),
    trusteeDetailsFields.postcode -> optional(text)
      .transform((x: Option[String]) => x.map(_.toUpperCase()), (z: Option[String]) => z.map(_.toUpperCase()))
      .verifying(Messages("ers_trustee_details.err.postcode"), so => isValidPostcode(so))
  )(TrusteeDetails.apply)(TrusteeDetails.unapply))

  /*
   * Manual Company Details Form definition
   */
  def companyDetailsForm()(implicit messages: Messages)  = Form(mapping(
    companyDetailsFields.companyName -> text.verifying(Messages("ers_manual_company_details.err.summary.company_name_required"), _.nonEmpty).verifying(Messages("ers_manual_company_details.err.company_name"), so => checklength(so, "companyDetailsFields.companyName")).verifying(Messages("ers_manual_company_details.err.invalidChars.company_name"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx)),
    companyDetailsFields.addressLine1 -> text.verifying(Messages("ers_manual_company_details.err.summary.address_line1_required"), _.nonEmpty).verifying(Messages("ers_manual_company_details.err.address_line1"), so => checklength(so, "companyDetailsFields.addressLine1")).verifying(Messages("ers_manual_company_details.err.invalidChars.address_line1"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx)),
    companyDetailsFields.addressLine2 -> optional(text.verifying(Messages("ers_manual_company_details.err.address_line2"), so => checklength(so.toString, "companyDetailsFields.addressLine2")).verifying(Messages("ers_manual_company_details.err.invalidChars.address_line2"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    companyDetailsFields.addressLine3 -> optional(text.verifying(Messages("ers_manual_company_details.err.address_line3"), so => checklength(so.toString, "companyDetailsFields.addressLine3")).verifying(Messages("ers_manual_company_details.err.invalidChars.address_line3"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    companyDetailsFields.addressLine4 -> optional(text.verifying(Messages("ers_manual_company_details.err.address_line4"), so => checklength(so.toString, "companyDetailsFields.addressLine4")).verifying(Messages("ers_manual_company_details.err.invalidChars.address_line4"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    companyDetailsFields.country -> optional(text verifying pattern(fieldValidationPatterns.addresssRegx.r, error = Messages("ers_scheme_organiser.err.summary.invalid_country"))),
    companyDetailsFields.postcode -> optional(text)
      .transform((x: Option[String]) => x.map(_.toUpperCase()), (z: Option[String]) => z.map(_.toUpperCase()))
      .verifying(Messages("ers_manual_company_details.err.postcode"), so => isValidPostcode(so)),
    companyDetailsFields.companyReg -> optional(text verifying pattern(fieldValidationPatterns.companyRegPattern.r, error = Messages("ers_manual_company_details.err.summary.company_reg_pattern"))),
    companyDetailsFields.corporationRef -> optional(text verifying pattern(fieldValidationPatterns.corporationRefPattern.r, error = Messages("ers_manual_company_details.err.summary.corporation_ref_pattern")))
  )(CompanyDetails.apply)(CompanyDetails.unapply))

  /*
   * Scheme Organiser Form definition
   */
  def schemeOrganiserForm()(implicit messages: Messages)  = Form(mapping(
    schemeOrganiserFields.companyName -> text.verifying(Messages("ers_scheme_organiser.err.summary.company_name_required"), _.nonEmpty).verifying(Messages("ers_scheme_organiser.err.company_name"), so => checklength(so, "schemeOrganiserFields.companyName")).verifying(Messages("ers_scheme_organiser.err.invalidChars.company_name"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx)),
    schemeOrganiserFields.addressLine1 -> text.verifying(Messages("ers_scheme_organiser.err.summary.address_line1_required"), _.nonEmpty).verifying(Messages("ers_scheme_organiser.err.address_line1"), so => checklength(so, "schemeOrganiserFields.addressLine1")).verifying(Messages("ers_scheme_organiser.err.invalidChars.address_line1"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx)),
    schemeOrganiserFields.addressLine2 -> optional(text.verifying(Messages("ers_scheme_organiser.err.address_line2"), so => checklength(so.toString, "schemeOrganiserFields.addressLine2")).verifying(Messages("ers_scheme_organiser.err.invalidChars.address_line2"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    schemeOrganiserFields.addressLine3 -> optional(text.verifying(Messages("ers_scheme_organiser.err.address_line3"), so => checklength(so.toString, "schemeOrganiserFields.addressLine3")).verifying(Messages("ers_scheme_organiser.err.invalidChars.address_line3"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    schemeOrganiserFields.addressLine4 -> optional(text.verifying(Messages("ers_scheme_organiser.err.address_line4"), so => checklength(so.toString, "schemeOrganiserFields.addressLine4")).verifying(Messages("ers_scheme_organiser.err.invalidChars.address_line4"), so => validInputCharacters(so, fieldValidationPatterns.addresssRegx))),
    schemeOrganiserFields.country -> optional(text verifying pattern(fieldValidationPatterns.addresssRegx.r, error = Messages("ers_scheme_organiser.err.summary.invalid_country"))),
    schemeOrganiserFields.postcode -> optional(text)
      .transform((x: Option[String]) => x.map(_.toUpperCase()), (z: Option[String]) => z.map(_.toUpperCase()))
      .verifying(Messages("ers_scheme_organiser.err.postcode"), so => isValidPostcode(so)),
    schemeOrganiserFields.companyReg -> optional(text verifying pattern(fieldValidationPatterns.companyRegPattern.r, error = Messages("ers_scheme_organiser.err.summary.company_reg_pattern"))),
    schemeOrganiserFields.corporationRef -> optional(text verifying pattern(fieldValidationPatterns.corporationRefPattern.r, error = Messages("ers_scheme_organiser.err.summary.corporation_ref_pattern")))
  )(SchemeOrganiserDetails.apply)(SchemeOrganiserDetails.unapply))

  /*
* scheme type Form definition.
*/
  def schemeTypeForm()(implicit messages: Messages)  = Form(

    mapping("schemeType" -> text)(RS_schemeType.apply)(RS_schemeType.unapply))


  def checklength(so: String, field: String) = {
    field match {
      case "schemeOrganiserFields.companyName" => {
        so.length match {
          case x => if (x <= 35) true else false
        }
      }
      case "companyDetailsFields.companyName" | "trusteeDetailsFields.name" => {
        so.length match {
          case x => if (x <= 120) true else false
        }
      }
      case "schemeOrganiserFields.addressLine1" | "schemeOrganiserFields.addressLine2" | "schemeOrganiserFields.addressLine3" => {
        so.length match {
          case x => if (x <= 27) true else false
        }
      }
      case "companyDetailsFields.addressLine1" | "companyDetailsFields.addressLine2" | "companyDetailsFields.addressLine3" => {
        so.length match {
          case x => if (x <= 27) true else false
        }
      }
      case "trusteeDetailsFields.addressLine1" | "trusteeDetailsFields.addressLine2" | "trusteeDetailsFields.addressLine3" => {
        so.length match {
          case x => if (x <= 27) true else false
        }
      }
      case "schemeOrganiserFields.addressLine4" | "companyDetailsFields.addressLine4" | "trusteeDetailsFields.addressLine4" => {
        so.length match {
          case x => if (x <= 18) true else false
        }
      }
      case "schemeOrganiserFields.postcode" | "companyDetailsFields.postcode" | "trusteeDetailsFields.postcode" => {
        so.length match {
          case x => if (x <= 8) true else false
        }
      }
      case _ => true
    }
  }

  def validInputCharacters(field: String, regXValue: String) = {
    if (field.matches(regXValue)) true else false
  }

  def isValidPostcode(input: Option[String]) = {
    input.fold(true)(_.trim.matches(fieldValidationPatterns.postCodeRegx)) && isValidLengthIfPopulated(input, 6, 8)
  }

  def isValidLengthIfPopulated(input: Option[String], minSize: Int, maxSize: Int) = input.fold(true)(s => s.trim.size >= minSize && s.trim.size <= maxSize)

}

object reportableEventsFields {
  val isNilReturn = "isNilReturn"
}

object checkFileTypeFileds {
  val checkFileType = "checkFileType"
}

object trusteeDetailsFields {
  val name = "name"
  val addressLine1 = "addressLine1"
  val addressLine2 = "addressLine2"
  val addressLine3 = "addressLine3"
  val addressLine4 = "addressLine4"
  val country = "country"
  val postcode = "postcode"
}

object companyDetailsFields {
  val companyName = "companyName"
  val addressLine1 = "addressLine1"
  val addressLine2 = "addressLine2"
  val addressLine3 = "addressLine3"
  val addressLine4 = "addressLine4"
  val country = "country"
  val postcode = "postcode"
  val companyReg = "companyReg"
  val corporationRef = "corporationRef"
}

object schemeOrganiserFields {
  val companyName = "companyName"
  val addressLine1 = "addressLine1"
  val addressLine2 = "addressLine2"
  val addressLine3 = "addressLine3"
  val addressLine4 = "addressLine4"
  val country = "country"
  val postcode = "postcode"
  val companyReg = "companyReg"
  val corporationRef = "corporationRef"
}

object fieldValidationPatterns {
  def companyRegPattern = "(^[a-zA-Z0-9]{1,10})$"

  def corporationRefPattern = "^([0-9]{10})$"

  def addresssRegx = """^[A-Za-zÀ-ÿ0-9 &'(),-./]{0,}$"""

  def postCodeRegx = """(GIR 0AA)|((([A-Z-[QVX]][0-9][0-9]?)|(([A-Z-[QVX]][A-Z-[IJZ]][0-9][0-9]?)|(([A-Z-[QVX‌​]][0-9][A-HJKSTUW])|([A-Z-[QVX]][A-Z-[IJZ]][0-9][ABEHMNPRVWXY]))))\s?[0-9][A-Z-[C‌​IKMOV]]{2})"""

  def yesNoRegPattern = "^([1-2]{1})$"

  def csvOdsRegPattern = "^((ods|csv))$"

  def csvFileNameRegx = """^file[0-9]{1}$"""
}

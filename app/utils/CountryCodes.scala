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

package utils

import play.api.{Logger, Play}
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import scala.io.Source


object CountryCodes extends CountryCodes

trait CountryCodes {

  case class Country(country: String, countryCode: String)

  object Country {
    implicit val formats = Json.format[Country]
  }

  val jsonInputStream = Play.application.resourceAsStream("country-codes.json")

  private val json: JsValue = {
    jsonInputStream match {
      case Some(inputStream) => Json.parse(Source.fromInputStream(inputStream, "UTF-8").mkString)
      case _ => {
        Logger.error(s"Country codes file not found, timestamp: ${System.currentTimeMillis()}.")
        throw new Exception
      }
    }
  }

  val countries: String = {
    Json.toJson(json.\\("country").toList.map(x => x.toString().replaceAll("\"", ""))).toString()
  }

  val countriesMap: List[Country] = {
    val countryList = json.validate[List[Country]].get
    countryList
  }

  private val countryCodesMap: Map[String, String] = {
    val countryCodeList = json.validate[List[Country]].get
    countryCodeList.map(country => (country.countryCode, country.country)).toMap
  }

  def getCountry(countryCode: String): Option[String] = {
    countryCodesMap.get(countryCode)
  }

}

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

import java.io.InputStream

import akka.stream.Materializer
import org.scalatestplus.play.OneServerPerSuite
import play.api.Play.current
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Play}
import uk.gov.hmrc.play.test.UnitSpec

class CountryCodeTest extends UnitSpec with OneServerPerSuite with ERSFakeApplicationConfig {

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()
  implicit lazy val mat: Materializer = app.materializer

  object TestCountryCodes extends CountryCodes {
    override val jsonInputStream: Option[InputStream] = Play.application.resourceAsStream("country-code-test.json")
  }

  "CountryCode countries" should {
    "return a string of countries" in {
      val countries = CountryCodes.countries
      countries should include("Andorra")
      countries should include("Germany")
      countries should include("France")
    }
    "not return a string of countries" in {
      intercept[Exception] {
        TestCountryCodes.countries
      }
    }
  }

  "CountryCode getCountry" should {
    "return a country from a country code" in {
      CountryCodes.getCountry("AD") should be(Some("Andorra"))
      CountryCodes.getCountry("DE") should be(Some("Germany"))
      CountryCodes.getCountry("FR") should be(Some("France"))

      CountryCodes.getCountry("ZZ") should be(None)
    }
  }

}

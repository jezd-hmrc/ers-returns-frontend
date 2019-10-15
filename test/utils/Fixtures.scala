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

package utils

import controllers.RequestWithSchemeInfo
import models._
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{EmpRef, Generator}
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

object Fixtures {
  val firstName = "FirstName"
  val middleName = "MiddleName"
  val surname = "Surname"
  val nino = new Generator().nextNino.nino
  val companyName = "Company Name"

  def getAwaitDuration(): Duration = {
    60 seconds
  }

  val accounts =  Accounts(None, epaye = Some(EpayeAccount(s"/epaye/empRef", EmpRef("ABC", "1234"))))

  val loggedInUser = LoggedInUser("userId", Some(DateTime.now), Some(DateTime.now), Some("governmentGatewayToken"), CredentialStrength.Strong, ConfidenceLevel.L500,"")

  def buildFakeAuthContext = AuthContext(loggedInUser, Principal(Some("name"), accounts), Some(Attorney("name", Link("url", "text"))), None, None, None)

  def fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID","screenSchemeInfo" -> "2 - EMI - MYScheme - XX12345678 - 2016")

  def fakeRequestWithFormData(formData: Seq[(String, String)]) =
    fakeRequest.withFormUrlEncodedBody(formData: _*)

  implicit def fakeRequestToRequestWithSchemeInfo[A](request: FakeRequest[A]): RequestWithSchemeInfo[A] =
    RequestWithSchemeInfo(request, SchemeInfo(schemeRef = "XX12345678", schemeId = "2", schemeName = "MYScheme", schemeType = "EMI", taxYear = "2016"))

  def buildFakeRequestWithSessionId(method: String = "pointless") = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID","screenSchemeInfo" -> "2 - EMI - MYScheme - XX12345678 - 2016")

  def buildFakeRequestWithNoScreenScheemeInfo(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID")

  def buildFakeRequestWithBrokenScreenScheemeInfo(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID", "screenSchemeInfo" -> "-")

  def buildFakeRequestWithSessionIdCSOP(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID", "screenSchemeInfo" -> "1 - CSOP - MYScheme - XX12345678 - 2016")

  def buildFakeRequestWithSessionIdSAYE(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID", "screenSchemeInfo" -> "4 - SAYE - MYScheme - XX12345678 - 2016")

  def buildFakeRequestWithSessionIdSIP(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID", "screenSchemeInfo" -> "5 - SIP - MYScheme - XX12345678 - 2016")

  def buildFakeRequestWithSessionIdEMI(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID", "screenSchemeInfo" -> "2 - EMI - MYScheme - XX12345678 - 2016")

  def buildFakeRequestWithSessionIdOTHER(method: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession("sessionId" -> "FAKE_SESSION_ID", "screenSchemeInfo" -> "3 - OTHER - MYScheme - XX12345678 - 2016")

  def buildFakeRequest(method: String):FakeRequest[_] = FakeRequest()

  def fetchAndGetMockScheme(): Future[Option[String]] = {
    Future.successful(Some("1"))
  }

  def schemeRef : String = {"XYZ12345"}

  val timestamp: DateTime = DateTime.now

  val schemeType = "EMI"

  val EMISchemeInfo: SchemeInfo = SchemeInfo (
    schemeRef = "XA1100000000000",
    timestamp = timestamp,
    schemeId = "123AA12345678",
    taxYear = "2014/15",
    schemeName = "My scheme",
    schemeType = schemeType
  )

  val EMIMetaData = ErsMetaData(
    schemeInfo = EMISchemeInfo,
    ipRef = "127.0.0.0",
    aoRef = Some("123AA12345678"),
    empRef = "EMI - MyScheme - XA1100000000000 - 2014/15",
    agentRef = None,
    sapNumber = Some("sap-123456")
  )

  val scheetName: String = "EMI40_Adjustments_V3"
  val data: Option[ListBuffer[Seq[String]]] = Some(
    ListBuffer(
      Seq("no", "no", "yes", "3", "2015-12-09", firstName, "", surname, nino, "123/XZ55555555", "10.1234", "100.12", "10.1234", "10.1234"),
      Seq("no", "no", "no", "", "2015-12-09", firstName, "", surname, nino, "123/XZ55555555", "10.1234", "100.12", "10.1234", "10.1234"),
      Seq("yes", "", "", "", "2015-12-09", firstName, middleName, surname, nino, "123/XZ55555555", "10.1234", "100.12", "10.1234", "10.1234")
    )
  )

  val invalidJson: JsObject = Json.obj(
    "metafield1" -> "metavalue1",
    "metafield2" -> "metavalue2",
    "metafield3" -> "metavalue3"
  )

  val schemeOrganiserDetails: SchemeOrganiserDetails = SchemeOrganiserDetails(
    "companyName",
    "addressLine1",
    None,
    None,
    None,
    None,
    None,
    None,
    Some("corporationRef")
  )

  val companyDetails: CompanyDetails = CompanyDetails(
    "testCompany",
    "testAddress1",
    Some("testAddress2"),
    Some("testAddress3"),
    Some("testAddress4"),
    Some("AA1 1AA"),
    Some("United Kingdom"),
    Some("1234567890"),
    Some("1234567890")
  )

  val groupScheme  = GroupSchemeInfo(Some("1"), Some("emi"))

  val companiesList = CompanyDetailsList(List(companyDetails))
  val ersSummary = ErsSummary("testbundle", "1", None, new DateTime(2016,6,8,11,5), metaData = EMIMetaData, None, None, Some(groupScheme), Some(schemeOrganiserDetails),
    Some(companiesList), None,None,None)

  val metadataJson: JsObject = Json.toJson(EMIMetaData).as[JsObject]
}

/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.tai.model.tai.TaxYear

class BaseConnectorSpec extends ConnectorBaseSpec {

  lazy val metrics: Metrics = inject[Metrics]
  lazy val httpClient: HttpClient = inject[HttpClient]

  lazy val sut: BaseConnector = new BaseConnector {
    override def originatorId: String = "testOriginatorId"
  }

  lazy val npsConnector: DefaultEmploymentDetailsConnector = inject[DefaultEmploymentDetailsConnector]

  lazy val endpoint: String = "/foo"
  lazy val url: String = s"${server.baseUrl()}$endpoint"

  val apiType: APITypes.Value = APITypes.RTIAPI

  val body: String =
    """{
      |"name": "Bob",
      |"age": 24
      |}""".stripMargin

  case class ResponseObject(name: String, age: Int)
  implicit val format: OFormat[ResponseObject] = Json.format[ResponseObject]

  val bodyAsObj: ResponseObject = Json.parse(body).as[ResponseObject]

  val rtiData: RtiData = RtiData(nino.nino, TaxYear(2017), "req123", Nil)
  val rtiDataBody: String = Json.toJson(rtiData).toString()

  val eTagKey: String = "ETag"
  val eTag: Int = 34

  "BaseConnector" must {
    "get the version from the HttpResponse" when {
      "the HttpResponse contains the ETag header" in {

        val response: HttpResponse = HttpResponse(200, "", Map(eTagKey -> Seq(s"$eTag")))

        sut.getVersionFromHttpHeader(response) mustBe eTag
      }
    }

    "get a default version value" when {
      "the HttpResponse does not contain the ETag header" in {
        val response: HttpResponse = HttpResponse(200, "")

        sut.getVersionFromHttpHeader(response) mustBe -1
      }
    }

    "throw a NumberFormatException" when {
      "the ETag value is not a valid integer" in {

        val invalidEtag = "Foobar"

        val response: HttpResponse = HttpResponse(200, "", Map(eTagKey -> Seq(invalidEtag)))

        val ex = the[NumberFormatException] thrownBy sut.getVersionFromHttpHeader(response)
        ex.getMessage mustBe s"""For input string: "$invalidEtag""""
      }
    }
  }
}

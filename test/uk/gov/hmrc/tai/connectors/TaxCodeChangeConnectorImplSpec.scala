/*
 * Copyright 2018 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TaxCodeChangeConnectorImplSpec extends PlaySpec with WireMockHelper with MockitoSugar {

//  private lazy val connector = injector.instanceOf[TaxCodeChangeConnector]
//
//  "tax code change API" must{
//    "return tax code change response" in {
//      val taxYear = TaxYear(2017)
//      val testNino = randomNino
//      val host = "localhost"
//      val port = 9332
//      val url = s"http://$host:$port/personal-tax-account/tax-code/history/api/v1/$testNino/${taxYear.year}"
//
//      server.stubFor(
//        get(urlEqualTo(url)).willReturn(ok(jsonResponse.toString))
//      )
//
//      val expectedResult = TaxCodeHistoryDetails(testNino, Seq(TaxCodeHistoryItem(1234567890, true, "2011-06-23")))
//
//      val result = Await.result(connector.taxCodeChanges(testNino, taxYear), 5 seconds)
//
//      result mustEqual expectedResult
//    }
//  }
//
//  private val jsonResponse = Json.obj(
//    "nino" -> new Generator(new Random).nextNino,
//    "taxHistoryList" -> Seq(
//      Json.obj(
//        "employmentId" -> 1234567890,
//        "p2Issued" -> true,
//        "p2Date" -> "2011-06-23"
//      )
//    )
//  )
//
//  implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
//
//  private def randomNino: Nino = new Generator(new Random).nextNino

}
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

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.{DesConfig, NpsConfig}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class IabdConnectorSpec extends PlaySpec with MockitoSugar {

  "Iabd Connector" must {
    "return Iabd json" in {

      val mockHttpHandler = mock[HttpHandler]
      val mockConfig = mock[DesConfig]
      val iabdUrls = mock[IabdUrls]

      when(iabdUrls.iabdUrlDes(any(), any())).thenReturn("URL")
      when(mockConfig.originatorId).thenReturn("TEST")
      when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.DesIabdAllAPI))(Matchers.
        eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

      val sut = createSUT(config = mockConfig, iabdUrls = iabdUrls, httpHandler = mockHttpHandler)

      val result = Await.result(sut.iabds(nino, TaxYear()), 5.seconds)

      result mustBe json
    }

    "return empty json" when {
      "looking for next tax year" in {
        val mockHttpHandler = mock[HttpHandler]
        val mockConfig = mock[DesConfig]
        val iabdUrls = mock[IabdUrls]
        when(iabdUrls.iabdUrlDes(any(), any())).thenReturn("URL")
        when(mockConfig.originatorId).thenReturn("TEST")
        when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.DesIabdAllAPI))(Matchers.
          eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))
        val sut = createSUT(config = mockConfig, iabdUrls = iabdUrls, httpHandler = mockHttpHandler)

        val result = Await.result(sut.iabds(nino, TaxYear().next), 5.seconds)

        result mustBe Json.arr()
      }

      "looking for cy+2 year" in {
        val mockHttpHandler = mock[HttpHandler]
        val mockConfig = mock[DesConfig]
        val iabdUrls = mock[IabdUrls]
        when(iabdUrls.iabdUrlDes(any(), any())).thenReturn("URL")
        when(mockConfig.originatorId).thenReturn("TEST")
        when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.DesIabdAllAPI))(Matchers.
          eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))
        val sut = createSUT(config = mockConfig, iabdUrls = iabdUrls, httpHandler = mockHttpHandler)

        val result = Await.result(sut.iabds(nino, TaxYear().next.next), 5.seconds)

        result mustBe Json.arr()
      }
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  val nino = new Generator(new Random).nextNino
  private val json = Json.arr(
    Json.obj(
      "nino" -> nino.withoutSuffix,
      "taxYear" -> 2017,
      "type" -> 10,
      "source" -> 15,
      "grossAmount" -> JsNull,
      "receiptDate" -> JsNull,
      "captureDate" -> "10/04/2017",
      "typeDescription" -> "Total gift aid Payments",
      "netAmount" -> 100
    )
  )


  private def createSUT(config: DesConfig = mock[DesConfig],
                        httpHandler: HttpHandler = mock[HttpHandler],
                        iabdUrls: IabdUrls = mock[IabdUrls]) = {
    new IabdConnector(config, httpHandler, iabdUrls)
  }

}

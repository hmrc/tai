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

package uk.gov.hmrc.tai.connectors

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.{DesConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class IabdConnectorSpec extends PlaySpec with MockitoSugar {

  "IABD Connector" when {

    "toggled to use NPS" must {
      "return IABD json" in {
        val mockHttpHandler = mock[HttpHandler]
        val mockNpsConfig = mock[NpsConfig]
        val mockDesConfig = mock[DesConfig]
        val iabdUrls = mock[IabdUrls]
        val featureTogglesConfig = mock[FeatureTogglesConfig]

        when(iabdUrls.npsIabdUrl(any(), any())).thenReturn("URL")
        when(featureTogglesConfig.desEnabled).thenReturn(false)
        when(mockNpsConfig.originatorId).thenReturn("TEST")
        when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.NpsIabdAllAPI))(Matchers.
          eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

        val sut = createSUT(mockNpsConfig, mockDesConfig, mockHttpHandler, iabdUrls, featureTogglesConfig)
        val result = Await.result(sut.iabds(nino, TaxYear()), 5.seconds)

        result mustBe json
      }

      "return empty json" when {
        "looking for next tax year" in {
          val mockHttpHandler = mock[HttpHandler]
          val mockNpsConfig = mock[NpsConfig]
          val mockDesConfig = mock[DesConfig]
          val iabdUrls = mock[IabdUrls]
          val featureTogglesConfig = mock[FeatureTogglesConfig]

          when(iabdUrls.desIabdUrl(any(), any())).thenReturn("URL")
          when(featureTogglesConfig.desEnabled).thenReturn(false)
          when(mockNpsConfig.originatorId).thenReturn("TEST")
          when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.NpsIabdAllAPI))(Matchers.
            eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

          val sut = createSUT(mockNpsConfig, mockDesConfig, mockHttpHandler, iabdUrls, featureTogglesConfig)
          val result = Await.result(sut.iabds(nino, TaxYear().next), 5.seconds)

          result mustBe Json.arr()
        }

        "looking for cy+2 year" in {
          val mockHttpHandler = mock[HttpHandler]
          val mockNpsConfig = mock[NpsConfig]
          val mockDesConfig = mock[DesConfig]
          val iabdUrls = mock[IabdUrls]
          val featureTogglesConfig = mock[FeatureTogglesConfig]

          when(iabdUrls.desIabdUrl(any(), any())).thenReturn("URL")
          when(featureTogglesConfig.desEnabled).thenReturn(false)
          when(mockNpsConfig.originatorId).thenReturn("TEST")
          when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.NpsIabdAllAPI))(Matchers.
            eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

          val sut = createSUT(mockNpsConfig, mockDesConfig, mockHttpHandler, iabdUrls, featureTogglesConfig)
          val result = Await.result(sut.iabds(nino, TaxYear().next.next), 5.seconds)

          result mustBe Json.arr()
        }
      }
    }

    "toggled to use DES" must {

      "return IABD json" in {
        val mockHttpHandler = mock[HttpHandler]
        val mockNpsConfig = mock[NpsConfig]
        val mockDesConfig = mock[DesConfig]
        val iabdUrls = mock[IabdUrls]
        val featureTogglesConfig = mock[FeatureTogglesConfig]

        when(featureTogglesConfig.desEnabled).thenReturn(true)
        when(iabdUrls.desIabdUrl(any(), any())).thenReturn("URL")
        when(mockDesConfig.originatorId).thenReturn("TEST")
        when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.DesIabdAllAPI))(Matchers.
          eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

        val sut = createSUT(mockNpsConfig, mockDesConfig, mockHttpHandler, iabdUrls, featureTogglesConfig)
        val result = Await.result(sut.iabds(nino, TaxYear()), 5.seconds)

        result mustBe json
      }

      "return empty json" when {
        "looking for next tax year" in {
          val mockHttpHandler = mock[HttpHandler]
          val mockNpsConfig = mock[NpsConfig]
          val mockDesConfig = mock[DesConfig]
          val iabdUrls = mock[IabdUrls]
          val featureTogglesConfig = mock[FeatureTogglesConfig]

          when(iabdUrls.desIabdUrl(any(), any())).thenReturn("URL")
          when(featureTogglesConfig.desEnabled).thenReturn(true)
          when(mockDesConfig.originatorId).thenReturn("TEST")
          when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.DesIabdAllAPI))(Matchers.
            eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

          val sut = createSUT(mockNpsConfig, mockDesConfig, mockHttpHandler, iabdUrls, featureTogglesConfig)
          val result = Await.result(sut.iabds(nino, TaxYear().next), 5.seconds)

          result mustBe Json.arr()
        }

        "looking for cy+2 year" in {
          val mockHttpHandler = mock[HttpHandler]
          val mockNpsConfig = mock[NpsConfig]
          val mockDesConfig = mock[DesConfig]
          val iabdUrls = mock[IabdUrls]
          val featureTogglesConfig = mock[FeatureTogglesConfig]

          when(iabdUrls.desIabdUrl(any(), any())).thenReturn("URL")
          when(featureTogglesConfig.desEnabled).thenReturn(true)
          when(mockDesConfig.originatorId).thenReturn("TEST")
          when(mockHttpHandler.getFromApi(Matchers.eq("URL"), Matchers.eq(APITypes.DesIabdAllAPI))(Matchers.
            eq(hc.withExtraHeaders("Gov-Uk-Originator-Id" -> "TEST")))).thenReturn(Future.successful(json))

          val sut = createSUT(mockNpsConfig, mockDesConfig, mockHttpHandler, iabdUrls, featureTogglesConfig)
          val result = Await.result(sut.iabds(nino, TaxYear().next.next), 5.seconds)

          result mustBe Json.arr()
        }
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


  private def createSUT(npsConfig: NpsConfig = mock[NpsConfig],
                        desConfig: DesConfig = mock[DesConfig],
                        httpHandler: HttpHandler = mock[HttpHandler],
                        iabdUrls: IabdUrls = mock[IabdUrls],
                        featureTogglesConfig: FeatureTogglesConfig = mock[FeatureTogglesConfig]) = {
    new IabdConnector(npsConfig, desConfig, httpHandler, iabdUrls, featureTogglesConfig)
  }

}

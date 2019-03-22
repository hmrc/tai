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

import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.config._
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.util.Random


class ApplicationUrlsSpec extends PlaySpec with MockitoSugar {

  val mockConfigNps = mock[NpsConfig]
  val mockConfigDes = mock[DesConfig]
  val mockConfigFileUpload = mock[FileUploadConfig]
  val mockFeatureToggleConfig = mock[FeatureTogglesConfig]

  when(mockConfigNps.baseURL).thenReturn("")
  when(mockConfigDes.baseURL).thenReturn("")
  when(mockConfigFileUpload.baseURL).thenReturn("")

  val taxAccountUrls = new TaxAccountUrls(mockConfigNps, mockConfigDes, mockFeatureToggleConfig)
  val iabdUrls = new IabdUrls(mockConfigNps, mockConfigDes)

  def featureToggle(desEnabled: Boolean, confirmedAPIEnabled: Boolean) = {
    when(mockFeatureToggleConfig.desEnabled).thenReturn(desEnabled)
    when(mockFeatureToggleConfig.confirmedAPIEnabled).thenReturn(confirmedAPIEnabled)
  }

  private val nino: Nino = new Generator(new Random).nextNino


  "RtiUrls" must {
    "return the correct urls" when {
      "given argument values" in {
        val rtiUrls = new RtiUrls(mockConfigDes)
        rtiUrls.paymentsForYearUrl(nino.nino, TaxYear(2017)) mustBe
          s"/rti/individual/payments/nino/${nino.nino}/tax-year/17-18"
      }
    }
  }

  "PdfUrls" must {
    "return the correct urls" in {
      val mockConfig = mock[PdfConfig]
      when(mockConfig.baseURL)
        .thenReturn("")

      val pdfUrls = new PdfUrls(mockConfig)

      pdfUrls.generatePdfUrl mustBe "/pdf-generator-service/generate"
    }
  }

  "PayeUrls" must {
    "return the correct urls" when {
      "given argument values" in {
        val mockConfig = mock[PayeConfig]
        when(mockConfig.baseURL)
          .thenReturn("")

        val payeUrls = new PayeUrls(mockConfig)

        payeUrls.carBenefitsForYearUrl(nino, TaxYear(2017)) mustBe s"/paye/${nino.nino}/car-benefits/2017"
        payeUrls.ninoVersionUrl(nino) mustBe s"/paye/${nino.nino}/version"
        payeUrls.removeCarBenefitUrl(nino, TaxYear(2017), 1, 2) mustBe s"/paye/${nino.nino}/benefits/2017/1/car/2/remove"
      }
    }
  }

  "FileUploadUrls" must {
    "return the correct urls" when {
      "no arguments are given" in {
        val fileUploadUrls = new FileUploadUrls(mockConfigFileUpload)

        fileUploadUrls.envelopesUrl mustBe "/file-upload/envelopes"
        fileUploadUrls.routingUrl mustBe "/file-routing/requests"
      }

      "given argument values" in {

        when(mockConfigFileUpload.frontendBaseURL).thenReturn("")

        val fileUploadUrls = new FileUploadUrls(mockConfigFileUpload)

        fileUploadUrls.fileUrl("123-123", "xml") mustBe "/file-upload/upload/envelopes/123-123/files/xml"
      }
    }
  }

  "CitizenDetailsUrls" must {
    "return the correct urls" when {
      "given argument values" in {
        val mockConfig = mock[CitizenDetailsConfig]
        when(mockConfig.baseURL)
          .thenReturn("")

        val citizenDetailsUrls = new CitizenDetailsUrls(mockConfig)

        citizenDetailsUrls.designatoryDetailsUrl(nino) mustBe s"/citizen-details/${nino.nino}/designatory-details"
      }
    }
  }

  "BbsiUrls" must {
    "return the correct urls" when {
      "given argument values" in {
        val bbsiUrls = new BbsiUrls(mockConfigDes)

        bbsiUrls.bbsiUrl(nino, TaxYear(2017)) mustBe
          s"/pre-population-of-investment-income/nino/${nino.nino.take(8)}/tax-year/2017"
      }
    }
  }

  "TaxAccountUrls" when {
    "toggled for calculation" must {
      "return the correct DES url" when {
        "given argument values" ignore {
          featureToggle(true, false)
          taxAccountUrls.taxAccountUrl(nino, TaxYear(2017)) mustBe s"/pay-as-you-earn/individuals/${nino.nino}/tax-account/tax-year/2017?calculation=true"
        }
      }

      "return the correct NPS url" when {
        "given argument values" ignore {
          featureToggle(false, false)
          taxAccountUrls.taxAccountUrl(nino, TaxYear(2017)) mustBe s"/person/${nino.nino}/tax-account/2017/calculation"
        }
      }
    }

    "toggled for confirmed" must {
      "return the correct DES url" when {
        "given argument values" in {
          featureToggle(true, true)
          taxAccountUrls.taxAccountUrl(nino, TaxYear(2017)) mustBe s"/pay-as-you-earn/individuals/${nino.nino}/tax-account/tax-year/2017"
        }
      }
      "return the correct NPS url" when {
        "given argument values" in {
          featureToggle(false, true)
          taxAccountUrls.taxAccountUrl(nino, TaxYear(2017)) mustBe s"/person/${nino.nino}/tax-account/2017"
        }
      }
    }

  }



  "IabdUrls" must {
    "return the correct urls" when {
      "given argument values" in {
        iabdUrls.npsIabdUrl(nino, TaxYear(2017)) mustBe s"/person/${nino.nino}/iabds/2017"
        iabdUrls.desIabdUrl(nino, TaxYear(2017)) mustBe s"/pay-as-you-earn/individuals/${nino.nino}/iabds/tax-year/2017"
      }
    }

    "return the correct iabd employment url" when {
      "given argument values" in {
        iabdUrls.npsIabdEmploymentUrl(nino, TaxYear(2017), 1) mustBe s"/person/${nino.nino}/iabds/2017/employment/1"
        iabdUrls.desIabdEmploymentUrl(nino, TaxYear(2017), 1) mustBe s"/pay-as-you-earn/individuals/${nino.nino}/iabds/tax-year/2017/employment/1"
      }
    }

    "IabdByTypeUrls" must {
      "return the correct urls" when {
        "given argument values" in {
          iabdUrls.desIabdByTypeUrl(nino, TaxYear(2017), IabdType.FlatRateJobExpenses) mustBe s"/pay-as-you-earn/individuals/${nino.nino}/iabds/tax-year/2017?type=56"
        }
      }
    }
  }

}

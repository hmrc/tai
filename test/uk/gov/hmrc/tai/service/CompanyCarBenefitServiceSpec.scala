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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.tai.connectors.CompanyCarConnector
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCarBenefit
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class CompanyCarBenefitServiceSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCompanyCarConnector)
    when(mockCompanyCarConnector.ninoVersion(any())(any()))
      .thenReturn(Future.successful(sampleNinoVersion))
  }

  val mockCompanyCarConnector: CompanyCarConnector = mock[CompanyCarConnector]
  private val sampleNinoVersion = 4

  private def createSUT(companyCarConnector: CompanyCarConnector) =
    new CompanyCarBenefitService(companyCarConnector)

  "carBenefit" must {
    "return the empty list coming from the company car service" in {
      val carBenefitFromCompanyCarService = Nil

      when(mockCompanyCarConnector.carBenefits(any(), any())(any()))
        .thenReturn(Future.successful(carBenefitFromCompanyCarService))

      val sut = createSUT(mockCompanyCarConnector)
      sut.carBenefit(nino, TaxYear(2017)).futureValue mustBe carBenefitFromCompanyCarService
    }

    "return the non-empty list coming from the company car service" in {
      val carBenefitSeq = Seq(CompanyCarBenefit(10, 10, Nil))
      val carBenefitSeqWithVersion = Seq(CompanyCarBenefit(10, 10, Nil, Some(sampleNinoVersion)))

      when(mockCompanyCarConnector.carBenefits(any(), any())(any()))
        .thenReturn(Future.successful(carBenefitSeq))

      val sut = createSUT(mockCompanyCarConnector)
      sut.carBenefit(nino, TaxYear(2017)).futureValue mustBe carBenefitSeqWithVersion
    }
  }
}

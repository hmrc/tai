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

package uk.gov.hmrc.tai.repositories

import cats.data.EitherT
import cats.implicits._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.connectors.RtiConnector
import uk.gov.hmrc.tai.connectors.deprecated.NpsConnector
import uk.gov.hmrc.tai.model.domain
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.error.EmploymentNotFound
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.{EmploymentRepository, TaiCacheRepository}
import uk.gov.hmrc.tai.util.BaseSpec

import java.io.File
import java.time.LocalDate
import scala.concurrent.Future
import scala.io.BufferedSource

class EmploymentRepositorySpec extends BaseSpec {

  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev
  val employmentDataKey = s"EmploymentData-${currentTaxYear.year}"
  implicit val request = FakeRequest()

  val npsSingleEmployment = Employment(
    "EMPLOYER1",
    Live,
    Some("0"),
    LocalDate.of(2016, 4, 6),
    None,
    Seq.empty[AnnualAccount],
    "0",
    "0",
    1,
    None,
    false,
    false)

  val npsDualEmployment = (
    Employment(
      name = "EMPLOYER1",
      employmentStatus = Live,
      payrollNumber = Some("0"),
      startDate = LocalDate.of(2016, 4, 6),
      endDate = None,
      annualAccounts = Seq.empty[AnnualAccount],
      taxDistrictNumber = "0",
      payeNumber = "0",
      sequenceNumber = 1,
      cessationPay = None,
      hasPayrolledBenefit = false,
      receivingOccupationalPension = false
    ),
    Employment(
      name = "EMPLOYER2",
      employmentStatus = Live,
      payrollNumber = Some("00"),
      startDate = LocalDate.of(2016, 4, 6),
      endDate = None,
      annualAccounts = Seq.empty[AnnualAccount],
      taxDistrictNumber = "00",
      payeNumber = "00",
      sequenceNumber = 2,
      cessationPay = Some(100),
      hasPayrolledBenefit = true,
      receivingOccupationalPension = false
    )
  )

  def createAnnualAccount(
    rtiStatus: RealTimeStatus = Available,
    sequenceNumber: Int = 1,
    taxYear: TaxYear = currentTaxYear): AnnualAccount =
    AnnualAccount(sequenceNumber, taxYear, rtiStatus, Nil, Nil)


  private def testRepository(
                              rtiConnector: RtiConnector = mock[RtiConnector],
                              taiCacheRepository: TaiCacheRepository = mock[TaiCacheRepository],
                              npsConnector: NpsConnector = mock[NpsConnector],
                              employmentBuilder: EmploymentBuilder = mock[EmploymentBuilder]): EmploymentRepository =
    new EmploymentRepository(rtiConnector, taiCacheRepository, npsConnector, employmentBuilder)

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentRepositoryTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    Json.parse(source.mkString(""))
  }
}

/*
 * Copyright 2021 HM Revenue & Customs
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

import java.io.File

import org.joda.time.LocalDate
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, EndOfTaxYearUpdate, _}
import uk.gov.hmrc.tai.model.error.EmploymentNotFound
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.util.Random

class EmploymentRepositorySpec extends BaseSpec {

  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev
  val employmentDataKey = s"EmploymentData-${currentTaxYear.year}"

  val npsSingleEmployment = Employment(
    "EMPLOYER1",
    Live,
    Some("0"),
    new LocalDate(2016, 4, 6),
    None,
    Seq.empty[AnnualAccount],
    "0",
    "0",
    2,
    None,
    false,
    false)

  val npsDualEmployment = (
    Employment(
      name = "EMPLOYER1",
      employmentStatus = Live,
      payrollNumber = Some("0"),
      startDate = new LocalDate(2016, 4, 6),
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
      startDate = new LocalDate(2016, 4, 6),
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
    key: String = "0-0-0",
    taxYear: TaxYear = currentTaxYear): AnnualAccount =
    AnnualAccount(key, taxYear, rtiStatus, Nil, Nil)

  "employmentsForYear" should {
    "return the employment domain model" when {
      "there is no data in the cache" when {
        "a call to rti results in a ResourceNotFound error response" in {

          val annualAccount = createAnnualAccount(Unavailable)
          val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(annualAccount)))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Left(ResourceNotFoundError)))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(
            mockCacheConnector
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(expectedEmployments), any())(any()))
            .thenReturn(Future.successful(expectedEmployments))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder.combineAccountsWithEmployments(
              meq(Seq(npsSingleEmployment)),
              meq(Seq(annualAccount)),
              meq(nino),
              meq(currentTaxYear))(any()))
            .thenReturn(Employments(expectedEmployments))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            mockEmploymentBuilder)

          val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5 seconds)

          result mustBe Employments(expectedEmployments)

          verify(mockCacheConnector, times(1))
            .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(expectedEmployments), meq(employmentDataKey))(any())
        }

        "a call to rti results in a ServiceUnavailableError response" in {

          val annualAccount = createAnnualAccount(TemporarilyUnavailable)
          val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(annualAccount)))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Left(ServiceUnavailableError)))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(
            mockCacheConnector
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(expectedEmployments), any())(any()))
            .thenReturn(Future.successful(expectedEmployments))

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder
              .combineAccountsWithEmployments(
                meq(Seq(npsSingleEmployment)),
                meq(Seq(annualAccount)),
                meq(nino),
                meq(currentTaxYear))(any()))
            .thenReturn(Employments(expectedEmployments))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            mockEmploymentBuilder)

          val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5 seconds)

          result mustBe Employments(expectedEmployments)

          verify(mockCacheConnector, times(1))
            .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(expectedEmployments), meq(employmentDataKey))(any())

        }

        "data from hods includes corresponding annual account data (a single payment, and a single end tax year update)" in {
          val eyus = List(
            EndOfTaxYearUpdate(
              new LocalDate("2016-06-17"),
              List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))

          val payments =
            List(Payment(new LocalDate("2016-04-30"), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None))

          val annualAccount = AnnualAccount(
            key = "0-0-0",
            taxYear = currentTaxYear,
            realTimeStatus = Available,
            payments = payments,
            endOfTaxYearUpdates = eyus)

          val expectedEmployments = List(
            Employment(
              "EMPLOYER1",
              Live,
              Some("0"),
              new LocalDate(2016, 4, 6),
              None,
              Seq(annualAccount),
              "0",
              "0",
              2,
              None,
              false,
              false))

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder
              .combineAccountsWithEmployments(
                meq(Seq(npsSingleEmployment)),
                meq(Seq(annualAccount)),
                meq(nino),
                meq(currentTaxYear))(any()))
            .thenReturn(Employments(expectedEmployments))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Right(Seq(annualAccount))))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
            .thenReturn(Future.successful(expectedEmployments))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            employmentBuilder = mockEmploymentBuilder)
          val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5 seconds)

          result mustBe Employments(expectedEmployments)
        }

        "data from hods includes corresponding annual account data (a single payment, and two end tax year updates)" in {
          val eyus = List(
            EndOfTaxYearUpdate(
              new LocalDate("2016-06-17"),
              List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))),
            EndOfTaxYearUpdate(
              new LocalDate("2016-06-17"),
              List(Adjustment(TaxAdjustment, -28.99), Adjustment(NationalInsuranceAdjustment, 13.3)))
          )

          val payments =
            List(Payment(new LocalDate("2016-04-30"), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, OneOff, None))

          val annualAccount = AnnualAccount(
            key = "0-0-0",
            taxYear = currentTaxYear,
            realTimeStatus = Available,
            payments = payments,
            endOfTaxYearUpdates = eyus)

          val expectedEmploymentDetails = List(
            Employment(
              "EMPLOYER1",
              Live,
              Some("0"),
              new LocalDate(2016, 4, 6),
              None,
              Seq(annualAccount),
              "0",
              "0",
              2,
              None,
              false,
              false))

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder
              .combineAccountsWithEmployments(
                meq(Seq(npsSingleEmployment)),
                meq(Seq(annualAccount)),
                meq(nino),
                meq(currentTaxYear))(any()))
            .thenReturn(Employments(expectedEmploymentDetails))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Right(Seq(annualAccount))))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
            .thenReturn(Future.successful(expectedEmploymentDetails))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            employmentBuilder = mockEmploymentBuilder)
          val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5 seconds)
          result mustBe Employments(expectedEmploymentDetails)
        }

        "data from hods includes corresponding annual account data (two employments, each with a single payment, " +
          "and a single end tax year update)" in {
          val eyus1 = List(
            EndOfTaxYearUpdate(
              new LocalDate("2016-06-17"),
              List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
          val eyus2 = List(
            EndOfTaxYearUpdate(
              new LocalDate("2016-06-17"),
              List(Adjustment(TaxAdjustment, -66.6), Adjustment(NationalInsuranceAdjustment, 66.6))))

          val payments1 =
            List(Payment(new LocalDate("2016-04-30"), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, Annually, None))
          val payments2 =
            List(Payment(new LocalDate("2016-04-30"), 6600.0, 1600.0, 600.0, 6600.0, 1600.0, 600.0, FourWeekly, None))

          val annualAccount1 = AnnualAccount(
            key = "0-0-0",
            taxYear = currentTaxYear,
            realTimeStatus = Available,
            payments = payments1,
            endOfTaxYearUpdates = eyus1)

          val annualAccount2 = AnnualAccount(
            key = "00-00-00",
            taxYear = currentTaxYear,
            realTimeStatus = Available,
            payments = payments2,
            endOfTaxYearUpdates = eyus2)

          val expectedEmploymentDetails = List(
            Employment(
              "EMPLOYER1",
              Live,
              Some("0"),
              new LocalDate(2016, 4, 6),
              None,
              Seq(annualAccount1),
              "0",
              "0",
              1,
              None,
              false,
              false),
            Employment(
              "EMPLOYER2",
              Live,
              Some("00"),
              new LocalDate(2016, 4, 6),
              None,
              Seq(annualAccount2),
              "00",
              "00",
              2,
              Some(100),
              true,
              false)
          )

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder
              .combineAccountsWithEmployments(
                meq(Seq(npsDualEmployment._1, npsDualEmployment._2)),
                meq(Seq(annualAccount1, annualAccount2)),
                meq(nino),
                meq(currentTaxYear)
              )(any()))
            .thenReturn(Employments(expectedEmploymentDetails))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Right(Seq(annualAccount1, annualAccount2))))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
            .thenReturn(Future.successful(expectedEmploymentDetails))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsDualEmployments")))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            employmentBuilder = mockEmploymentBuilder)
          val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5 seconds)
          result mustBe Employments(expectedEmploymentDetails)
        }

        "result in an exception" when {
          "data is not present in cache or the hods for the given year" in {
            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))

            val mockNpsConnector = mock[NpsConnector]
            when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
              .thenReturn(Future.failed(new NotFoundException("nothing")))

            val sut = testRepository(cacheConnector = mockCacheConnector, npsConnector = mockNpsConnector)
            the[NotFoundException] thrownBy Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
          }
        }
      }

      "data is present in cache " when {
        "there is only one current year employment in the cache" when {
          "the request is for the current year the cached employment is passed back" in {

            val employments = Seq(
              Employment(
                "EMPLOYER1",
                Live,
                Some("12345"),
                LocalDate.now(),
                None,
                List(AnnualAccount("0", currentTaxYear, Available, Nil, Nil)),
                "",
                "",
                2,
                Some(100),
                false,
                false))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(employments))
            val mockNpsConnector = mock[NpsConnector]
            val mockRtiConnector = mock[RtiConnector]

            val sut = testRepository(
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              rtiConnector = mockRtiConnector)
            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)

            result mustBe Employments(employments)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockRtiConnector, times(0))
              .getPaymentsForYear(any(), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(employments), meq(employmentDataKey))(any())

          }
        }

        "there are multiple employments in the cache for different tax years" when {
          "the request is for the current tax year, the current tax year employment is returned" in {

            val cyEmployment = Employment(
              "employer1",
              Live,
              Some("12345"),
              LocalDate.now(),
              None,
              List(AnnualAccount("0", currentTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)

            val pyEmployment = Employment(
              "employer2",
              Live,
              Some("123456"),
              LocalDate.now(),
              None,
              List(AnnualAccount("0", previousTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cyEmployment, pyEmployment)))
            val mockNpsConnector = mock[NpsConnector]
            val mockRtiConnector = mock[RtiConnector]

            val sut = testRepository(
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              rtiConnector = mockRtiConnector)
            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)

            result mustBe Employments(List(cyEmployment))

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockRtiConnector, times(0))
              .getPaymentsForYear(any(), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(Seq(cyEmployment)), meq(employmentDataKey))(any())

          }
        }

        "the cache contains an employment with accounts for multiple tax years" when {
          "request is for a previous year the employment will be returned with just the previous year annual account" in {

            val cachedEmployments = List(
              Employment(
                "employer1",
                Live,
                Some("12345"),
                LocalDate.now(),
                None,
                List(
                  AnnualAccount("0", currentTaxYear, Available, Nil, Nil),
                  AnnualAccount("0", previousTaxYear, Available, Nil, Nil)),
                "",
                "",
                2,
                Some(100),
                false,
                false
              )
            )

            val expectedEmployment = Employment(
              "employer1",
              Live,
              Some("12345"),
              LocalDate.now(),
              None,
              List(AnnualAccount("0", previousTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(cachedEmployments))
            val mockNpsConnector = mock[NpsConnector]
            val mockRtiConnector = mock[RtiConnector]

            val sut = testRepository(
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              rtiConnector = mockRtiConnector)
            val result = Await.result(sut.employmentsForYear(nino, previousTaxYear), 5.seconds)

            result mustBe Employments(List(expectedEmployment))

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockRtiConnector, times(0))
              .getPaymentsForYear(any(), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), meq(Seq(expectedEmployment)), meq(employmentDataKey))(
                any())
          }
        }

        "the cache contains different employments which contain annual accounts for different tax years" when {

          val cachedEmployments = List(
            Employment(
              "TEST",
              Live,
              Some("12345"),
              LocalDate.now(),
              None,
              List(
                AnnualAccount("12345", currentTaxYear, Available, Nil, Nil),
                AnnualAccount("12345", previousTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false
            ),
            Employment(
              "TEST1",
              Live,
              Some("123456"),
              LocalDate.now(),
              None,
              List(
                AnnualAccount("123456", currentTaxYear, Available, Nil, Nil),
                AnnualAccount("123456", previousTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false
            )
          )

          val expectedCYEmployments = List(
            Employment(
              "TEST",
              Live,
              Some("12345"),
              LocalDate.now(),
              None,
              List(AnnualAccount("12345", currentTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false),
            Employment(
              "TEST1",
              Live,
              Some("123456"),
              LocalDate.now(),
              None,
              List(AnnualAccount("123456", currentTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)
          )

          val expectedPYEmployments = List(
            Employment(
              "TEST",
              Live,
              Some("12345"),
              LocalDate.now(),
              None,
              List(AnnualAccount("12345", previousTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false),
            Employment(
              "TEST1",
              Live,
              Some("123456"),
              LocalDate.now(),
              None,
              List(AnnualAccount("123456", previousTaxYear, Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)
          )

          Seq((currentTaxYear, expectedCYEmployments), (previousTaxYear, expectedPYEmployments)) foreach {
            taxYearAndEmployment =>
              s"the request is for ${taxYearAndEmployment._1} return those related employments" in {

                val mockCacheConnector = mock[CacheConnector]
                when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
                  .thenReturn(Future.successful(cachedEmployments))

                val sut = testRepository(cacheConnector = mockCacheConnector)
                val result = Await.result(sut.employmentsForYear(nino, taxYearAndEmployment._1), 5.seconds)

                result mustBe Employments(taxYearAndEmployment._2)

              }
          }
        }

        "the cache contains employments but not for the requested year" when {
          "a request is made the employment returned has a different key which is added to the cache" in {

            val cachedEmployment1 = Employment(
              "employer1",
              Live,
              Some("1"),
              LocalDate.now(),
              None,
              List(AnnualAccount("1", TaxYear(2018), Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)

            val cachedEmployment2 = Employment(
              "employer2",
              Live,
              Some("2"),
              LocalDate.now(),
              None,
              List(AnnualAccount("2", TaxYear(2018), Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)

            val cachedEmploymentsFor2018 = List(cachedEmployment1, cachedEmployment2)

            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              currentTaxYear,
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount)))
            val updatedCacheContents = cachedEmploymentsFor2018 ++ expectedEmployments

            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  meq(Seq(npsSingleEmployment)),
                  meq(Seq(expectedAnnualAccount)),
                  meq(nino),
                  meq(currentTaxYear)
                )(any()))
              .thenReturn(Employments(expectedEmployments))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

            val mockNpsConnector = mock[NpsConnector]
            when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
              .thenReturn(Future.successful(getJson("npsSingleEmployment")))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(cachedEmploymentsFor2018))

            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(updatedCacheContents))

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
            result mustBe Employments(expectedEmployments)

            verify(mockNpsConnector, times(1))
              .getEmploymentDetails(meq(nino), meq(currentTaxYear.year))(any())

            verify(mockRtiConnector, times(1))
              .getPaymentsForYear(meq(nino), meq(currentTaxYear))(any())

            verify(mockCacheConnector, times(1))
              .findSeq[Employment](meq(CacheId(nino)), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), employmentsCaptor.capture(), meq(employmentDataKey))(
                any())

            val cachedEmployments = employmentsCaptor.getValue
            cachedEmployments.size mustBe 3

            val expectedEmployment = expectedEmployments.head

            Seq(cachedEmployment1, cachedEmployment2, expectedEmployment) foreach { expected =>
              cachedEmployments must contain(expected)
            }

          }

          "a request is made the employment which is returned has the same key as one of the cached employments. It is" +
            "returned then merged into the cached employment" in {

            val now = LocalDate.now()

            val annualAccount1 = AnnualAccount("0-0-0", TaxYear(2018), Available, Nil, Nil)
            val employment1 = npsSingleEmployment.copy(annualAccounts = Seq(annualAccount1))

            val employment2 = Employment(
              "employer2",
              Live,
              Some("00"),
              now,
              None,
              List(AnnualAccount("00", TaxYear(2018), Available, Nil, Nil)),
              "",
              "",
              2,
              Some(100),
              false,
              false)

            val cachedEmploymentsFor2018 = List(
              employment1,
              employment2
            )

            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              currentTaxYear,
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount)))
            val cacheUpdatedEmployment1 =
              npsSingleEmployment.copy(annualAccounts = Seq(annualAccount1, expectedAnnualAccount))

            val updatedCacheContents = Seq(employment2, cacheUpdatedEmployment1)
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  meq(Seq(npsSingleEmployment)),
                  meq(Seq(expectedAnnualAccount)),
                  meq(nino),
                  meq(currentTaxYear)
                )(any()))
              .thenReturn(Employments(expectedEmployments))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

            val mockNpsConnector = mock[NpsConnector]
            when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
              .thenReturn(Future.successful(getJson("npsSingleEmployment")))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(cachedEmploymentsFor2018))

            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(updatedCacheContents))

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
            result mustBe Employments(expectedEmployments)

            verify(mockNpsConnector, times(1))
              .getEmploymentDetails(meq(nino), meq(currentTaxYear.year))(any())

            verify(mockRtiConnector, times(1))
              .getPaymentsForYear(meq(nino), meq(currentTaxYear))(any())

            verify(mockCacheConnector, times(1))
              .findSeq[Employment](meq(CacheId(nino)), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), employmentsCaptor.capture(), meq(employmentDataKey))(
                any())

            val actualCached = employmentsCaptor.getValue
            actualCached.size mustBe 2
            actualCached must contain(employment2)

            val cachedEmp1 = actualCached.filter(_.name == "EMPLOYER1")

            cachedEmp1.flatMap(_.annualAccounts) mustBe Seq(annualAccount1, expectedAnnualAccount)
          }
        }

        "the cached data contains an annualAccount with a TemporarilyUnavailable status" when {
          "a subsequent call is made to RTI, an AnnualAccount with a status of available is returned and the stubbed account is " +
            "removed from the cache" in {

            val cachedAnnualAccount = createAnnualAccount(TemporarilyUnavailable)
            val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount))

            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              currentTaxYear,
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount)))
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  meq(Seq(cachedEmployment)),
                  meq(Seq(expectedAnnualAccount)),
                  meq(nino),
                  meq(currentTaxYear)
                )(any()))
              .thenReturn(Employments(expectedEmployments))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cachedEmployment)))
            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(expectedEmployments))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
            result mustBe Employments(expectedEmployments)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), employmentsCaptor.capture(), meq(employmentDataKey))(
                any())

            employmentsCaptor.getValue mustBe expectedEmployments

          }

          "a subsequent call is made to RTI and an AnnualAccount with a status of Unavailable is returned and the stubbed account" +
            "is removed from the cache" in {

            val cachedAnnualAccount = createAnnualAccount(TemporarilyUnavailable, "00")
            val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount))
            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              currentTaxYear,
              Unavailable,
              List(),
              List()
            )

            val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount)))
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  meq(Seq(cachedEmployment)),
                  meq(Seq(expectedAnnualAccount)),
                  meq(nino),
                  meq(currentTaxYear)
                )(any()))
              .thenReturn(Employments(expectedEmployments))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cachedEmployment)))
            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(expectedEmployments))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
            result mustBe Employments(expectedEmployments)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), employmentsCaptor.capture(), meq(employmentDataKey))(
                any())

            employmentsCaptor.getValue mustBe expectedEmployments

          }

          "a subsequent call is made to RTI and a ServiceUnavailableError is returned" in {

            val tempUnavailableAccount = createAnnualAccount(TemporarilyUnavailable, "00")
            val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(tempUnavailableAccount))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cachedEmployment)))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Left(ServiceUnavailableError)))

            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector)

            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
            result mustBe Employments(Seq(cachedEmployment))

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), any(), meq(employmentDataKey))(any())
          }
        }

        "the cache data contains an employment with two annual accounts for different years with a status of TemporarilyUnavailable" when {
          "a subsequent request is made to RTI a status of available is returned, one annual account should be updated and the other left unmodified " in {

            val cachedAnnualAccount1 = createAnnualAccount(TemporarilyUnavailable)
            val cachedAnnualAccount2 =
              createAnnualAccount(rtiStatus = TemporarilyUnavailable, taxYear = TaxYear(2016))
            val cachedEmployment =
              npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount1, cachedAnnualAccount2))
            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              currentTaxYear,
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployments = Seq(npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount)))
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  any(),
                  meq(Seq(expectedAnnualAccount)),
                  meq(nino),
                  meq(currentTaxYear)
                )(any()))
              .thenReturn(Employments(expectedEmployments))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(Seq(cachedEmployment)))
            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(expectedEmployments))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))
            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)
            result mustBe Employments(expectedEmployments)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(meq(nino), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](meq(CacheId(nino)), employmentsCaptor.capture(), meq(employmentDataKey))(
                any())

            val actualCached = employmentsCaptor.getValue
            actualCached.size mustBe 1
            val cachedEmp1 = actualCached.filter(_.name == "EMPLOYER1")

            cachedEmp1.flatMap(_.annualAccounts) mustBe Seq(expectedAnnualAccount, cachedAnnualAccount2)
          }
        }

        "the cached data contains an annualAccount with a status not equal to TemporarilyUnavailable" in {

          val cachedAnnualAccount = createAnnualAccount(Available, "00")
          val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
            .thenReturn(Future.successful(Seq(cachedEmployment)))

          val sut = testRepository(cacheConnector = mockCacheConnector)
          val result = Await.result(sut.employmentsForYear(nino, currentTaxYear), 5.seconds)

          result mustBe Employments(Seq(cachedEmployment))
        }
      }
    }
  }

  "employment" must {
    "return a specific employment by ID" in {

      val annualAccountCY = createAnnualAccount(taxYear = currentTaxYear)
      val annualAccountPY = createAnnualAccount(taxYear = previousTaxYear)

      val employment1Id = 4

      val emp1 = Employment(
        "TEST",
        Live,
        Some("12345"),
        LocalDate.now(),
        None,
        List(annualAccountCY, annualAccountPY),
        "",
        "",
        employment1Id,
        Some(100),
        false,
        false
      )

      val emp2 = Employment(
        "TEST1",
        Live,
        Some("123456"),
        LocalDate.now(),
        None,
        List(annualAccountCY, annualAccountPY),
        "",
        "",
        2,
        Some(100),
        false,
        false
      )

      val expectedEmployment = emp1.copy(annualAccounts = Seq(annualAccountCY))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(List(emp1, emp2)))

      val sut = testRepository(cacheConnector = mockCacheConnector)

      Await.result(sut.employment(nino, employment1Id), 5 seconds) mustBe Right(expectedEmployment)
    }
    "return Employment not found error type when there is no employment found for that ID" in {

      val annualAccountCY = createAnnualAccount(taxYear = currentTaxYear)
      val annualAccountPY = createAnnualAccount(taxYear = previousTaxYear)

      val emp1 = Employment(
        "TEST",
        Live,
        Some("12345"),
        LocalDate.now(),
        None,
        List(annualAccountCY, annualAccountPY),
        "",
        "",
        4,
        Some(100),
        false,
        false
      )

      val emp2 = Employment(
        "TEST1",
        Live,
        Some("123456"),
        LocalDate.now(),
        None,
        List(annualAccountCY, annualAccountPY),
        "",
        "",
        2,
        Some(100),
        false,
        false
      )

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(List(emp1, emp2)))

      val notFoundEmploymentId = 5

      val sut = testRepository(cacheConnector = mockCacheConnector)
      Await.result(sut.employment(nino, notFoundEmploymentId), 5 seconds) mustBe Left(EmploymentNotFound)
    }

    "return employments with annual accounts with TemporaryUnavailable status type when RTI is temporarily unavailable" in {

      val temporaryUnavailableAnnualAccount = createAnnualAccount(TemporarilyUnavailable, taxYear = TaxYear())
      val employmentWithUnavailableAnnualAccount =
        npsSingleEmployment.copy(annualAccounts = Seq(temporaryUnavailableAnnualAccount))

      val mockRtiConnector = mock[RtiConnector]
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
        .thenReturn(Future.successful(Left(ServiceUnavailableError)))

      val mockEmploymentBuilder = mock[EmploymentBuilder]
      when(
        mockEmploymentBuilder
          .combineAccountsWithEmployments(
            any(),
            any(),
            meq(nino),
            meq(TaxYear())
          )(any()))
        .thenReturn(Employments(Seq(employmentWithUnavailableAnnualAccount)))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(Seq(employmentWithUnavailableAnnualAccount)))
      when(
        mockCacheConnector
          .createOrUpdateSeq[Employment](any(), any(), any())(any()))
        .thenReturn(Future.successful(Seq(employmentWithUnavailableAnnualAccount)))

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
        .thenReturn(Future.successful(getJson("npsSingleEmployment")))

      val sut =
        testRepository(
          rtiConnector = mockRtiConnector,
          cacheConnector = mockCacheConnector,
          npsConnector = mockNpsConnector,
          employmentBuilder = mockEmploymentBuilder)

      Await.result(sut.employment(nino, 2), 5 seconds) mustBe Right(employmentWithUnavailableAnnualAccount)
    }

    "get the current year employments from the hod" when {
      "data is in the cache for a year other than the current one and it does not contain the required employment" in {

        val annualAccount2018 = createAnnualAccount(taxYear = TaxYear(2018))
        val annualAccountPY = createAnnualAccount(taxYear = previousTaxYear)

        val emp2015 = Employment(
          "TEST",
          Live,
          Some("12345"),
          LocalDate.now(),
          None,
          List(annualAccount2018, annualAccountPY),
          "",
          "",
          4,
          Some(100),
          false,
          false
        )

        val expectedAnnualAccount = AnnualAccount(
          "0-0-0",
          currentTaxYear,
          Available,
          List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
          List(
            EndOfTaxYearUpdate(
              new LocalDate(2016, 6, 17),
              List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
        )

        val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))

        val mockEmploymentBuilder = mock[EmploymentBuilder]
        when(
          mockEmploymentBuilder
            .combineAccountsWithEmployments(
              any(),
              any(),
              meq(nino),
              meq(TaxYear())
            )(any()))
          .thenReturn(Employments(Seq(expectedEmployment)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(List(emp2015)))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
          .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

        val controller = testRepository(
          rtiConnector = mockRtiConnector,
          cacheConnector = mockCacheConnector,
          npsConnector = mockNpsConnector,
          employmentBuilder = mockEmploymentBuilder)

        val result = Await.result(controller.employment(nino, 2), 5 seconds)
        result mustBe Right(expectedEmployment)

        verify(mockNpsConnector, times(1))
          .getEmploymentDetails(meq(nino), meq(TaxYear().year))(any())
      }
    }
  }

  private def testRepository(
    rtiConnector: RtiConnector = mock[RtiConnector],
    cacheConnector: CacheConnector = mock[CacheConnector],
    npsConnector: NpsConnector = mock[NpsConnector],
    employmentBuilder: EmploymentBuilder = mock[EmploymentBuilder]): EmploymentRepository =
    new EmploymentRepository(rtiConnector, cacheConnector, npsConnector, employmentBuilder)

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentRepositoryTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    Json.parse(source.mkString(""))
  }
}

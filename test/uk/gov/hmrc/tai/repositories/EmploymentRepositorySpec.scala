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

package uk.gov.hmrc.tai.repositories

import java.io.File

import org.joda.time.LocalDate
import org.mockito.Matchers.{any, eq => Meq}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, EndOfTaxYearUpdate, _}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.util.Random

class EmploymentRepositorySpec extends PlaySpec with MockitoSugar {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev

  val npsSingleEmployment = Employment(
    "EMPLOYER1",
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

  def createStubbedAnnualAccount(
    rtiStatus: RealTimeStatus = Available,
    key: String = "0-0-0",
    taxYear: TaxYear = TaxYear(2017)): AnnualAccount =
    AnnualAccount(key, taxYear, rtiStatus, Nil, Nil)

  "employmentsForYear" should {
    "return the employment domain model" when {
      "there is no data in the cache" when {
        "a call to rti results in an Unavailable response" in {

          val annualAccount = createStubbedAnnualAccount(Unavailable)
          val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(annualAccount))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Left(Unavailable)))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(
            mockCacheConnector.createOrUpdateSeq[Employment](
              Matchers.eq(CacheId(nino)),
              Matchers.eq(Seq(expectedEmployment)),
              any())(any()))
            .thenReturn(Future.successful(Seq(expectedEmployment)))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder.combineAccountsWithEmployments(
              Matchers.eq(Seq(npsSingleEmployment)),
              Matchers.eq(Seq(annualAccount)),
              Matchers.eq(nino),
              Matchers.eq(TaxYear(2017)))(any()))
            .thenReturn(Employments(Seq(expectedEmployment)))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            mockEmploymentBuilder)

          val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5 seconds)

          result mustBe Seq(expectedEmployment)

          verify(mockCacheConnector, times(1))
            .createOrUpdateSeq[Employment](
              Matchers.eq(CacheId(nino)),
              Matchers.eq(Seq(expectedEmployment)),
              Matchers.eq("EmploymentData"))(any())
        }

        "a call to rti results in an TemporarilyUnavailable response" in {

          val annualAccount = createStubbedAnnualAccount(TemporarilyUnavailable)
          val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(annualAccount))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Left(TemporarilyUnavailable)))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(
            mockCacheConnector.createOrUpdateSeq[Employment](
              Matchers.eq(CacheId(nino)),
              Matchers.eq(Seq(expectedEmployment)),
              any())(any()))
            .thenReturn(Future.successful(Seq(expectedEmployment)))

          val mockEmploymentBuilder = mock[EmploymentBuilder]
          when(
            mockEmploymentBuilder
              .combineAccountsWithEmployments(
                Matchers.eq(Seq(npsSingleEmployment)),
                Matchers.eq(Seq(annualAccount)),
                Matchers.eq(nino),
                Matchers.eq(TaxYear(2017)))(any()))
            .thenReturn(Employments(Seq(expectedEmployment)))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            mockEmploymentBuilder)

          val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5 seconds)

          result mustBe Seq(expectedEmployment)

          verify(mockCacheConnector, times(1))
            .createOrUpdateSeq[Employment](
              Matchers.eq(CacheId(nino)),
              Matchers.eq(Seq(expectedEmployment)),
              Matchers.eq("EmploymentData"))(any())

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
            taxYear = TaxYear(2017),
            realTimeStatus = Available,
            payments = payments,
            endOfTaxYearUpdates = eyus)

          val expectedEmployment = List(
            Employment(
              "EMPLOYER1",
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
                Matchers.eq(Seq(npsSingleEmployment)),
                Matchers.eq(Seq(annualAccount)),
                Matchers.eq(nino),
                Matchers.eq(TaxYear(2017)))(any()))
            .thenReturn(Employments(expectedEmployment))

          val mockRtiConnector = mock[RtiConnector]
          when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
            .thenReturn(Future.successful(Right(Seq(annualAccount))))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
          when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
            .thenReturn(Future.successful(expectedEmployment))

          val mockNpsConnector = mock[NpsConnector]
          when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
            .thenReturn(Future.successful(getJson("npsSingleEmployment")))

          val sut = testRepository(
            rtiConnector = mockRtiConnector,
            cacheConnector = mockCacheConnector,
            npsConnector = mockNpsConnector,
            employmentBuilder = mockEmploymentBuilder)
          val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5 seconds)

          result mustBe expectedEmployment
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
            taxYear = TaxYear(2017),
            realTimeStatus = Available,
            payments = payments,
            endOfTaxYearUpdates = eyus)

          val expectedEmploymentDetails = List(
            Employment(
              "EMPLOYER1",
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
                Matchers.eq(Seq(npsSingleEmployment)),
                Matchers.eq(Seq(annualAccount)),
                Matchers.eq(nino),
                Matchers.eq(TaxYear(2017)))(any()))
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
          val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5 seconds)
          result mustBe expectedEmploymentDetails
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
            taxYear = TaxYear(2017),
            realTimeStatus = Available,
            payments = payments1,
            endOfTaxYearUpdates = eyus1)

          val annualAccount2 = AnnualAccount(
            key = "00-00-00",
            taxYear = TaxYear(2017),
            realTimeStatus = Available,
            payments = payments2,
            endOfTaxYearUpdates = eyus2)

          val expectedEmploymentDetails = List(
            Employment(
              "EMPLOYER1",
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
                Matchers.eq(Seq(npsDualEmployment._1, npsDualEmployment._2)),
                Matchers.eq(Seq(annualAccount1, annualAccount2)),
                Matchers.eq(nino),
                Matchers.eq(TaxYear(2017))
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
          val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5 seconds)
          result mustBe expectedEmploymentDetails
        }

        "result in an exception" when {
          "data is not present in cache or the hods for the given year" in {
            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))

            val mockNpsConnector = mock[NpsConnector]
            when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
              .thenReturn(Future.failed(new NotFoundException("nothing")))

            val sut = testRepository(cacheConnector = mockCacheConnector, npsConnector = mockNpsConnector)
            the[NotFoundException] thrownBy Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
          }
        }
      }

      "data is present in cache " when {
        "there is only one current year employment in the cache" when {
          "the request is for the current year the cached employment is passed back" in {

            val employment = Seq(
              Employment(
                "EMPLOYER1",
                Some("12345"),
                LocalDate.now(),
                None,
                List(AnnualAccount("0", TaxYear(2017), Available, Nil, Nil)),
                "",
                "",
                2,
                Some(100),
                false,
                false))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(employment))
            val mockNpsConnector = mock[NpsConnector]
            val mockRtiConnector = mock[RtiConnector]

            val sut = testRepository(
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              rtiConnector = mockRtiConnector)
            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)

            result mustBe employment

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockRtiConnector, times(0))
              .getPaymentsForYear(any(), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                Matchers.eq(employment),
                Matchers.eq("EmploymentData"))(any())

          }
        }

        "there are multiple employments in the cache for different tax years" when {
          "the request is for the current tax year, the current tax year employment is returned" in {

            val cyEmployment = Employment(
              "employer1",
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

            result mustBe List(cyEmployment)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockRtiConnector, times(0))
              .getPaymentsForYear(any(), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                Matchers.eq(Seq(cyEmployment)),
                Matchers.eq("EmploymentData"))(any())

          }
        }

        "the cache contains an employment with accounts for multiple tax years" when {
          "request is for a previous year the employment will be returned with just the previous year annual account" in {

            val cachedEmployments = List(
              Employment(
                "employer1",
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

            result mustBe List(expectedEmployment)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockRtiConnector, times(0))
              .getPaymentsForYear(any(), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                Matchers.eq(Seq(expectedEmployment)),
                Matchers.eq("EmploymentData"))(any())
          }
        }

        "the cache contains different employments which contain annual accounts for different tax years" when {

          val cachedEmployments = List(
            Employment(
              "TEST",
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

                result mustBe taxYearAndEmployment._2

              }
          }
        }

        "the cache contains employments but not for the requested year" when {
          "a request is made the employment returned has a different key which is added to the cache" in {

            val cachedEmployment1 = Employment(
              "employer1",
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
              TaxYear(2017),
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))
            val updatedCacheContents = cachedEmploymentsFor2018 ++ Seq(expectedEmployment)

            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  Matchers.eq(Seq(npsSingleEmployment)),
                  Matchers.eq(Seq(expectedAnnualAccount)),
                  Matchers.eq(nino),
                  Matchers.eq(TaxYear(2017))
                )(any()))
              .thenReturn(Employments(Seq(expectedEmployment)))

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

            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
            result mustBe Seq(expectedEmployment)

            verify(mockNpsConnector, times(1))
              .getEmploymentDetails(Matchers.eq(nino), Matchers.eq(2017))(any())

            verify(mockRtiConnector, times(1))
              .getPaymentsForYear(Matchers.eq(nino), Matchers.eq(TaxYear(2017)))(any())

            verify(mockCacheConnector, times(1))
              .findSeq[Employment](Matchers.eq(CacheId(nino)), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                employmentsCaptor.capture(),
                Matchers.eq("EmploymentData"))(any())

            val cachedEmployments = employmentsCaptor.getValue
            cachedEmployments.size mustBe 3

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
              TaxYear(2017),
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))
            val cacheUpdatedEmployment1 =
              npsSingleEmployment.copy(annualAccounts = Seq(annualAccount1, expectedAnnualAccount))

            val updatedCacheContents = Seq(employment2, cacheUpdatedEmployment1)
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  Matchers.eq(Seq(npsSingleEmployment)),
                  Matchers.eq(Seq(expectedAnnualAccount)),
                  Matchers.eq(nino),
                  Matchers.eq(TaxYear(2017))
                )(any()))
              .thenReturn(Employments(Seq(expectedEmployment)))

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

            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
            result mustBe Seq(expectedEmployment)

            verify(mockNpsConnector, times(1))
              .getEmploymentDetails(Matchers.eq(nino), Matchers.eq(2017))(any())

            verify(mockRtiConnector, times(1))
              .getPaymentsForYear(Matchers.eq(nino), Matchers.eq(TaxYear(2017)))(any())

            verify(mockCacheConnector, times(1))
              .findSeq[Employment](Matchers.eq(CacheId(nino)), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                employmentsCaptor.capture(),
                Matchers.eq("EmploymentData"))(any())

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

            val cachedAnnualAccount = createStubbedAnnualAccount(TemporarilyUnavailable)
            val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount))

            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              TaxYear(2017),
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  Matchers.eq(Seq(cachedEmployment)),
                  Matchers.eq(Seq(expectedAnnualAccount)),
                  Matchers.eq(nino),
                  Matchers.eq(TaxYear(2017))
                )(any()))
              .thenReturn(Employments(Seq(expectedEmployment)))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cachedEmployment)))
            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(Seq(expectedEmployment)))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
            result mustBe Seq(expectedEmployment)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                employmentsCaptor.capture(),
                Matchers.eq("EmploymentData"))(any())

            employmentsCaptor.getValue mustBe Seq(expectedEmployment)

          }

          "a subsequent call is made to RTI and an AnnualAccount with a status of Unavailable is returned and the stubbed account" +
            "is removed from the cache" in {

            val cachedAnnualAccount = createStubbedAnnualAccount(TemporarilyUnavailable, "00")
            val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount))
            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              TaxYear(2017),
              Unavailable,
              List(),
              List()
            )

            val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  Matchers.eq(Seq(cachedEmployment)),
                  Matchers.eq(Seq(expectedAnnualAccount)),
                  Matchers.eq(nino),
                  Matchers.eq(TaxYear(2017))
                )(any()))
              .thenReturn(Employments(Seq(expectedEmployment)))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cachedEmployment)))
            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(Seq(expectedEmployment)))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))

            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
            result.map(_ mustBe expectedEmployment)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                employmentsCaptor.capture(),
                Matchers.eq("EmploymentData"))(any())

            employmentsCaptor.getValue mustBe Seq(expectedEmployment)

          }

          "a subsequent call is made to RTI and an AnnualAccount with TemporarilyUnavailable is returned" in {

            val tempUnavailableAccount = createStubbedAnnualAccount(TemporarilyUnavailable, "00")
            val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(tempUnavailableAccount))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(List(cachedEmployment)))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Left(TemporarilyUnavailable)))

            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector)

            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
            result mustBe Seq(cachedEmployment)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockCacheConnector, times(0))
              .createOrUpdateSeq[Employment](Matchers.eq(CacheId(nino)), any(), Matchers.eq("EmploymentData"))(any())
          }
        }

        "the cache data contains an employment with two annual accounts for different years with a status of TemporarilyUnavailable" when {
          "a subsequent request is made to RTI a status of available is returned, one annual account should be updated and the other left unmodified " in {

            val cachedAnnualAccount1 = createStubbedAnnualAccount(TemporarilyUnavailable)
            val cachedAnnualAccount2 =
              createStubbedAnnualAccount(rtiStatus = TemporarilyUnavailable, taxYear = TaxYear(2016))
            val cachedEmployment =
              npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount1, cachedAnnualAccount2))
            val expectedAnnualAccount = AnnualAccount(
              "0-0-0",
              TaxYear(2017),
              Available,
              List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
              List(
                EndOfTaxYearUpdate(
                  new LocalDate(2016, 6, 17),
                  List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
            )

            val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))
            val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

            val mockEmploymentBuilder = mock[EmploymentBuilder]
            when(
              mockEmploymentBuilder
                .combineAccountsWithEmployments(
                  any(),
                  Matchers.eq(Seq(expectedAnnualAccount)),
                  Matchers.eq(nino),
                  Matchers.eq(TaxYear(2017))
                )(any()))
              .thenReturn(Employments(Seq(expectedEmployment)))

            val mockCacheConnector = mock[CacheConnector]
            when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
              .thenReturn(Future.successful(Seq(cachedEmployment)))
            when(mockCacheConnector.createOrUpdateSeq[Employment](any(), any(), any())(any()))
              .thenReturn(Future.successful(Seq(expectedEmployment)))

            val mockRtiConnector = mock[RtiConnector]
            when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
              .thenReturn(Future.successful(Right(Seq(expectedAnnualAccount))))
            val mockNpsConnector = mock[NpsConnector]

            val sut = testRepository(
              rtiConnector = mockRtiConnector,
              cacheConnector = mockCacheConnector,
              npsConnector = mockNpsConnector,
              employmentBuilder = mockEmploymentBuilder)

            val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
            result mustBe Seq(expectedEmployment)

            verify(mockNpsConnector, times(0))
              .getEmploymentDetails(Matchers.eq(nino), any())(any())

            verify(mockCacheConnector, times(1))
              .createOrUpdateSeq[Employment](
                Matchers.eq(CacheId(nino)),
                employmentsCaptor.capture(),
                Matchers.eq("EmploymentData"))(any())

            val actualCached = employmentsCaptor.getValue
            actualCached.size mustBe 1
            val cachedEmp1 = actualCached.filter(_.name == "EMPLOYER1")

            cachedEmp1.flatMap(_.annualAccounts) mustBe Seq(expectedAnnualAccount, cachedAnnualAccount2)
          }
        }

        "the cached data contains an annualAccount with a status not equal to TemporarilyUnavailable" in {

          val cachedAnnualAccount = createStubbedAnnualAccount(Available, "00")
          val cachedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(cachedAnnualAccount))

          val mockCacheConnector = mock[CacheConnector]
          when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
            .thenReturn(Future.successful(Seq(cachedEmployment)))

          val sut = testRepository(cacheConnector = mockCacheConnector)
          val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)

          result mustBe Seq(cachedEmployment)
        }
      }
    }
  }

  "employment" must {
    "return a specific employment by ID" in {
      val emp1 = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(
          AnnualAccount("", currentTaxYear, Available, Nil, Nil),
          AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
        "",
        "",
        4,
        Some(100),
        false,
        false
      )

      val emp2 = Employment(
        "TEST1",
        Some("123456"),
        LocalDate.now(),
        None,
        List(
          AnnualAccount("", currentTaxYear, Unavailable, Nil, Nil),
          AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false
      )

      val expectedEmployment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", currentTaxYear, Available, Nil, Nil)),
        "",
        "",
        4,
        Some(100),
        false,
        false
      )

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(List(emp1, emp2)))

      val employmentId = 4
      val sut = testRepository(cacheConnector = mockCacheConnector)

      Await.result(sut.employment(nino, employmentId), 5 seconds) mustBe Right(expectedEmployment)
    }
    "return Employment not found error type when there is no employment found for that ID" in {
      val emp1 = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(
          AnnualAccount("", currentTaxYear, Available, Nil, Nil),
          AnnualAccount("", previousTaxYear, TemporarilyUnavailable, Nil, Nil)),
        "",
        "",
        4,
        Some(100),
        false,
        false
      )

      val emp2 = Employment(
        "TEST1",
        Some("123456"),
        LocalDate.now(),
        None,
        List(
          AnnualAccount("", currentTaxYear, Unavailable, Nil, Nil),
          AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
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

      val sut = testRepository(cacheConnector = mockCacheConnector)
      Await.result(sut.employment(nino, 10), 5 seconds) mustBe Left(EmploymentNotFound)
    }

    "return Employment stubbed account error type when RTI is Temporarily unavailable" in {

      val annualAccount = createStubbedAnnualAccount(TemporarilyUnavailable, taxYear = TaxYear())
      val employment = npsSingleEmployment.copy(annualAccounts = Seq(annualAccount))

      val mockRtiConnector = mock[RtiConnector]
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any()))
        .thenReturn(Future.successful(Left(TemporarilyUnavailable)))

      val mockEmploymentBuilder = mock[EmploymentBuilder]
      when(
        mockEmploymentBuilder
          .combineAccountsWithEmployments(
            any(),
            any(),
            Matchers.eq(nino),
            Matchers.eq(TaxYear())
          )(any()))
        .thenReturn(Employments(Seq(employment)))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Seq(employment)))
      when(
        mockCacheConnector
          .createOrUpdateSeq[Employment](any(), any(), any())(any()))
        .thenReturn(Future.successful(Seq(employment)))

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
        .thenReturn(Future.successful(getJson("npsSingleEmployment")))

      val sut =
        testRepository(
          rtiConnector = mockRtiConnector,
          cacheConnector = mockCacheConnector,
          npsConnector = mockNpsConnector,
          employmentBuilder = mockEmploymentBuilder)

      Await.result(sut.employment(nino, 2), 5 seconds) mustBe Left(EmploymentAccountStubbed)
    }

    "get the current year employments from the hod" when {
      "data is in the cache for a year other than the current one and it does not contain the required employment" in {

        val emp2015 = Employment(
          "TEST",
          Some("12345"),
          LocalDate.now(),
          None,
          List(
            AnnualAccount("", TaxYear(2015), Available, Nil, Nil),
            AnnualAccount("", previousTaxYear, TemporarilyUnavailable, Nil, Nil)),
          "",
          "",
          4,
          Some(100),
          false,
          false
        )

        val expectedAnnualAccount = AnnualAccount(
          "0-0-0",
          TaxYear(2017),
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
              Matchers.eq(nino),
              Matchers.eq(TaxYear())
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
          .getEmploymentDetails(org.mockito.Matchers.eq(nino), org.mockito.Matchers.eq(TaxYear().year))(any())
      }
    }
  }

  private val nino = new Generator(new Random).nextNino
  private val cacheId = CacheId(nino)

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

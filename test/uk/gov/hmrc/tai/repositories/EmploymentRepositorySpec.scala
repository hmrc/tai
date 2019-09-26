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

package uk.gov.hmrc.tai.repositories

import java.io.File

import org.joda.time.LocalDate
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, NotFoundException}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CacheConnector, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.TaiRoot
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, EndOfTaxYearUpdate, _}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.util.Random

class EmploymentRepositorySpec extends PlaySpec with MockitoSugar {

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TESTING")))
  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev

  "stubAccounts" should {

    "generate a stubbed AnnualAccount instance with appropriate status, for each known Employment" when {
      val ty = TaxYear(2017)
      val employments = List(
        Employment("TEST", Some("12345"), LocalDate.now(), None, Nil, "tdNo", "payeNumber", 1, Some(100), false, false),
        Employment("TEST", Some("12346"), LocalDate.now(), None, Nil, "tdNo", "payeNumber", 2, Some(100), false, false)
      )

      "account retrieval has failed with http response code of 404" in {
        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val accounts = sut.stubAccounts(404, employments, ty)

        accounts mustBe List(
          AnnualAccount("tdNo-payeNumber-12345", ty, Unavailable, Nil, Nil),
          AnnualAccount("tdNo-payeNumber-12346", ty, Unavailable, Nil, Nil))
      }

      "account retrieval has failed with http response code of other tham 404" in {
        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val accounts = sut.stubAccounts(500, employments, ty)

        accounts mustBe List(
          AnnualAccount("tdNo-payeNumber-12345", ty, TemporarilyUnavailable, Nil, Nil),
          AnnualAccount("tdNo-payeNumber-12346", ty, TemporarilyUnavailable, Nil, Nil))
      }
    }
  }

  "unifiedEmployments" should {

    "unify stubbed Employment instances (having Nil accounts), with their corrsesponding AnnualAccount instances" when {

      "each AnnualAccount record has a single matching Employment record by employer designation, " +
        "i.e. taxDistrictNumber and payeNumber match AnnualAccount officeNo and payeRef values respectively. " +
        "(The match is unambiguous - payroll need not figure.)" in {

        val employmentsNoPayroll = List(
          Employment(
            "TestEmp1",
            None,
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false),
          Employment(
            "TestEmp2",
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false)
        )

        val accounts = List(
          AnnualAccount("taxDistrict1-payeRefemployer1-payrollNo88", TaxYear(2017), Available, Nil, Nil),
          AnnualAccount("taxDistrict2-payeRefemployer2", TaxYear(2017), Available, Nil, Nil)
        )

        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val unifiedEmployments = sut.unifiedEmployments(employmentsNoPayroll, accounts, nino, TaxYear(2017))

        unifiedEmployments must contain(
          Employment(
            "TestEmp1",
            None,
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("taxDistrict1-payeRefemployer1-payrollNo88", TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false
          ))

        unifiedEmployments must contain(
          Employment(
            "TestEmp2",
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("taxDistrict2-payeRefemployer2", TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false
          ))

        unifiedEmployments.size mustBe 2
      }

      "an AnnualAccount record has more than one Employment record that matches by employer designation, " +
        "but one of them also matches by payrollNumber (employee designation)" in {

        val employmentsNoPayroll = List(
          Employment(
            "TestEmp1",
            Some("payrollNo88"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false),
          Employment(
            "TestEmp1",
            Some("payrollNo14"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict1",
            "payeRefemployer1",
            2,
            Some(100),
            false,
            false)
        )

        val accounts =
          List(AnnualAccount("taxDistrict1-payeRefemployer1-payrollNo88", TaxYear(2017), Available, Nil, Nil))

        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val unifiedEmployments = sut.unifiedEmployments(employmentsNoPayroll, accounts, nino, TaxYear(2017))

        unifiedEmployments must contain(
          Employment(
            "TestEmp1",
            Some("payrollNo88"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("taxDistrict1-payeRefemployer1-payrollNo88", TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false
          )
        )
      }

      "multiple AnnualAccount records match the same employment record by employer designation" in {
        val employmentsNoPayroll = List(
          Employment(
            "TestEmp1",
            Some("payrollNo88"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false),
          Employment(
            "TestEmp2",
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false)
        )

        val accounts = List(
          AnnualAccount("taxDistrict1-payeRefemployer1", TaxYear(2017), Available, Nil, Nil),
          AnnualAccount("taxDistrict1-payeRefemployer1-payrollNo88", TaxYear(2017), Available, Nil, Nil),
          AnnualAccount("taxDistrict2-payeRefemployer2-payrollNo1", TaxYear(2017), Available, Nil, Nil)
        )

        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val unifiedEmployments = sut.unifiedEmployments(employmentsNoPayroll, accounts, nino, TaxYear(2017))

        unifiedEmployments must contain(
          Employment(
            "TestEmp1",
            Some("payrollNo88"),
            LocalDate.parse("2017-07-24"),
            None,
            List(
              AnnualAccount("taxDistrict1-payeRefemployer1", TaxYear(2017), Available, Nil, Nil),
              AnnualAccount("taxDistrict1-payeRefemployer1-payrollNo88", TaxYear(2017), Available, Nil, Nil)
            ),
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false
          )
        )

        unifiedEmployments must contain(
          Employment(
            "TestEmp2",
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("taxDistrict2-payeRefemployer2-payrollNo1", TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false
          )
        )

        unifiedEmployments.size mustBe 2
      }
    }

    "unify stubbed Employment instances (having Nil accounts) with placeholder 'Unavailable' AnnualAccount instances" when {
      val ty = TaxYear(2017)

      "one of the known employments has no corresponding AnnualAccount" in {
        val employments = List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false),
          Employment(
            "TEST",
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false)
        )

        val accounts = List(
          AnnualAccount("tdNo-payeNumber-12345", ty, Available, Nil, Nil),
          AnnualAccount("tdNo-payeNumber-77777", ty, Available, Nil, Nil))

        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val unified = sut.unifiedEmployments(employments, accounts, nino, ty)

        unified mustBe List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-12345", ty, Available, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false
          ),
          Employment(
            "TEST",
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-12346", ty, Unavailable, Nil, Nil)),
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false
          )
        )
      }

      "no AnnualAccount records are available" in {
        val employments = List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false),
          Employment(
            "TEST",
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false)
        )

        val accounts = Nil

        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val unified = sut.unifiedEmployments(employments, accounts, nino, ty)

        unified mustBe List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-12345", ty, Unavailable, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false
          ),
          Employment(
            "TEST",
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-12346", ty, Unavailable, Nil, Nil)),
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false
          )
        )
      }

      "multiple AnnualAccounts exist for one employment record, another record has no corresponding account records, " +
        "and one of the account records matches none of the employment records" in {
        val employments = List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false),
          Employment(
            "TEST",
            Some("88888"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false)
        )

        val accounts = List(
          AnnualAccount("tdNo-payeNumber-12345", ty, Available, Nil, Nil),
          AnnualAccount("tdNo-payeNumber-12345", ty, Available, Nil, Nil),
          AnnualAccount("tdNo-payeNumber-77777", ty, Available, Nil, Nil)
        )

        val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])
        val unified = sut.unifiedEmployments(employments, accounts, nino, ty)

        unified mustBe List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(
              AnnualAccount("tdNo-payeNumber-12345", ty, Available, Nil, Nil),
              AnnualAccount("tdNo-payeNumber-12345", ty, Available, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false
          ),
          Employment(
            "TEST",
            Some("88888"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-88888", ty, Unavailable, Nil, Nil)),
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false
          )
        )
      }
    }
  }

  "checkAndUpdateCache" should {
    "cache supplied data" when {
      "no data is currently in cache" in {
        val employment = Seq(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.now(),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(employment), 5.seconds)

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq("TESTING"), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](Matchers.eq("TESTING"), any[Seq[Employment]](), Matchers.eq("EmploymentData"))(
            any())
      }
    }

    "append supplied data to existing cached data" when {
      val pty = TaxYear().prev
      val cty = TaxYear()
      val cachedEmployments = List(
        Employment(
          "TEST",
          Some("12345"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12345", cty, Available, Nil, Nil)),
          "tdNo",
          "payeNumber",
          1,
          Some(100),
          false,
          false
        ),
        Employment(
          "TEST",
          Some("12346"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, Available, Nil, Nil)),
          "tdNo",
          "payeNumber",
          2,
          Some(100),
          false,
          false
        )
      )

      "supplied employment data matches one of the cached employments by key" in {
        val employment = Seq(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-12345", pty, Available, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false
          ))

        val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(cachedEmployments))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(employment), 5.seconds)

        val expectedEmp1 = Employment(
          "TEST",
          Some("12346"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, Available, Nil, Nil)),
          "tdNo",
          "payeNumber",
          2,
          Some(100),
          false,
          false
        )
        val expectedEmp2 = Employment(
          "TEST",
          Some("12345"),
          LocalDate.parse("2017-07-24"),
          None,
          List(
            AnnualAccount("tdNo-payeNumber-12345", pty, Available, Nil, Nil),
            AnnualAccount("tdNo-payeNumber-12345", cty, Available, Nil, Nil)),
          "tdNo",
          "payeNumber",
          1,
          Some(100),
          false,
          false
        )

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq("TESTING"), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](
            Matchers.eq("TESTING"),
            employmentsCaptor.capture(),
            Matchers.eq("EmploymentData"))(any())

        employmentsCaptor.getValue must contain(expectedEmp1)
        employmentsCaptor.getValue must contain(expectedEmp2)
      }

      "supplied employment does not match any of the cached employments by key" in {
        val employment = Seq(
          Employment(
            "TEST",
            Some("77777"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount("tdNo-payeNumber-77777", pty, Available, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false
          ))

        val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(cachedEmployments))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(employment), 5.seconds)

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq("TESTING"), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](
            Matchers.eq("TESTING"),
            employmentsCaptor.capture(),
            Matchers.eq("EmploymentData"))(any())

        val actualCached = employmentsCaptor.getValue
        actualCached must contain(cachedEmployments.head)
        actualCached must contain(cachedEmployments.last)
        actualCached must contain(employment.head)
      }
    }

    "selectively ignore supplied AnnualAccount data" when {

      "real time status is TemporarilyUnavailable" in {
        val cty = TaxYear()

        val emp1 = Employment(
          "TEST",
          Some("12345"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12345", cty, Available, Nil, Nil)),
          "tdNo",
          "payeNumber",
          1,
          Some(100),
          false,
          false
        )
        val emp2 = Employment(
          "TEST",
          Some("12346"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, Unavailable, Nil, Nil)),
          "tdNo",
          "payeNumber",
          2,
          Some(100),
          false,
          false
        )
        val emp3 = Employment(
          "TEST",
          Some("12347"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, Available, Nil, Nil)),
          "tdNo",
          "payeNumber",
          3,
          Some(100),
          false,
          false
        )
        val emp4 = Employment(
          "TEST",
          Some("12348"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, TemporarilyUnavailable, Nil, Nil)),
          "tdNo",
          "payeNumber",
          4,
          Some(100),
          false,
          false
        )

        val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(List(emp1, emp2, emp3, emp4)), 5.seconds)

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq("TESTING"), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](
            Matchers.eq("TESTING"),
            employmentsCaptor.capture(),
            Matchers.eq("EmploymentData"))(any())

        val actualCached = employmentsCaptor.getValue
        actualCached must contain(emp1)
        actualCached must contain(emp2)
        actualCached must contain(emp3)
        actualCached must not contain (emp4)
        actualCached.size mustBe 3
      }
    }

    "bypass caching entirely" when {

      "all records are found to have realtime status of TemporarilyUnavailable" in {
        val cty = TaxYear()

        val emp1 = Employment(
          "TEST",
          Some("12345"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12345", cty, TemporarilyUnavailable, Nil, Nil)),
          "tdNo",
          "payeNumber",
          1,
          Some(100),
          false,
          false
        )
        val emp2 = Employment(
          "TEST",
          Some("12346"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, TemporarilyUnavailable, Nil, Nil)),
          "tdNo",
          "payeNumber",
          2,
          Some(100),
          false,
          false
        )
        val emp3 = Employment(
          "TEST",
          Some("12347"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, TemporarilyUnavailable, Nil, Nil)),
          "tdNo",
          "payeNumber",
          3,
          Some(100),
          false,
          false
        )
        val emp4 = Employment(
          "TEST",
          Some("12348"),
          LocalDate.parse("2017-07-24"),
          None,
          List(AnnualAccount("tdNo-payeNumber-12346", cty, TemporarilyUnavailable, Nil, Nil)),
          "tdNo",
          "payeNumber",
          4,
          Some(100),
          false,
          false
        )

        val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(List(emp1, emp2, emp3, emp4)), 5.seconds)

        verify(mockCacheConnector, times(0))
          .findSeq[Employment](Matchers.eq("TESTING"), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(0))
          .createOrUpdateSeq[Employment](Matchers.eq("TESTING"), any(), Matchers.eq("EmploymentData"))(any())
      }
    }
  }

  "employmentsFromHod" should {
    "set the real time status of the annual account" when {
      "rti data is unavailable" in {
        val annualAccountRtiDown = Seq(
          AnnualAccount(
            key = "0-0-0",
            taxYear = TaxYear(2017),
            realTimeStatus = Unavailable,
            payments = Nil,
            endOfTaxYearUpdates = Nil))

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.failed(new HttpException("rti down", 404)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result: Seq[Employment] = Await.result(sut.employmentsFromHod(nino, TaxYear(2017))(hc), 5 seconds)

        result.map(emp => emp.annualAccounts mustBe annualAccountRtiDown)
      }

      "rti is temporarily unavailable" in {
        val annualAccountRtiTempDown = Seq(
          AnnualAccount(
            key = "0-0-0",
            taxYear = TaxYear(2017),
            realTimeStatus = TemporarilyUnavailable,
            payments = Nil,
            endOfTaxYearUpdates = Nil))

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.failed(new HttpException("rti down", 500)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsFromHod(nino, TaxYear(2017))(hc), 5 seconds)

        result.map(emp => emp.annualAccounts mustBe annualAccountRtiTempDown)
      }
    }

    "Update cache with successfully retrieved 'Available' status data" in {
      val mockRtiConnector = mock[RtiConnector]
      when(mockRtiConnector.getRTIDetails(any(), any())(any()))
        .thenReturn(Future.successful(getJson("rtiDualEmploymentDualPayment")))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
      when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
        .thenReturn(Future.successful(getJson("npsDualEmployments")))

      val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
      val employments = Await.result(sut.employmentsFromHod(nino, TaxYear(2017)), 5.seconds)

      verify(mockCacheConnector, times(1))
        .findSeq[Employment](Matchers.eq("TESTING"), Matchers.eq("EmploymentData"))(any())
      verify(mockCacheConnector, times(1))
        .createOrUpdateSeq[Employment](Matchers.eq("TESTING"), Matchers.eq(employments), Matchers.eq("EmploymentData"))(
          any())
    }
  }

  "monitorAndAuditAssociatedEmployment" should {
    "return the supplied Employment option" in {
      val emp = Some(
        Employment(
          "EMPLOYER1",
          Some("12345"),
          LocalDate.parse("2017-07-24"),
          None,
          Nil,
          "tdNo",
          "payeNumber",
          1,
          Some(100),
          false,
          false))
      val cyEmployment = Employment(
        "EMPLOYER1",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", currentTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false)
      val pyEmployment = Employment(
        "EMPLOYER2",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false)
      val account = AnnualAccount("", currentTaxYear, Available, Nil, Nil)
      val employmentsForYear = List(cyEmployment, pyEmployment)

      val sut = createSUT(mock[RtiConnector], mock[CacheConnector], mock[NpsConnector], mock[Auditor])

      sut.monitorAndAuditAssociatedEmployment(emp, account, employmentsForYear, nino.nino, "2017") mustBe emp
      sut.monitorAndAuditAssociatedEmployment(None, account, employmentsForYear, nino.nino, "2017") mustBe None
    }
  }

  "employmentsForYear" should {

    "return the employment domain model" when {
      "data is present in cache for the same year" in {
        val employmentsForYear = List(
          Employment(
            "EMPLOYER1",
            Some("12345"),
            LocalDate.now(),
            None,
            List(AnnualAccount("", TaxYear(2017), Available, Nil, Nil)),
            "",
            "",
            2,
            Some(100),
            false,
            false))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(employmentsForYear))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
        val employment = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5.seconds)

        employment mustBe employmentsForYear
      }

      "data is not present in cache for the given year and there is no annualAccount data" in {
        val unavailPlaceholderAccount = List(AnnualAccount("0-0-0", TaxYear(2017), Unavailable, Nil, Nil))

        val employmentsForYear = List(
          Employment(
            "EMPLOYER1",
            Some("0"),
            new LocalDate(2016, 4, 6),
            None,
            unavailPlaceholderAccount,
            "0",
            "0",
            2,
            None,
            false,
            false))

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.failed(new HttpException("data not found", 404)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe employmentsForYear
      }

      "data is not present in cache, there is no annualAccountData, and there are two employments returned from the hods for the given year" in {
        val unavailPlaceholderAccount1 = List(AnnualAccount("0-0-0", TaxYear(2017), Unavailable, Nil, Nil))
        val unavailPlaceholderAccount2 = List(AnnualAccount("00-00-00", TaxYear(2017), Unavailable, Nil, Nil))

        val employmentsForYear = List(
          Employment(
            "EMPLOYER1",
            Some("0"),
            new LocalDate(2016, 4, 6),
            None,
            unavailPlaceholderAccount1,
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
            unavailPlaceholderAccount2,
            "00",
            "00",
            2,
            Some(100),
            true,
            false)
        )

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.failed(new HttpException("data not found", 404)))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsDualEmployments")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe employmentsForYear
      }

      "data is not present in cache for the given year, and data from hods includes corresponding annual account data " +
        "(a single payment, and a single end tax year update)" in {
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

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("rtiSingleEmploymentSinglePayment")))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe expectedEmploymentDetails
      }

      "data is not present in cache for the given year, and data from hods includes corresponding annual account data " +
        "(a single payment, and two end tax year updates)" in {
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

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("rtiSingleEmploymentSinglePaymentDualEyu")))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe expectedEmploymentDetails
      }

      "data is not present in cache for the given year, and data from hods includes corresponding annual account data " +
        "(two employments, each with a single payment, and a single end tax year update)" in {
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

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("rtiDualEmploymentDualPayment")))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsDualEmployments")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe expectedEmploymentDetails
      }
    }

    "result in an exception" when {
      "data is not present in cache or the hods for the given year" in {
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("nothing")))

        val sut = createSUT(mock[RtiConnector], mockCacheConnector, mockNpsConnector, mock[Auditor])
        the[NotFoundException] thrownBy Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5.seconds)
      }
    }

    "return the employment for passed year" in {
      val cyEmployment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", currentTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false)
      val pyEmployment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false)

      val employmentsForYear = List(cyEmployment, pyEmployment)

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(employmentsForYear))

      val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
      val cyEmploymentDetails = Await.result(sut.employmentsForYear(nino, currentTaxYear)(hc), 5.seconds)
      val pyEmploymentDetails = Await.result(sut.employmentsForYear(nino, previousTaxYear)(hc), 5.seconds)

      cyEmploymentDetails mustBe List(cyEmployment)
      pyEmploymentDetails mustBe List(pyEmployment)
    }

    "return the employment for previous year only" in {
      val pyEmployment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false)

      val cyEmployment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        List(AnnualAccount("", currentTaxYear, Available, Nil, Nil)),
        "",
        "",
        2,
        Some(100),
        false,
        false)

      val employmentsForYear = List(
        Employment(
          "TEST",
          Some("12345"),
          LocalDate.now(),
          None,
          List(
            AnnualAccount("", currentTaxYear, Available, Nil, Nil),
            AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
          "",
          "",
          2,
          Some(100),
          false,
          false
        ),
        cyEmployment
      )

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(employmentsForYear))

      val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
      val pyEmploymentDetails = Await.result(sut.employmentsForYear(nino, previousTaxYear)(hc), 5.seconds)

      pyEmploymentDetails mustBe List(pyEmployment)
    }

    "return sequence of employment for passed year" in {
      val cyEmployment = List(
        Employment(
          "TEST",
          Some("12345"),
          LocalDate.now(),
          None,
          List(AnnualAccount("", currentTaxYear, Available, Nil, Nil)),
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
          List(AnnualAccount("", currentTaxYear, Unavailable, Nil, Nil)),
          "",
          "",
          2,
          Some(100),
          false,
          false)
      )

      val pyEmployment = List(
        Employment(
          "TEST",
          Some("12345"),
          LocalDate.now(),
          None,
          List(AnnualAccount("", previousTaxYear, TemporarilyUnavailable, Nil, Nil)),
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
          List(AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
          "",
          "",
          2,
          Some(100),
          false,
          false)
      )

      val employmentsForYear = List(
        Employment(
          "TEST",
          Some("12345"),
          LocalDate.now(),
          None,
          List(
            AnnualAccount("", currentTaxYear, Available, Nil, Nil),
            AnnualAccount("", previousTaxYear, TemporarilyUnavailable, Nil, Nil)),
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
            AnnualAccount("", currentTaxYear, Unavailable, Nil, Nil),
            AnnualAccount("", previousTaxYear, Available, Nil, Nil)),
          "",
          "",
          2,
          Some(100),
          false,
          false
        )
      )

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(employmentsForYear))

      val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
      val cyEmploymentDetails = Await.result(sut.employmentsForYear(nino, currentTaxYear)(hc), 5.seconds)
      val pyEmploymentDetails = Await.result(sut.employmentsForYear(nino, previousTaxYear)(hc), 5.seconds)

      cyEmploymentDetails mustBe cyEmployment
      pyEmploymentDetails mustBe pyEmployment
    }
    "return get data from the hod" when {
      "data is in the cache, but not for the requested year" in {
        val employments2017 = List(
          Employment(
            "TEST",
            Some("12345"),
            LocalDate.now(),
            None,
            List(AnnualAccount("", TaxYear(2017), TemporarilyUnavailable, Nil, Nil)),
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
            List(AnnualAccount("", TaxYear(2017), Available, Nil, Nil)),
            "",
            "",
            2,
            Some(100),
            false,
            false)
        )

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("rtiSingleEmploymentSinglePayment")))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(employments2017))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        Await.result(sut.employmentsForYear(Nino(nino.nino), TaxYear(2015)), 5.seconds)

        verify(mockNpsConnector, times(1))
          .getEmploymentDetails(Matchers.eq(Nino(nino.nino)), Matchers.eq(2015))(any())
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
      when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(List(emp1, emp2)))

      val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
      Await.result(sut.employment(Nino(nino.nino), 4), 5 seconds) mustBe Right(emp1)
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
      when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(List(emp1, emp2)))

      val sut = createSUT(mock[RtiConnector], mockCacheConnector, mock[NpsConnector], mock[Auditor])
      Await.result(sut.employment(Nino(nino.nino), 10), 5 seconds) mustBe Left(EmploymentNotFound)
    }

    "return Employment stubbed account error type when RTI is down and there is no data in the cache" in {

      val mockRtiConnector = mock[RtiConnector]
      when(mockRtiConnector.getRTIDetails(any(), any())(any()))
        .thenReturn(Future.failed(new HttpException("rti down", 503)))

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
      when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
        .thenReturn(Future.successful(getJson("npsSingleEmployment")))

      val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
      Await.result(sut.employment(Nino(nino.nino), 10), 5 seconds) mustBe Left(EmploymentAccountStubbed)
    }

    "get the current year employments from the hod" when {
      "data is in the cache for a year other than the current one and it does not contain the required employment " in {
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

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(List(emp2015)))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val mockRtiConnector = mock[RtiConnector]
        when(mockRtiConnector.getRTIDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("rtiSingleEmploymentSinglePayment")))

        val sut = createSUT(mockRtiConnector, mockCacheConnector, mockNpsConnector, mock[Auditor])
        Await.result(sut.employment(Nino(nino.nino), 3), 5 seconds)

        verify(mockNpsConnector, times(1))
          .getEmploymentDetails(org.mockito.Matchers.eq(Nino(nino.nino)), org.mockito.Matchers.eq(TaxYear().year))(
            any())
      }
    }
  }

  private val nino: Nino = new Generator(new Random).nextNino

  private val nonGatekeeperTaiRoot = TaiRoot(
    nino = nino.nino,
    version = 0,
    title = "",
    firstName = "",
    secondName = None,
    surname = "",
    name = " ",
    manualCorrespondenceInd = false,
    deceasedIndicator = None)

  private def createSUT(
    rtiConnector: RtiConnector,
    cacheConnector: CacheConnector,
    npsConnector: NpsConnector,
    auditor: Auditor) =
    new EmploymentRepository(rtiConnector, cacheConnector, npsConnector, auditor)

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentRepositoryTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    Json.parse(source.mkString(""))
  }
}

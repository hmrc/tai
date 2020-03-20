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
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, NotFoundException}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.TaiRoot
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, EndOfTaxYearUpdate, _}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.util.Random

//TODO: Reference old tests in here for the new endpoint(s).
//TODO: Refactor Tests to reduce length. A CreateEmployment method would work wonders to simplify this.
class EmploymentRepositorySpec extends PlaySpec with MockitoSugar {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev

  "checkAndUpdateCache" should {
    "cache supplied data" when {
      "no data is currently in cache" in {
        val employment = Seq(
          Employment("TEST", Some("12345"), LocalDate.now(), None, "tdNo", "payeNumber", 1, Some(100), false, false))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(cacheId, employment), 5.seconds)

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq(cacheId), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](Matchers.eq(cacheId), any[Seq[Employment]](), Matchers.eq("EmploymentData"))(
            any())
      }
    }

    "append supplied data to existing cached data" when {
      val cachedEmployments = List(
        Employment(
          "TEST",
          Some("12345"),
          new LocalDate(2017, 7, 24),
          None,
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
          new LocalDate(2017, 7, 24),
          None,
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
            new LocalDate(2017, 7, 24),
            None,
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
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(cacheId, employment), 5.seconds)

        val expectedEmp1 = Employment(
          "TEST",
          Some("12346"),
          new LocalDate(2017, 7, 24),
          None,
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
          new LocalDate(2017, 7, 24),
          None,
          "tdNo",
          "payeNumber",
          1,
          Some(100),
          false,
          false
        )

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq(cacheId), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](
            Matchers.eq(cacheId),
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
            new LocalDate(2017, 7, 24),
            None,
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
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
        Await.result(sut.checkAndUpdateCache(cacheId, employment), 5.seconds)

        verify(mockCacheConnector, times(1))
          .findSeq[Employment](Matchers.eq(cacheId), Matchers.eq("EmploymentData"))(any())
        verify(mockCacheConnector, times(1))
          .createOrUpdateSeq[Employment](
            Matchers.eq(cacheId),
            employmentsCaptor.capture(),
            Matchers.eq("EmploymentData"))(any())

        val actualCached = employmentsCaptor.getValue
        actualCached must contain(cachedEmployments.head)
        actualCached must contain(cachedEmployments.last)
        actualCached must contain(employment.head)
      }
    }
  }

  "employmentsFromHod" should {

    "Update cache with successfully retrieved 'Available' status data" in {
      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
      when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any())).thenReturn(Future.successful(Nil))

      val mockNpsConnector = mock[NpsConnector]
      when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
        .thenReturn(Future.successful(getJson("npsDualEmployments")))

      val sut = createSUT(mockCacheConnector, mockNpsConnector, mock[Auditor])
      val employments = Await.result(sut.employmentsFromHod(nino, TaxYear(2017)), 5.seconds)

      verify(mockCacheConnector, times(1))
        .findSeq[Employment](Matchers.eq(cacheId), Matchers.eq("EmploymentData"))(any())
      verify(mockCacheConnector, times(1))
        .createOrUpdateSeq[Employment](Matchers.eq(cacheId), Matchers.eq(employments), Matchers.eq("EmploymentData"))(
          any())
    }
  }

  "employmentsForYear" should {

    "return the employment domain model" when {
      "data is present in cache for the same year" in {
        val employmentsForYear = List(
          Employment(
            "EMPLOYER1",
            Some("12345"),
            new LocalDate(2017, 4, 10),
            None,
            "",
            "",
            2,
            Some(100),
            false,
            false
          ))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(employmentsForYear))

        val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
        val employment = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5.seconds)

        employment mustBe employmentsForYear
      }

      "data is not present in cache for the given year and one employment returned from the hods" in {
        val employmentsForYear =
          List(Employment("EMPLOYER1", Some("0"), new LocalDate(2016, 4, 6), None, "0", "0", 2, None, false, false))

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe employmentsForYear
      }

      "data is not present in cache, and there are two employments returned from the hods for the given year" in {
        val employmentsForYear = List(
          Employment("EMPLOYER1", Some("0"), new LocalDate(2016, 4, 6), None, "0", "0", 1, None, false, false),
          Employment("EMPLOYER2", Some("00"), new LocalDate(2016, 4, 6), None, "00", "00", 2, Some(100), true, false)
        )

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsDualEmployments")))

        val sut = createSUT(mockCacheConnector, mockNpsConnector, mock[Auditor])
        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5 seconds)
        result mustBe employmentsForYear
      }
    }

    "result in an exception" when {
      "data is not present in cache or the hods for the given year" in {
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any())).thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.failed(new NotFoundException("nothing")))

        val sut = createSUT(mockCacheConnector, mockNpsConnector, mock[Auditor])
        the[NotFoundException] thrownBy Await.result(sut.employmentsForYear(nino, TaxYear(2017))(hc), 5.seconds)
      }
    }

    "return the employment for passed year" in {
      val cyEmployment =
        Employment("TEST", Some("12345"), currentTaxYear.start, None, "", "", 2, Some(100), false, false)
      val pyEmployment = Employment(
        "TEST",
        Some("12345"),
        previousTaxYear.start,
        Some(previousTaxYear.end),
        "",
        "",
        2,
        Some(100),
        false,
        false
      )

      val employmentsForYear = List(cyEmployment, pyEmployment)

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(employmentsForYear))

      val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
      val cyEmploymentDetails = Await.result(sut.employmentsForYear(nino, currentTaxYear)(hc), 5.seconds)
      val pyEmploymentDetails = Await.result(sut.employmentsForYear(nino, previousTaxYear)(hc), 5.seconds)

      cyEmploymentDetails mustBe List(cyEmployment)
      pyEmploymentDetails mustBe List(pyEmployment)
    }

    "return the employment for previous year only" in {
      val pyEmployment = Employment(
        "TEST",
        Some("12345"),
        previousTaxYear.start,
        Some(previousTaxYear.end),
        "",
        "",
        2,
        Some(100),
        false,
        false)

      val cyEmployment =
        Employment("TEST", Some("12345"), currentTaxYear.start, None, "", "", 2, Some(100), false, false)

      val employmentsForYear = List(
        Employment(
          "TEST",
          Some("12345"),
          LocalDate.now(),
          None,
          "",
          "",
          2,
          Some(100),
          false,
          false
        ),
        cyEmployment,
        pyEmployment
      )

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(employmentsForYear))

      val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
      val pyEmploymentDetails = Await.result(sut.employmentsForYear(nino, previousTaxYear)(hc), 5.seconds)

      pyEmploymentDetails mustBe List(pyEmployment)
    }

    "return sequence of employment for passed year" in {
      val cyEmployment = List(
        Employment(
          "TEST",
          Some("12345"),
          currentTaxYear.start,
          Some(currentTaxYear.start.plusMonths(3)),
          "",
          "",
          2,
          Some(100),
          false,
          false),
        Employment(
          "TEST1",
          Some("123456"),
          currentTaxYear.start.plusMonths(3),
          None,
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
          previousTaxYear.end.minusMonths(3),
          Some(previousTaxYear.end),
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
          previousTaxYear.end.minusMonths(6),
          Some(previousTaxYear.end.minusMonths(3)),
          "",
          "",
          2,
          Some(100),
          false,
          false)
      )

      val employments = cyEmployment.union(pyEmployment)

      val mockCacheConnector = mock[CacheConnector]
      when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
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
            new LocalDate(2017, 4, 8),
            Some(new LocalDate(2017, 11, 8)),
            "",
            "",
            2,
            Some(100),
            false,
            false
          ),
          Employment("TEST1", Some("123456"), new LocalDate(2017, 11, 9), None, "", "", 2, Some(100), false, false)
        )

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(employments2017))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockCacheConnector, mockNpsConnector, mock[Auditor])
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

      val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
      Await.result(sut.employment(Nino(nino.nino), 4), 5 seconds) mustBe Right(emp1)
    }

    "return Employment not found error type when there is no employment found for that ID" in {
      val emp1 = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
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

      val sut = createSUT(mockCacheConnector, mock[NpsConnector], mock[Auditor])
      Await.result(sut.employment(Nino(nino.nino), 10), 5 seconds) mustBe Left(EmploymentNotFound)
    }

    "get the current year employments from the hod" when {
      "data is in the cache for a year other than the current one and it does not contain the required employment " in {
        val emp2015 = Employment(
          "TEST",
          Some("12345"),
          TaxYear(2015).start,
          Some(TaxYear(2015).end),
          "",
          "",
          4,
          Some(100),
          false,
          false
        )

        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.findSeq[Employment](any(), any())(any()))
          .thenReturn(Future.successful(List(emp2015)))
        when(mockCacheConnector.createOrUpdateSeq(any(), any(), any())(any()))
          .thenReturn(Future.successful(Nil))

        val mockNpsConnector = mock[NpsConnector]
        when(mockNpsConnector.getEmploymentDetails(any(), any())(any()))
          .thenReturn(Future.successful(getJson("npsSingleEmployment")))

        val sut = createSUT(mockCacheConnector, mockNpsConnector, mock[Auditor])
        Await.result(sut.employment(Nino(nino.nino), 3), 5 seconds)

        verify(mockNpsConnector, times(1))
          .getEmploymentDetails(org.mockito.Matchers.eq(Nino(nino.nino)), org.mockito.Matchers.eq(TaxYear().year))(
            any())
      }
    }
  }

  private val nino = new Generator(new Random).nextNino
  private val cacheId = CacheId(nino)

  private def createSUT(cacheConnector: CacheConnector, npsConnector: NpsConnector, auditor: Auditor) =
    new EmploymentRepository(cacheConnector, npsConnector, auditor)

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentRepositoryTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    Json.parse(source.mkString(""))
  }
}

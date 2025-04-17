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

package uk.gov.hmrc.tai.model.domain

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{times, verify}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate
import scala.util.Random

class EmploymentBuilderSpec extends PlaySpec with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val auditTransactionName = "NPS RTI Data Mismatch"

  trait EmploymentBuilderSetup {
    val nino: Nino = new Generator(new Random).nextNino
    val mockAuditor: Auditor = mock[Auditor]
    val testEmploymentBuilder = new EmploymentBuilder(mockAuditor)
  }

  "combineAccountsWithEmployments" must {
    "combine Employment instances (having Nil accounts), with their corresponding AnnualAccount instances" when {
      "each AnnualAccount record has a single matching Employment record by employer designation" in new EmploymentBuilderSetup {

        val employmentsNoPayroll: List[Employment] = List(
          Employment(
            "TestEmp1",
            Live,
            None,
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TestEmp2",
            Live,
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        val accounts: List[AnnualAccount] = List(
          AnnualAccount(1, TaxYear(2017), Available, Nil, Nil),
          AnnualAccount(2, TaxYear(2017), Available, Nil, Nil)
        )

        val unifiedEmployments: Seq[Employment] =
          testEmploymentBuilder
            .combineAccountsWithEmployments(employmentsNoPayroll, accounts, false, nino, TaxYear(2017))
            .employments

        unifiedEmployments must contain(
          Employment(
            "TestEmp1",
            Live,
            None,
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(1, TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        unifiedEmployments must contain(
          Employment(
            "TestEmp2",
            Live,
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(2, TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        unifiedEmployments.size mustBe 2

        verify(mockAuditor, times(0)).sendDataEvent(meq(auditTransactionName), any())(any())
      }

      "an AnnualAccount record has more than one Employment record that matches by employer designation, " +
        "but one of them also matches by payrollNumber (employee designation)" in new EmploymentBuilderSetup {

          val employmentsNoPayroll: List[Employment] = List(
            Employment(
              "TestEmp1",
              Live,
              Some("payrollNo88"),
              LocalDate.parse("2017-07-24"),
              None,
              Nil,
              "taxDistrict1",
              "payeRefemployer1",
              1,
              Some(100),
              false,
              false,
              PensionIncome
            ),
            Employment(
              "TestEmp2",
              Live,
              Some("payrollNo14"),
              LocalDate.parse("2017-07-24"),
              None,
              Nil,
              "taxDistrict1",
              "payeRefemployer1",
              2,
              Some(100),
              false,
              false,
              PensionIncome
            )
          )

          val accounts: List[AnnualAccount] =
            List(AnnualAccount(1, TaxYear(2017), Available, Nil, Nil))

          val unifiedEmployments: Seq[Employment] =
            testEmploymentBuilder
              .combineAccountsWithEmployments(employmentsNoPayroll, accounts, false, nino, TaxYear(2017))
              .employments

          unifiedEmployments must contain(
            Employment(
              "TestEmp1",
              Live,
              Some("payrollNo88"),
              LocalDate.parse("2017-07-24"),
              None,
              List(AnnualAccount(1, TaxYear(2017), Available, Nil, Nil)),
              "taxDistrict1",
              "payeRefemployer1",
              1,
              Some(100),
              false,
              false,
              PensionIncome
            )
          )

          verify(mockAuditor, times(0)).sendDataEvent(meq(auditTransactionName), any())(any())
        }

      "multiple AnnualAccount records match the same employment record by employer designation" in new EmploymentBuilderSetup {
        val employmentsNoPayroll: List[Employment] = List(
          Employment(
            "TestEmp1",
            Live,
            Some("payrollNo88"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TestEmp2",
            Live,
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        val accounts: List[AnnualAccount] = List(
          AnnualAccount(1, TaxYear(2017), Available, Nil, Nil),
          AnnualAccount(2, TaxYear(2017), Available, Nil, Nil)
        )

        val unifiedEmployments: Seq[Employment] =
          testEmploymentBuilder
            .combineAccountsWithEmployments(employmentsNoPayroll, accounts, false, nino, TaxYear(2017))
            .employments

        unifiedEmployments must contain(
          Employment(
            "TestEmp1",
            Live,
            Some("payrollNo88"),
            LocalDate.parse("2017-07-24"),
            None,
            List(
              AnnualAccount(1, TaxYear(2017), Available, Nil, Nil)
            ),
            "taxDistrict1",
            "payeRefemployer1",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        unifiedEmployments must contain(
          Employment(
            "TestEmp2",
            Live,
            Some("payrollNo1"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(2, TaxYear(2017), Available, Nil, Nil)),
            "taxDistrict2",
            "payeRefemployer2",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        unifiedEmployments.size mustBe 2

        verify(mockAuditor, times(0)).sendDataEvent(meq(auditTransactionName), any())(any())
      }
    }

    "unify stubbed Employment instances (having Nil accounts) with placeholder 'Unavailable' AnnualAccount instances" when {
      val ty = TaxYear(2017)

      "one of the known employments has no corresponding AnnualAccount & no rti api failure" in new EmploymentBuilderSetup {
        val employments: List[Employment] = List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TEST2",
            Live,
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        val accounts: List[AnnualAccount] =
          List(AnnualAccount(1, ty, Available, Nil, Nil), AnnualAccount(5, ty, Available, Nil, Nil))

        val unified: Seq[Employment] =
          testEmploymentBuilder.combineAccountsWithEmployments(employments, accounts, false, nino, ty).employments

        unified mustBe List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(1, ty, Available, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TEST2",
            Live,
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(2, ty, Available, Nil, Nil)),
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        verify(mockAuditor, times(1)).sendDataEvent(meq(auditTransactionName), any())(any())
      }

      "one of the known employments has no corresponding AnnualAccount & an rti api failure" in new EmploymentBuilderSetup {
        val employments: List[Employment] = List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TEST2",
            Live,
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        val unified: Seq[Employment] =
          testEmploymentBuilder.combineAccountsWithEmployments(employments, List.empty, true, nino, ty).employments

        unified mustBe List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(1, ty, TemporarilyUnavailable, Nil, Nil)),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TEST2",
            Live,
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(2, ty, TemporarilyUnavailable, Nil, Nil)),
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        verify(mockAuditor, times(0)).sendDataEvent(meq(auditTransactionName), any())(any())
      }

      "no AnnualAccount records are available & no rti api failure" in new EmploymentBuilderSetup {
        val employments: List[Employment] = List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TEST2",
            Live,
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            Nil,
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        val accounts: Nil.type = Nil

        val unified: Seq[Employment] =
          testEmploymentBuilder.combineAccountsWithEmployments(employments, accounts, false, nino, ty).employments

        unified mustBe List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(1, ty, Available, List(), List())),
            "tdNo",
            "payeNumber",
            1,
            Some(100),
            false,
            false,
            PensionIncome
          ),
          Employment(
            "TEST2",
            Live,
            Some("12346"),
            LocalDate.parse("2017-07-24"),
            None,
            List(AnnualAccount(2, ty, Available, List(), List())),
            "tdNo",
            "payeNumber",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        verify(mockAuditor, times(0)).sendDataEvent(meq(auditTransactionName), any())(any())
      }

      "multiple AnnualAccounts exist for one employment record, another record has no corresponding account records, " +
        "and one of the account records matches none of the employment records & no rti api failure" in new EmploymentBuilderSetup {
          val employments: List[Employment] = List(
            Employment(
              "TEST",
              Live,
              Some("12345"),
              LocalDate.parse("2017-07-24"),
              None,
              Nil,
              "tdNo",
              "payeNumber",
              1,
              Some(100),
              false,
              false,
              PensionIncome
            ),
            Employment(
              "TEST2",
              Live,
              Some("88888"),
              LocalDate.parse("2017-07-24"),
              None,
              Nil,
              "tdNo",
              "payeNumber",
              2,
              Some(100),
              false,
              false,
              PensionIncome
            )
          )

          val accounts: List[AnnualAccount] = List(
            AnnualAccount(1, ty, Available, Nil, Nil),
            AnnualAccount(1, ty, Available, Nil, Nil),
            AnnualAccount(5, ty, Available, Nil, Nil)
          )

          val unified: Seq[Employment] =
            testEmploymentBuilder.combineAccountsWithEmployments(employments, accounts, false, nino, ty).employments

          unified mustBe List(
            Employment(
              "TEST",
              Live,
              Some("12345"),
              LocalDate.parse("2017-07-24"),
              None,
              List(AnnualAccount(1, ty, Available, Nil, Nil), AnnualAccount(1, ty, Available, Nil, Nil)),
              "tdNo",
              "payeNumber",
              1,
              Some(100),
              false,
              false,
              PensionIncome
            ),
            Employment(
              "TEST2",
              Live,
              Some("88888"),
              LocalDate.parse("2017-07-24"),
              None,
              List(AnnualAccount(2, ty, Available, Nil, Nil)),
              "tdNo",
              "payeNumber",
              2,
              Some(100),
              false,
              false,
              PensionIncome
            )
          )

          verify(mockAuditor, times(1)).sendDataEvent(meq(auditTransactionName), any())(any())
        }
    }
  }
}

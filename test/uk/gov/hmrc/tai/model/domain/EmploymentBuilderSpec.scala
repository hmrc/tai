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

package uk.gov.hmrc.tai.model.domain

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.util.Random

class EmploymentBuilderSpec extends PlaySpec with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val auditTransactionName = "NPS RTI Data Mismatch"

  trait EmploymentBuilderSetup {
    val nino = new Generator(new Random).nextNino
    val mockAuditor = mock[Auditor]
    val testEmploymentBuilder = new EmploymentBuilder(mockAuditor)
  }

  "combineAccountsWithEmployments" should {
    "combine Employment instances (having Nil accounts), with their corresponding AnnualAccount instances" when {
      "each AnnualAccount record has a single matching Employment record by employer designation" in new EmploymentBuilderSetup {

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

        val unifiedEmployments =
          testEmploymentBuilder
            .combineAccountsWithEmployments(employmentsNoPayroll, accounts, nino, TaxYear(2017))
            .employments

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

        verify(mockAuditor, times(0)).sendDataEvent(Matchers.eq(auditTransactionName), any())(any())
      }

      "an AnnualAccount record has more than one Employment record that matches by employer designation, " +
        "but one of them also matches by payrollNumber (employee designation)" in new EmploymentBuilderSetup {

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

        val unifiedEmployments =
          testEmploymentBuilder
            .combineAccountsWithEmployments(employmentsNoPayroll, accounts, nino, TaxYear(2017))
            .employments

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

        verify(mockAuditor, times(0)).sendDataEvent(Matchers.eq(auditTransactionName), any())(any())
      }

      "multiple AnnualAccount records match the same employment record by employer designation" in new EmploymentBuilderSetup {
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

        val unifiedEmployments =
          testEmploymentBuilder
            .combineAccountsWithEmployments(employmentsNoPayroll, accounts, nino, TaxYear(2017))
            .employments

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

        verify(mockAuditor, times(0)).sendDataEvent(Matchers.eq(auditTransactionName), any())(any())
      }
    }

    "unify stubbed Employment instances (having Nil accounts) with placeholder 'Unavailable' AnnualAccount instances" when {
      val ty = TaxYear(2017)

      "one of the known employments has no corresponding AnnualAccount" in new EmploymentBuilderSetup {
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

        val unified = testEmploymentBuilder.combineAccountsWithEmployments(employments, accounts, nino, ty).employments

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

        verify(mockAuditor, times(1)).sendDataEvent(Matchers.eq(auditTransactionName), any())(any())
      }

      "no AnnualAccount records are available" in new EmploymentBuilderSetup {
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

        val unified = testEmploymentBuilder.combineAccountsWithEmployments(employments, accounts, nino, ty).employments

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

        verify(mockAuditor, times(0)).sendDataEvent(Matchers.eq(auditTransactionName), any())(any())
      }

      "multiple AnnualAccounts exist for one employment record, another record has no corresponding account records, " +
        "and one of the account records matches none of the employment records" in new EmploymentBuilderSetup {
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

        val unified = testEmploymentBuilder.combineAccountsWithEmployments(employments, accounts, nino, ty).employments

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

        verify(mockAuditor, times(1)).sendDataEvent(Matchers.eq(auditTransactionName), any())(any())
      }
    }
  }
}

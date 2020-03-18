package uk.gov.hmrc.tai.builders

import org.joda.time.LocalDate
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Available, Employment, OldEmployment}
import uk.gov.hmrc.tai.model.tai.TaxYear

class OldEmploymentBuilderSpec extends PlaySpec with MockitoSugar {

  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev


  private def buildEmployment(taxDistrictNumber: String, payeNumber: String, payrollNumber: Option[String] = None) =
    Employment("name", payrollNumber, LocalDate.now().minusMonths(1), None, taxDistrictNumber, payeNumber, 0, None, false, false)

  private def buildAnnualAccount(key: String) =
    AnnualAccount(key, currentTaxYear, Available, Nil, Nil)

  "build" must {
    "match according to employer designation when there is only one match" in {
      val employmentA1 = buildEmployment("A", "1")
      val employmentA2 = buildEmployment("A", "2")
      val employmentA3 = buildEmployment("A", "3")

      val accountA1 = buildAnnualAccount("A-1")
      val accountA2 = buildAnnualAccount("A-2")
      val accountA4 = buildAnnualAccount("A-4")

      val employments = Seq(employmentA1, employmentA2, employmentA3)
      val accounts = Seq(accountA1, accountA2, accountA4)

      val result = OldEmploymentBuilder.build(employments, accounts, currentTaxYear)

      result.contains(OldEmployment(accountA1, employmentA1)) mustBe true
      result.contains(OldEmployment(accountA2, employmentA2)) mustBe true
      result.contains(OldEmployment(accountA4, employmentA3)) mustBe false
      result.length mustBe 3
    }

    "match according to key when there are multiple matches" in {

      val employmentA1a = buildEmployment("A", "1", Some("a"))
      val employmentA1b = buildEmployment("A", "1", Some("b"))
      val employmentA1c = buildEmployment("A", "1", Some("c"))

      val account = buildAnnualAccount("A-1-a")

      val result = OldEmploymentBuilder.build(Seq(employmentA1a, employmentA1b, employmentA1c), Seq(account), currentTaxYear)

      result.contains(OldEmployment(account, employmentA1a)) mustBe true
      result.contains(OldEmployment(account, employmentA1b)) mustBe false
      result.contains(OldEmployment(account, employmentA1c)) mustBe false
      result.length mustBe 3
    }

    "provide empty account information when there is no match" in {
      val employmentA1a = buildEmployment("A", "1", Some("a"))
      val employmentA1b = buildEmployment("A", "1", Some("b"))
      val employmentA1c = buildEmployment("A", "1", Some("c"))

      val account = buildAnnualAccount("A-2-a")

      val result = OldEmploymentBuilder.build(Seq(employmentA1a, employmentA1b, employmentA1c), Seq(account), currentTaxYear)

      result.contains(OldEmployment(account, employmentA1a)) mustBe false
      result.contains(OldEmployment(account, employmentA1b)) mustBe false
      result.contains(OldEmployment(account, employmentA1c)) mustBe false
      result.filter(oe => oe.annualAccounts != Nil).length mustBe 3
    }

    "merge duplicate employments" in {
      val employments = Seq(
        buildEmployment("A", "1", Some("a")),
        buildEmployment("A", "1", Some("a")),
        buildEmployment("A", "1", Some("a")))

      val account = buildAnnualAccount("A-1-a")

      val result = OldEmploymentBuilder.build(employments, Seq(account), currentTaxYear)

      result.length mustBe 1
    }
  }



}

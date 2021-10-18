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

package data

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.TaxSummaryDetails
import uk.gov.hmrc.tai.model.nps.{NpsEmployment, NpsIabdRoot, NpsTaxAccount}
import uk.gov.hmrc.tai.model.nps2.NpsFormatter

import java.io.File
import scala.io.BufferedSource
import scala.util.Random

object NpsData extends NpsFormatter {

  private lazy val nonCodedTaxAccountJson = "NonCodedMultiIncome/NpsTaxAccount.json"

  private lazy val nonCodedWithCeasedTaxAccountJson = "NonCodedIncomeWithCeased/NpsTaxAccount.json"
  private lazy val nonCodedWithCeaseddEmploymentJson = "NonCodedIncomeWithCeased/NpsEmployment.json"

  private lazy val basicRateExtnTaxAccountJson = "BasicRateExtensions/NpsTaxAccount.json"
  private lazy val basicRateExtnEmploymentJson = "BasicRateExtensions/NpsEmployment.json"

  private lazy val basicRateLivePensionTaxAccountJson = "BasicRateLivePensions/NpsTaxAccount.json"
  private lazy val basicRateLivePensionJson = "BasicRateLivePensions/NpsEmployment.json"

  private lazy val ceasedEmploymentTaxAccountJson = "CeasedEmployment/NpsTaxAccount.json"
  private lazy val ceasedEmploymentJson = "CeasedEmployment/NpsEmployment.json"

  private lazy val potentiallyCeasedEmploymentTaxAccountJson = "PotentiallyCeasedEmployment/NpsTaxAccount.json"
  private lazy val potentiallyCeasedEmploymentJson = "PotentiallyCeasedEmployment/NpsEmployment.json"

  private lazy val twoEmploymentsOneWithJSATaxAccountJson = "TwoEmploymentsOneWithJSA/NpsTaxAccount.json"
  private lazy val twoEmploymentsOneWithJSAJson = "TwoEmploymentsOneWithJSA/NpsEmployment.json"

  private lazy val bankInterestAndDividendsTaxAccountJson = "BankInterestAndDividends/NpsTaxAccount.json"

  private lazy val potentialUnderpaymentTaxAccountJson = "PotentialUnderPayment/NpsTaxAccount.json"
  private lazy val potentialUnderpaymentEmploymentJson = "PotentialUnderPayment/NpsEmployment.json"

  private lazy val bankInterestAllHigherRateTaxAccountJson = "BankInterestAllHigherRate/NpsTaxAccount.json"

  private lazy val childBenefitTaxAccountJson = "ChildBenefit/NpsTaxAccount.json"
  private lazy val childBenefitIabdsJson = "ChildBenefit/NpsIabds.json"

  private lazy val outstandingDebtJson = "OutstandingDebt/NpsTaxAccount.json"

  private lazy val taxSummaryDetailsJson = "TaxSummaryDetails/TaxSummary.json"
  private lazy val JSAOtherIncomeSourceNpsEmploymentJson = "AutoUpdate/JSAOtherIncomeSourceNpsEmployment.json"

  private lazy val NpsTaxAccountJson = "TaxDetail/TaxAccount.json"

  private lazy val NpsTaxAccountMultipleAllowancesJson = "TaxDetail/TaxAccount_Multiple_AllowReliefDeduct.json"

  private lazy val FETaxAccountJson = "TaxDetail/FETaxAccount.json"

  private val basePath = "test/resources/data/"

  private val nino: Nino = new Generator(new Random).nextNino

  private def getNpsTaxAccount(fileName: String): NpsTaxAccount = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[NpsTaxAccount](jsVal)
    result.get
  }

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    jsVal
  }

  private def getSelectedNpsEmployment(fileName: String): NpsEmployment = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[NpsEmployment](jsVal)
    result.get
  }

  private def getNpsEmployment(fileName: String): List[NpsEmployment] = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[List[NpsEmployment]](jsVal)
    result.get
  }

  private def getTaxSummaryDetails(fileName: String): TaxSummaryDetails = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[TaxSummaryDetails](jsVal)
    result.get
  }

  private def getNpsEmploymentIABDs(fileName: String): List[NpsIabdRoot] = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[List[NpsIabdRoot]](jsVal)
    result.get
  }

  private def getNpsIABDs(fileName: String): List[NpsIabdRoot] = {
    val jsonFilePath = basePath + fileName
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString("").replaceAll("\\$NINO", nino.nino))
    val result = Json.fromJson[List[NpsIabdRoot]](jsVal)
    result.get
  }

  def getNpsNonCodedTaxAccount() = getNpsTaxAccount(nonCodedTaxAccountJson)

  def getNpsNonCodedWithCeasedTaxAccount() = getNpsTaxAccount(nonCodedWithCeasedTaxAccountJson)
  def getNpsNonCodedWithCeasedEmployment() = getNpsEmployment(nonCodedWithCeaseddEmploymentJson)

  def getNpsBasicRateExtnTaxAccount() = getNpsTaxAccount(basicRateExtnTaxAccountJson)
  def getNpsBasicRateExtnEmployment() = getNpsEmployment(basicRateExtnEmploymentJson)

  def getNpsBasicRateLivePensionTaxAccount() = getNpsTaxAccount(basicRateLivePensionTaxAccountJson)
  def getNpsBasicRateLivePensions() = getNpsEmployment(basicRateLivePensionJson)

  def getNpsCeasedEmploymentTaxAccount() = getNpsTaxAccount(ceasedEmploymentTaxAccountJson)
  def getNpsCeasedEmployments() = getNpsEmployment(ceasedEmploymentJson)

  def getNpsPotentiallyCeasedEmploymentTaxAccount() = getNpsTaxAccount(potentiallyCeasedEmploymentTaxAccountJson)
  def getNpsPotentiallyCeasedEmployments() = getNpsEmployment(potentiallyCeasedEmploymentJson)

  def getNpsTwoEmploymentsOneWithJSAIndicatorTaxAccount() = getNpsTaxAccount(twoEmploymentsOneWithJSATaxAccountJson)
  def getNpsTwoEmploymentsOneWithJSAIndicator() = getNpsEmployment(twoEmploymentsOneWithJSAJson)

  def getNpsBankInterestAndDividendsTaxAccount() = getNpsTaxAccount(bankInterestAndDividendsTaxAccountJson)

  def getNpsPotentialUnderpaymentTaxAccount() = getNpsTaxAccount(potentialUnderpaymentTaxAccountJson)
  def getNpsPotentialUnderpaymentEmployments() = getNpsEmployment(potentialUnderpaymentEmploymentJson)

  def getNpsBankInterestAllHigherRateTaxAccount() = getNpsTaxAccount(bankInterestAllHigherRateTaxAccountJson)

  def getNpsChildBenefitTaxAccount() = getNpsTaxAccount(childBenefitTaxAccountJson)
  def getNpsChildBenefitIabds() = getNpsIABDs(childBenefitIabdsJson)

  def getNpsOutstandingDebt() = getNpsTaxAccount(outstandingDebtJson)

  private lazy val giftAidTaxAccountJson = "HigherRateIncomeWithGiftAid/NpsTaxAccount.json"
  def getNpsGiftAidTaxAccount() = getNpsTaxAccount(giftAidTaxAccountJson)

  private lazy val ukDividendsTaxAccountJson = "Dividends_BasicRate/NpsTaxAccount.json"
  def getNpsDividendsTaxAccount() = getNpsTaxAccount(ukDividendsTaxAccountJson)

  def getTaxSummary = getTaxSummaryDetails(taxSummaryDetailsJson)
  def getJSAOtherIncomeSourceNpsEmployment = getNpsEmployment(JSAOtherIncomeSourceNpsEmploymentJson)
  def getNpsTaxAccountJson = getJson(NpsTaxAccountJson)
  def getFETaxAccountJson = getJson(FETaxAccountJson)
  def getNpsTaxAccountMultipleAllowancesJson = getJson(NpsTaxAccountMultipleAllowancesJson)
}

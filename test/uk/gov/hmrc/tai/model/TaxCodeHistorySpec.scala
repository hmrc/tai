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

package uk.gov.hmrc.tai.model

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.factory.TaxCodeRecordFactory
import uk.gov.hmrc.tai.model.TaxCodeHistory.{reads, writes}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EncryptionService
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

import scala.util.Random

class TaxCodeHistorySpec extends PlaySpec with BeforeAndAfterEach with TaxCodeHistoryConstants {
  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private implicit val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedValueAsString: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedValueAsString)
  private val nino: Nino = new Generator(new Random).nextNino
  private val validJson = Json.obj(
    "nino"          -> nino,
    "taxCodeRecord" -> Seq.empty[TaxCodeRecord]
  )
  private val taxCodeHistory = TaxCodeHistory(nino.nino, Seq.empty)

  private val encryptionService = new EncryptionService()
  private def formatWithEncryption: Format[TaxCodeHistory] = encryptionService.sensitiveFormatJsObject[TaxCodeHistory]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
  }

  "formatWithEncryption" must {
    "write encrypted, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(taxCodeHistory)(formatWithEncryption)

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read encrypted, calling decrypt successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(validJson)))

      val result = JsString(encryptedValueAsString).as[TaxCodeHistory](formatWithEncryption)

      result mustBe taxCodeHistory

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read unencrypted JsObject, not calling decrypt at all" in {
      val result = validJson.as[TaxCodeHistory](formatWithEncryption)

      result mustBe TaxCodeHistory(nino.nino, Seq.empty)

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())

    }
  }

  "TaxCodeHistory applicableTaxCodeRecords" must {
    "filter out operated tax code records" in {
      val nonOperatedRecord = TaxCodeRecordFactory.createNonOperatedEmployment()
      val primaryEmployment = TaxCodeRecordFactory.createPrimaryEmployment()
      val taxCodeHistory = TaxCodeHistory(nino.nino, Seq(nonOperatedRecord, primaryEmployment))

      taxCodeHistory.applicableTaxCodeRecords mustBe Seq(primaryEmployment)
    }

    "filter out tax code that are not in current year" in {
      val primaryEmployment = TaxCodeRecordFactory.createPrimaryEmployment()
      val nextYearTaxCodeRecord = TaxCodeRecordFactory.createPrimaryEmployment(taxYear = TaxYear().next)
      val taxCodeHistory = TaxCodeHistory(nino.nino, Seq(primaryEmployment, nextYearTaxCodeRecord))

      taxCodeHistory.applicableTaxCodeRecords mustBe Seq(primaryEmployment)
    }
  }

}

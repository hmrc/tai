/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeRecord}
import uk.gov.hmrc.tai.service.SensitiveFormatService._

import java.time.LocalDate
import scala.util.Random

class SensitiveFormatServiceSpec extends PlaySpec with BeforeAndAfterEach {
  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private implicit val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedValueAsString: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedValueAsString)
  private val unencryptedJsObject: JsObject = Json.obj(
    "testa" -> "valuea",
    "testb" -> "valueb"
  )
  private val unencryptedJsString: JsString = JsString("test")
  private val sensitiveJsObject: SensitiveJsValue = SensitiveJsValue(unencryptedJsObject)
  private val sensitiveJsString: SensitiveJsValue = SensitiveJsValue(unencryptedJsString)

  private val mockMongoConfig = mock[MongoConfig]

  private val sensitiveFormatService = new SensitiveFormatService(mockEncrypterDecrypter, mockMongoConfig)

  private val validJsonAnnualAccount =
    Json.obj(
      "sequenceNumber" -> 0,
      "taxYear"        -> 2020,
      "realTimeStatus" -> "Available",
      "payments" -> Json.arr(
        Json.obj(
          "date"                              -> "2017-05-26",
          "amountYearToDate"                  -> 10,
          "taxAmountYearToDate"               -> 10,
          "nationalInsuranceAmountYearToDate" -> 10,
          "amount"                            -> 10,
          "taxAmount"                         -> 10,
          "nationalInsuranceAmount"           -> 10,
          "payFrequency"                      -> "Monthly",
          "duplicate"                         -> true
        )
      ),
      "endOfTaxYearUpdates" -> Json.arr(
        Json.obj(
          "date" -> "2017-05-26",
          "adjustments" -> Json.arr(
            Json.obj(
              "type"   -> "NationalInsuranceAdjustment",
              "amount" -> 10
            )
          )
        )
      )
    )
  private val annualAccount =
    AnnualAccount(
      sequenceNumber = 0,
      taxYear = TaxYear(2020),
      realTimeStatus = Available,
      payments = Seq(
        Payment(
          date = LocalDate.of(2017, 5, 26),
          amountYearToDate = 10,
          taxAmountYearToDate = 10,
          nationalInsuranceAmountYearToDate = 10,
          amount = 10,
          taxAmount = 10,
          nationalInsuranceAmount = 10,
          payFrequency = Monthly,
          duplicate = Some(true)
        )
      ),
      endOfTaxYearUpdates =
        Seq(EndOfTaxYearUpdate(LocalDate.of(2017, 5, 26), Seq(Adjustment(NationalInsuranceAdjustment, BigDecimal(10)))))
    )

  private val nino: Nino = new Generator(new Random).nextNino
  private val taxCodeHistory = TaxCodeHistory(nino.nino, Seq.empty)
  private val validJson = Json.obj(
    "nino"          -> nino,
    "taxCodeRecord" -> Seq.empty[TaxCodeRecord]
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
    reset(mockMongoConfig)
    when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
  }

  "formatSensitiveJsValue" must {
    "write JsObject, calling encrypt when mongo encryption enabled" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(sensitiveJsObject)(sensitiveFormatService.sensitiveFormatJsValue[JsObject])

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "write JsObject, not calling encrypt when mongo encryption disabled" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)
      when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)

      val result: JsValue = Json.toJson(sensitiveJsObject)(sensitiveFormatService.sensitiveFormatJsValue[JsObject])

      result mustBe unencryptedJsObject

      verify(mockEncrypterDecrypter, times(0)).encrypt(any())
    }

    "write JsString, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(sensitiveJsString)(sensitiveFormatService.sensitiveFormatJsValue[JsString])

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read JsString as a JsObject, calling decrypt" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(unencryptedJsObject)))

      val result =
        JsString(encryptedValueAsString).as[SensitiveJsValue](sensitiveFormatService.sensitiveFormatJsValue[JsObject])

      result mustBe sensitiveJsObject

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsString as a JsObject, not calling decrypt when mongo encryption disabled" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(unencryptedJsObject)))
      when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)

      val result =
        unencryptedJsObject.as[SensitiveJsValue](sensitiveFormatService.sensitiveFormatJsValue[JsObject])

      result mustBe sensitiveJsObject

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())
    }

    "read JsString as a JsString, calling decrypt successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(JsString("test"))))

      val result =
        JsString(encryptedValueAsString).as[SensitiveJsValue](sensitiveFormatService.sensitiveFormatJsValue[JsString])

      result mustBe SensitiveJsValue(JsString("test"))

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsString as a JsString, calling decrypt unsuccessfully (i.e. not encrypted) and use unencrypted jsString" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenThrow(new SecurityException("Unable to decrypt value"))

      val result = JsString("abc").as[SensitiveJsValue](sensitiveFormatService.sensitiveFormatJsValue[JsString])

      result mustBe SensitiveJsValue(JsString("abc"))

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsObject, not calling decrypt at all" in {
      val result = unencryptedJsObject.as[SensitiveJsValue](sensitiveFormatService.sensitiveFormatJsValue[JsObject])

      result mustBe sensitiveJsObject

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())
    }
  }

  "sensitiveFormatJsArray" must {
    "write encrypted array, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)
      val result: JsValue =
        Json.toJson(Seq(annualAccount))(sensitiveFormatService.sensitiveFormatJsArray[Seq[AnnualAccount]])
      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read encrypted array, calling decrypt" in {
      when(mockEncrypterDecrypter.decrypt(any()))
        .thenReturn(PlainText(Json.stringify(Json.arr(validJsonAnnualAccount))))

      val result = JsString(encryptedValueAsString).as[Seq[AnnualAccount]](
        sensitiveFormatService.sensitiveFormatJsArray[Seq[AnnualAccount]]
      )

      result mustBe Seq(annualAccount)

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read unencrypted JsObject, not calling decrypt at all" in {
      val result = Json
        .arr(validJsonAnnualAccount)
        .as[Seq[AnnualAccount]](sensitiveFormatService.sensitiveFormatJsArray[Seq[AnnualAccount]])

      result mustBe List(annualAccount)

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())

    }
  }

  "sensitiveFormatJsObject" must {
    "write encrypted, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(taxCodeHistory)(sensitiveFormatService.sensitiveFormatJsObject[TaxCodeHistory])

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read encrypted, calling decrypt successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(validJson)))

      val result = JsString(encryptedValueAsString).as[TaxCodeHistory](
        sensitiveFormatService.sensitiveFormatJsObject[TaxCodeHistory]
      )

      result mustBe taxCodeHistory

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read unencrypted JsObject, not calling decrypt at all" in {
      val result = validJson.as[TaxCodeHistory](sensitiveFormatService.sensitiveFormatJsObject[TaxCodeHistory])

      result mustBe TaxCodeHistory(nino.nino, Seq.empty)

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())
    }
  }

}

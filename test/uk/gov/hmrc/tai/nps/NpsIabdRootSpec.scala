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

package uk.gov.hmrc.tai.nps

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot.formatWithEncryption
import uk.gov.hmrc.tai.model.nps.{NpsDate, NpsIabdRoot}

import java.time.LocalDate
import scala.util.Random

class NpsIabdRootSpec extends PlaySpec with BeforeAndAfterEach {
  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private implicit val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedValueAsString: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedValueAsString)
  private val nino: Nino = new Generator(new Random).nextNino

  private val validJson =
    Json.obj(
      "nino"                     -> nino,
      "employmentSequenceNumber" -> 1,
      "type"                     -> 2,
      "grossAmount"              -> BigDecimal(11.33),
      "netAmount"                -> BigDecimal(22.33),
      "source"                   -> 5,
      "receiptDate"              -> "26/11/2015",
      "captureDate"              -> "27/11/2015"
    )

  private val npsIabdRoot = NpsIabdRoot(
    nino = nino.nino,
    employmentSequenceNumber = Some(1),
    `type` = 2,
    grossAmount = Some(BigDecimal(11.33)),
    netAmount = Some(BigDecimal(22.33)),
    source = Some(5),
    receiptDate = Some(NpsDate(LocalDate.of(2015, 11, 26))),
    captureDate = Some(NpsDate(LocalDate.of(2015, 11, 27)))
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
  }

  "formatWithEncryption" must {
    "write encrypted array, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(List(npsIabdRoot))(formatWithEncryption)

      result.as[JsArray] mustBe Json.arr(JsString(encryptedValueAsString))

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read encrypted array, calling decrypt" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(validJson)))

      val result = Json.arr(JsString(encryptedValueAsString)).as[List[NpsIabdRoot]](formatWithEncryption)

      result mustBe List(npsIabdRoot)

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read unencrypted JsObject, not calling decrypt at all" in {
      val result = Json.arr(validJson).as[List[NpsIabdRoot]](formatWithEncryption)

      result mustBe List(npsIabdRoot)

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())

    }
  }
}
